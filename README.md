# ProfitSaathi Modular Monolith

Single Spring Boot 3.5 JAR that combines what used to be three services
(`ProfitSaathi`, `ProfitSaathi User`, `notification_service`) plus the
`notification-common` shared library. Optimised for the trial stage:
one JVM, one Postgres database, no Kafka, no Keycloak, no service discovery.

---

## What changed vs. the old multi-service setup

| Removed | Why | Replacement |
|---|---|---|
| Keycloak (resource server + admin client) | Heavy memory, ops burden | Local JWT (HS256, jjwt) — `/api/v1/auth/signup/{seller,customer}`, `/login`, `/refresh`, `/logout` |
| Kafka (`spring-kafka`, `email-topic`, `whatsapp-topic`) | Not needed for trial scale | Direct in-process method calls. `EmailService.send(...)` and `WhatsAppService.send(...)` are `@Async` + `@Retryable` (3 attempts, 5s/10s backoff), preserving the same retry/DLT semantics — `FAILED` rows during retry, `FAILED_PERMANENT` after exhaustion |
| Eureka, OpenFeign, Spring Cloud | Service discovery is unnecessary in a monolith | Direct `@Autowired` repositories (e.g. Cart now reads `ProductRepository` directly instead of Feign-calling the product service) |
| Three databases (`profitsaathi`, `profitsaathiuser`, `NotificationDB`) | Operational sprawl | Single Postgres database `profitsaathi` |
| Two `User` entities + duplicated `OtpEntity` / `MailService` / `UsageTracking` | Forced merge needed | `Seller` (table `sellers`) + `Customer` (table `customers`) + shared `Credentials` (table `credentials`); single `OtpEntity`, single `EmailService` |
| Self-hosted Loki | Memory/storage cost at trial stage | Logback writes structured JSON to stdout (`LOG_FORMAT=json`); pick it up with Docker logs, Better Stack, or Grafana Cloud free tier — no agent needed |

## Module layout

```
com.profitsaathi
├── ProfitSaathiApplication
├── auth                                   JWT signup/login/refresh, Credentials entity
├── notification
│   ├── email          (EmailService, WelcomeMailService, EmailRequest)
│   ├── whatsapp       (WhatsAppService, WhatsAppMessage)
│   └── log            (EmailLog + WhatsAppLog persisted to same DB; admin-only read endpoints)
├── otp                                    OtpEntity, OtpService, OtpController (calls EmailService/WhatsAppService directly)
├── seller
│   ├── user           (Seller entity, repo, controller — port from old ProfitSaathi/User/*)
│   ├── product, order, payment, coupon, offer, subscription,
│   │   pricing, dynamicprice, sales, dashboard, shipping, ai, scheduler
│   └── whatsapp.session (WAHA session + webhook — port from old ProfitSaathi/WhatsApp/*)
├── customer
│   ├── user           (Customer + CustomerAddress entities)
│   └── cart           (CartItem; reads ProductRepository directly — no Feign)
├── usagetracking      Single shared module
├── util.aes           AES-256 utilities, JPA EncryptedStringConverter, AESController
└── config             SecurityConfig, AsyncConfig, WebClientConfig, WebConfig (static uploads), GlobalExceptionHandler, SecurityAuditorAware
```

## Auth model

- `credentials` — one row per login. Holds `email`, `password_hash` (BCrypt),
  `role` (`SELLER` | `CUSTOMER` | `ADMIN`), `subject_id` → FK into `sellers.id`
  or `customers.id` depending on role, `refresh_token_id` for rotation.
- JWT subject = `credentials.id`. Custom claim `sid` = `subject_id`. Custom
  claim `role` is mapped to a Spring Security `ROLE_<ROLE>` authority.
- Inject the principal in any controller as
  `@AuthenticationPrincipal AuthenticatedPrincipal me`.

### Endpoints
- `POST /api/v1/auth/signup/seller` — create seller + credentials, send welcome email, return token pair
- `POST /api/v1/auth/signup/customer` — same for customers
- `POST /api/v1/auth/login` — email + password → token pair
- `POST /api/v1/auth/refresh` — refresh token (rotated; old JTI is invalidated)
- `POST /api/v1/auth/logout` — clears refresh token
- `GET  /api/v1/auth/me` — returns the decoded principal

## Running locally

```bash
# 1) create the DB
createdb profitsaathi

# 2) run with the local profile (inline credentials in application-local.properties)
./mvnw spring-boot:run

# OR with docker compose (provisions postgres + redis + waha + app)
cp .env.example .env       # fill in JWT_SECRET, MAIL_*, RAZORPAY_*
docker compose up --build
```

## Observability (no Loki, no Eureka, no service discovery)

| Concern | How |
|---|---|
| App logs | Logback JSON to stdout (`LOG_FORMAT=json`). Docker captures by default. |
| Log aggregation | Better Stack: point its Docker source at the container. Grafana Cloud free Loki tier works the same way. No agent, no Kafka. |
| Uptime | Uptime Kuma → `GET /actuator/health/liveness`. |
| Metrics | `GET /actuator/prometheus`. Grafana Cloud free tier scrapes it; locally just curl it. |
| Errors / traces | Micrometer + Brave bridge already in the pom — flip on Zipkin/Tempo when needed. Off by default to keep memory low. |

## Migration status — every domain module ported

