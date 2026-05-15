# Migration guide — porting the remaining domain modules

The auth, notification, OTP, identity, and AES utilities are already in the
monolith (`com.profitsaathi.*`). The seller-side and customer-side domain
modules that have **no Keycloak or Kafka coupling** can be ported with a
small mechanical pass. This file lists every transformation you need to
apply, and the few places you need to think.

## 1. Mechanical search-and-replace

Apply these rewrites to every Java file you copy from the old apps into
`profitsaathi-monolith/src/main/java/com/profitsaathi/<new-package>/`:

| From | To |
|---|---|
| `package com.ask.ProfitSaathi.<X>;` | `package com.profitsaathi.<new-package>;` |
| `import com.ask.ProfitSaathi.User.Entity.User;` | `import com.profitsaathi.seller.user.Seller;` (seller side) **or** `import com.profitsaathi.customer.user.Customer;` (customer side) |
| `import com.ask.ProfitSaathi.User.Enum.UserStatus;` | `import com.profitsaathi.seller.user.Seller.Status;` (or `Customer.Status`) |
| `import com.ask.ProfitSaathi.Util.AES.Converter.EncryptedStringConverter;` | `import com.profitsaathi.util.aes.EncryptedStringConverter;` |
| `import com.ask.ProfitSaathi.Util.AES.Services.AESServices;` | `import com.profitsaathi.util.aes.AESService;` |
| `import com.ask.ProfitSaathi.Util.AES.Services.AESRequest;` | `import com.profitsaathi.util.aes.AESRequest;` |
| `import org.ask.Util.AES.AESUtil;` | `import com.profitsaathi.util.aes.AESUtil;` |
| `import org.ask.Event.EmailEvent;` | **delete** — use `com.profitsaathi.notification.email.EmailRequest` |
| `import org.ask.Event.WhatsAppEvent;` | **delete** — use `com.profitsaathi.notification.whatsapp.WhatsAppMessage` |

## 2. Drop Keycloak everywhere

The whole `Config/KeycloakConfig/` package is gone. Anywhere a controller or
service used the JWT to identify the seller/customer, replace with
`@AuthenticationPrincipal AuthenticatedPrincipal me`:

```java
// BEFORE
@GetMapping("/me")
public User me(@AuthenticationPrincipal Jwt jwt) {
    String keycloakId = jwt.getSubject();
    return userService.findByKeycloakId(keycloakId);
}

// AFTER
@GetMapping("/me")
public Seller me(@AuthenticationPrincipal AuthenticatedPrincipal me) {
    return sellerRepository.findById(me.subjectId())
        .orElseThrow(() -> new IllegalStateException("Seller not found"));
}
```

For the seller domain replace `User` with `Seller`, `userRepository` with
`sellerRepository`, `findByKeycloakId(...)` with `findById(me.subjectId())`.

For the customer domain do the same with `Customer` / `customerRepository`.

`KeycloakUserService.createUser(...)` calls disappear — auth is now handled by
`AuthService.signupSeller / signupCustomer`. Any place the old `UserService`
called Keycloak admin client should just be deleted.

## 3. Replace Kafka producers with direct calls

### Old Kafka producer

```java
@Autowired KafkaTemplate<String, EmailEvent> kafka;

EmailEvent event = new EmailEvent();
event.setTo(List.of(user.getEmail()));
event.setSubject("Order shipped");
event.setTemplateName("welcome");
event.setVariables(Map.of("name", user.getName()));
kafka.send("email-topic", event);
```

### New direct call

```java
@Autowired EmailService emailService;

emailService.send(EmailRequest.builder()
    .to(List.of(seller.getEmail()))
    .subject("Order shipped")
    .templateName("welcome")
    .variables(Map.of("name", seller.getName()))
    .build());
```

`EmailService.send` is `@Async` + `@Retryable`, so the caller still doesn't
block on SMTP and transient failures still retry — same semantics, no broker.

For WhatsApp, the equivalent swap:

```java
// OLD
WhatsAppEvent event = new WhatsAppEvent();
event.setChatId(chatId);
event.setText("Your order has shipped: " + url);
whatsappKafka.send("whatsapp-topic", event);

// NEW
whatsAppService.send(WhatsAppMessage.builder()
    .chatId(chatId)
    .text("Your order has shipped: " + url)
    .build());
```

### `OrderNotificationService` (the one user-visible Kafka producer)

This one’s easy — it’s already a thin wrapper. Drop the `KafkaTemplate` field,
inject `WhatsAppService` instead, and replace `kafka.send(...)` with
`whatsAppService.send(...)`. Behavior is identical.

## 4. Drop Eureka / OpenFeign

In the customer-side `Cart` module, `ProductClient` (Feign) goes away.
Replace it with direct repository access:

```java
// OLD
@FeignClient(name = "product-service", url = "http://localhost:8084/...")
public interface ProductClient {
    ProductDTO getById(@PathVariable Long id);
}

// NEW
@Autowired ProductRepository productRepository;

Product p = productRepository.findById(id)
    .orElseThrow(() -> new EntityNotFoundException("Product " + id));
```

`@EnableFeignClients` and the `spring-cloud-starter-openfeign` /
`spring-cloud-starter-netflix-eureka-client` dependencies are not in
the new `pom.xml`. Just delete those imports.

## 5. Audit fields

`SecurityAuditorAware` is wired up. `@LastModifiedBy` columns will receive the
current principal's email automatically. No code change needed in entities —
they already have `@EntityListeners(AuditingEntityListener.class)`.

## 6. Two-collision domain

A handful of modules existed in **both** old apps (`OtpEntity`,
`UsageTracking`, `MailService`):
- The OTP module is already merged in `com.profitsaathi.otp`.
- `MailService` is replaced by `WelcomeMailService` + direct `EmailService.send`.
- `UsageTracking`: pick whichever copy is more complete; both used the same
  `usage_tracking` table so the schema is unchanged. Move it to
  `com.profitsaathi.usagetracking`.

## 7. Public endpoints

`SecurityConfig` already permits the same public endpoints the two old apps
permitted (`/api/v1/products/**` GET, `/api/v1/order/track/**`,
`/api/v1/payment/verify`, `/api/v1/payment/upload`, `/api/v1/whatsapp/webhook`,
OTP send/verify). If you add a new public endpoint, allow-list it there.

## 8. Things that **don't** need to change

- All `@Entity` classes (other than the `User` ones) — table names and columns
  are unchanged. They land in the same Postgres DB.
- Razorpay integration code — only the credential source changes
  (`razorpay.key.*` now reads from env in uat/prod, inline in local).
- ZXing QR generation, AI suggestion services, scheduled tasks — they don't
  touch Keycloak or Kafka, just repackage and they work.
- Email templates (`templates/email/welcome.html`, `templates/email/otp.html`)
  are already copied verbatim.