| Module | Source | Target package | Status |
|---|---|---|---|
| Product | `ProfitSaathi/Product/**` | `com.profitsaathi.seller.product` | ✅ done — `User` → `Seller`, `keycloakId` → `me.subjectId()` |
| Order | `ProfitSaathi/Order/**` | `com.profitsaathi.seller.order` | ✅ done — Kafka producer in `OrderNotificationService` rewritten as direct `WhatsAppService.send` |
| Payment | `ProfitSaathi/Payment/**` | `com.profitsaathi.seller.payment` | ✅ done — Razorpay/refund/audit, creds from env in uat/prod |
| Coupon | `ProfitSaathi/Product/Coupon/**` | `com.profitsaathi.seller.coupon` | ✅ done |
| Offer | `ProfitSaathi/Product/Offer/**` | `com.profitsaathi.seller.offer` | ✅ done |
| Subscription | `ProfitSaathi/Subscription/**` | `com.profitsaathi.seller.subscription` | ✅ done |
| Pricing | `PricingAnalysis/**`, `Scheduler/PricingScheduler/**` | `com.profitsaathi.seller.pricing` + `seller.scheduler` | ✅ done |
| DynamicPrice | `ProfitSaathi/DynamicPrice/**` | `com.profitsaathi.seller.dynamicprice` | ✅ done |
| Sales | `SalesSummary/**`, `Scheduler/MonthlyBusinessScheduler/**` | `com.profitsaathi.seller.sales` + `seller.scheduler` | ✅ done |
| Dashboard | `ProfitSaathi/Dashboard/**` | `com.profitsaathi.seller.dashboard` | ✅ done |
| Shipping (incl. seeder) | `ProfitSaathi/Shipping/**` | `com.profitsaathi.seller.shipping` | ✅ done |
| AI (Gemini + OpenRouter + orchestrator + growth) | `ProfitSaathi/AI/**` | `com.profitsaathi.seller.ai` | ✅ done — API keys env-driven |
| WhatsApp session (WAHA) | `ProfitSaathi/WhatsApp/**` | `com.profitsaathi.seller.whatsapp` | ✅ done — service renamed to `WhatsAppSessionService` to disambiguate from notification module |
| Store (public storefront + onboarding) | `ProfitSaathi/User/Controller/Store**`, `User/DTO/Onboard|Preferences|PaymentSettings|StoreInfoResponse` | `com.profitsaathi.seller.store` + `seller/user/SellerService` | ✅ done — all PATCH endpoints (`/onboard`, `/preferences`, `/payment`, `/payment/qr`) |
| Seller account | `ProfitSaathi/User/Controller/UserController` | `com.profitsaathi.seller.user.SellerController` | ✅ done |
| UsageTracking (single canonical) | both apps' `UsageTracking/**` | `com.profitsaathi.usagetracking` | ✅ done — keyed on `credentials_id` so it works for sellers + customers |
| Cart | `ProfitSaathi User/Cart/**` | `com.profitsaathi.customer.cart` | ✅ done — Feign `ProductClient` replaced by direct `ProductRepository` |
| Customer profile + addresses | `ProfitSaathi User/User/**` | `com.profitsaathi.customer.user` | ✅ done — `/me`, `/update`, `/numbers`, `/addresses/*` |
| Notification — email (SMTP + Thymeleaf) | `notification_service/EmailLog`, `Services/EmailService`, `Services/EmailEventListener` | `com.profitsaathi.notification.email` + `notification.log` | ✅ done — Kafka listener replaced by `@Async` + `@Retryable` |
| Notification — WhatsApp (WAHA outbound) | `notification_service/WhatsApp/**`, `WhatsappLog/**` | `com.profitsaathi.notification.whatsapp` + `notification.log` | ✅ done — Kafka listener replaced by `@Async` + `@Retryable` |
| OTP | both apps' `OTP/**` | `com.profitsaathi.otp` | ✅ done — single canonical, calls `EmailService` / `WhatsAppService` directly |
| AES utilities | both apps' `Util/AES/**` + `notification-common/AESUtil` | `com.profitsaathi.util.aes` | ✅ done |
| Auth (replaces Keycloak) | new | `com.profitsaathi.auth` | ✅ done — JWT (HS256), BCrypt, Credentials + Seller/Customer signup |

### Things deliberately NOT ported (superseded by the new architecture)

| Old code | Why dropped |
|---|---|
| `Config/KeycloakConfig/**` (both apps) | Keycloak removed; `auth/AuthService` handles signup/login directly |
| `Config/Encryption/EncryptionService` | Spring Security's `PasswordEncoder` (BCrypt) is wired in `SecurityConfig` and used by `AuthService` |
| `Config/SecurityConfig/SecurityUtils.getCurrentKeycloakId()` | Replaced everywhere by `@AuthenticationPrincipal AuthenticatedPrincipal me` |
| `Config/ExceptionHandler/ErrorResponse` + old `GlobalExceptionHandler` | Replaced by richer `config/GlobalExceptionHandler` returning structured map |
| `User/Event/UserCreatedEvent` + `User/Listener/UserCreatedListener` | Welcome email is now a direct call inside `AuthService.signup*` (no event indirection) |
| `User/Service/MailService` (both apps) | Replaced by `notification.email.WelcomeMailService` |
| `Util/Email/EmailRequest` (both apps) | Internal DTO; superseded by `notification.email.EmailRequest` record |
| `Cart/Client/ProductClient` (Feign) | Service discovery removed; cart calls `ProductRepository` directly |
| `Payment/service/PaymentService` | The original was fully commented out — nothing to port |
| `notification_service/WhatsApp/DTO/WhatsAppOtpRequest` | Was a Kafka payload; replaced by `notification.whatsapp.WhatsAppMessage` record |
| `ServletInitializer` (all 3 apps) | We package as a fat JAR, not a WAR |
| `notification-common` artifact (`EmailEvent`, `WhatsAppEvent`, `AESUtil`, `Main`) | Inlined; AESUtil moved to `util.aes`, events replaced with in-process records |

## Running tests

```bash
./mvnw test
```
