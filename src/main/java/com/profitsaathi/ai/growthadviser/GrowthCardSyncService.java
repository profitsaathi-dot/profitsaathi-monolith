package com.profitsaathi.ai.growthadviser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitsaathi.ai.AiProperties;
import com.profitsaathi.ai.log.AiChatLog;
import com.profitsaathi.ai.log.AiChatLogRepository;
import com.profitsaathi.ai.provider.LlmResult;
import com.profitsaathi.ai.provider.OpenRouterProvider;
import com.profitsaathi.seller.product.Product;
import com.profitsaathi.seller.product.ProductRepository;
import com.profitsaathi.seller.sales.SalesSummary;
import com.profitsaathi.seller.sales.SalesSummaryRepository;
import com.profitsaathi.seller.subscription.Subscription;
import com.profitsaathi.seller.subscription.SubscriptionService;
import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Generates a fresh batch of {@link GrowthCard} rows for a seller.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Cooldown check via {@link GrowthSyncState} — Free sellers get a
 *       24h cooldown, Premium gets 6h (configurable). Throws
 *       {@link CooldownActiveException} on the hot path so the controller
 *       can return HTTP 429 with the remaining wait.</li>
 *   <li>Build a structured seller context block (latest sales summary,
 *       product mix, festival hint).</li>
 *   <li>Ask OpenRouter to return a strict JSON array of cards. Free
 *       sellers route to the free model, paid to the premium model.</li>
 *   <li>Parse, validate, persist. Old ACTIVE cards are bulk-marked STALE
 *       in the same transaction so the dashboard always renders the
 *       latest batch.</li>
 * </ol>
 *
 * Failures don't update the sync state — a flaky upstream call shouldn't
 * burn a free seller's daily allowance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrowthCardSyncService {

    public static final int MAX_CARDS = 8;
    private static final int PROMPT_LOG_CAP = 4000;
    private static final int RESPONSE_LOG_CAP = 8000;

    private final OpenRouterProvider openRouter;
    private final SubscriptionService subscriptionService;
    private final SellerRepository sellerRepository;
    private final SalesSummaryRepository salesSummaryRepository;
    private final ProductRepository productRepository;
    private final GrowthCardRepository cardRepository;
    private final GrowthSyncStateRepository syncStateRepository;
    private final AiChatLogRepository logRepository;
    private final AiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class CooldownActiveException extends RuntimeException {
        private final Duration retryAfter;
        public CooldownActiveException(Duration retryAfter) {
            super("Sync cooldown active. Retry in " + retryAfter.toMinutes() + " min.");
            this.retryAfter = retryAfter;
        }
        public Duration retryAfter() { return retryAfter; }
    }

    /** Thrown when called for a seller_id with no matching seller row. */
    public static class SellerNotFoundException extends RuntimeException {
        public SellerNotFoundException(Long id) { super("Seller not found: " + id); }
    }

    public record SyncResult(
            int cardsGenerated,
            boolean premium,
            String model,
            LocalDateTime syncedAt,
            int syncsToday
    ) {}

    /**
     * @param source "manual" (controller) or "scheduled" (cron). Manual
     *               syncs enforce the cooldown; scheduled syncs are the
     *               source of the daily refresh and skip sellers already
     *               inside their cooldown.
     */
    @Transactional
    public SyncResult sync(Long sellerId, String source) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new SellerNotFoundException(sellerId));
        boolean premium = hasActivePremium(sellerId);

        GrowthSyncState state = syncStateRepository.findById(sellerId)
                .orElse(GrowthSyncState.builder().sellerId(sellerId).build());
        Duration cooldown = premium ? premiumCooldown() : freeCooldown();
        Duration sinceLast = state.getLastSyncAt() == null
                ? Duration.ofDays(365)
                : Duration.between(state.getLastSyncAt(), LocalDateTime.now());
        if (sinceLast.compareTo(cooldown) < 0) {
            throw new CooldownActiveException(cooldown.minus(sinceLast));
        }

        String model = premium
                ? props.getGrowthCardPremiumModel()
                : props.getGrowthCardFreeModel();

        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt(premium)),
                Map.of("role", "user", "content", userPrompt(seller, premium))
        );

        LlmResult result;
        try {
            result = openRouter.chatMessages(model, messages);
        } catch (Exception e) {
            log.warn("Growth card sync — primary model failed (model={}, premium={}): {}",
                    model, premium, e.getMessage());
            if (premium) {
                model = props.getGrowthCardFreeModel();
                result = openRouter.chatMessages(model, messages);
                premium = false;
            } else {
                throw e;
            }
        }

        List<GrowthCard> parsed = parseCards(result.response(), sellerId);
        if (parsed.isEmpty()) {
            log.warn("Growth card sync produced 0 cards for seller={} model={}", sellerId, model);
        }

        cardRepository.markActiveCardsStale(sellerId);
        LocalDateTime now = LocalDateTime.now();
        for (GrowthCard c : parsed) {
            c.setSyncRunAt(now);
            cardRepository.save(c);
        }

        // Log the prompt/response for admin observability.
        persistLog(seller, result.response(), model);

        // Record the successful sync — only after persistence succeeds, so
        // a failed parse doesn't burn the seller's daily allowance.
        String today = LocalDate.now().toString();
        if (!today.equals(state.getDayBucket())) {
            state.setDayBucket(today);
            state.setSyncsToday(0);
        }
        state.setLastSyncAt(now);
        state.setLastSource(source);
        state.setSyncsToday(state.getSyncsToday() + 1);
        syncStateRepository.save(state);

        return new SyncResult(parsed.size(), premium, model, now, state.getSyncsToday());
    }

    // ── prompt construction ──────────────────────────────────────────────

    private String systemPrompt(boolean premium) {
        return """
               You are ProfitSaathi's Growth Adviser, an expert business coach for Indian micro and small sellers.
               Your job: analyze the seller's data and produce concrete, India-specific growth recommendations as JSON cards.

               Output rules — strict:
               - Return ONLY a JSON array. No prose, no markdown fences, no commentary.
               - Each element MUST match this schema:
                 {
                   "type":  "PRICING" | "INVENTORY" | "MARKETING" | "CRM" | "FESTIVAL" | "FINANCE" | "OPERATIONS",
                   "title": string (≤80 chars, action-oriented),
                   "description": string (1-3 sentences, India-specific, INR amounts where useful),
                   "priority": "HIGH" | "MEDIUM" | "LOW",
                   "impact": string (e.g. "+₹12,000/month" or "Save ₹4,800") or null,
                   "cta": string (≤30 chars, e.g. "Open pricing"),
                   "actionUrl": "/pricing" | "/products" | "/orders" | "/whatsapp" | "/profit" | "/settings" | null,
                   "confidence": integer 0-100
                 }
               - Generate 5 to 8 cards. Skip categories where you have no actionable data.
               - Never invent specific INR numbers the seller did not share — if margin/sales are missing, frame the card as a question.
               - Mix priorities: at least 1 HIGH and at most 4 HIGH per batch.
               """ + (premium
                    ? "\n- You are running on the PREMIUM model — go deeper, mention concrete percentages, and tie advice to the seller's exact numbers."
                    : "\n- You are running on the FREE model — keep cards concise; sellers get unlimited reads but limited regenerations.");
    }

    private String userPrompt(Seller seller, boolean premium) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("Seller profile:\n");
        sb.append("- Store: ").append(safe(seller.getStoreName(), "(unnamed store)")).append("\n");
        sb.append("- Type: ").append(safe(seller.getSellerType(), "small business")).append("\n");
        sb.append("- Tier: ").append(premium ? "Premium" : "Free").append("\n");
        sb.append("- Preferred language: ").append(safe(seller.getLanguage(), "en")).append("\n");
        sb.append("- Today's date: ").append(LocalDate.now()).append("\n");
        sb.append("- Festival/season hint: ").append(festivalForToday()).append("\n");

        SalesSummary summary = salesSummaryRepository.findTopBySellerOrderByYearDescMonthDesc(seller);
        if (summary != null) {
            sb.append("\nLatest monthly snapshot (")
              .append(summary.getMonth()).append("/").append(summary.getYear()).append("):\n");
            sb.append("- Total sales: ₹").append(format(summary.getTotalSales())).append("\n");
            sb.append("- Total expenses: ₹").append(format(summary.getTotalExpenses())).append("\n");
            sb.append("- Net profit: ₹").append(format(summary.getNetProfit())).append("\n");
            if (summary.getProfitMargin() != null) {
                sb.append("- Profit margin: ").append(summary.getProfitMargin()).append("%\n");
            }
            if (summary.getHealthScore() != null) {
                sb.append("- Health score: ").append(summary.getHealthScore()).append("/100\n");
            }
        } else {
            sb.append("\nNo monthly sales summary on file yet — produce cards that ask the seller to record last month's sales/expenses, plus generic high-leverage advice.\n");
        }

        long totalProducts = productRepository.countBySellerId(seller.getId());
        long activeProducts = productRepository.countBySellerIdAndStatus(seller.getId(), "ACTIVE");
        sb.append("\nCatalog:\n");
        sb.append("- Total products: ").append(totalProducts).append("\n");
        sb.append("- Active products: ").append(activeProducts).append("\n");

        // Top products by selling price + cost margin — drives PRICING and FINANCE cards.
        List<Product> recent = productRepository
                .findBySellerId(seller.getId(), PageRequest.of(0, 12))
                .getContent();
        if (!recent.isEmpty()) {
            sb.append("- Sample products (name | sellingPrice | costPrice | margin%):\n");
            for (Product p : recent) {
                String margin = computeMargin(p.getSellingPrice(), p.getCostPrice());
                sb.append("  • ").append(safe(p.getName(), "(unnamed)"))
                  .append(" | ₹").append(format(p.getSellingPrice()))
                  .append(" | ₹").append(format(p.getCostPrice()))
                  .append(" | ").append(margin).append("\n");
            }
        }

        sb.append("\nProduce the JSON now.");
        return sb.toString();
    }

    // ── parsing ──────────────────────────────────────────────────────────

    private List<GrowthCard> parseCards(String raw, Long sellerId) {
        if (raw == null || raw.isBlank()) return List.of();
        String cleaned = stripFence(raw.trim());
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            if (!root.isArray()) {
                // Some models wrap the array under a top-level key — try common ones.
                if (root.has("cards") && root.get("cards").isArray()) root = root.get("cards");
                else if (root.has("data") && root.get("data").isArray()) root = root.get("data");
                else return List.of();
            }
            List<GrowthCard> out = new ArrayList<>();
            for (JsonNode node : root) {
                GrowthCard card = parseCard(node, sellerId);
                if (card != null) out.add(card);
                if (out.size() >= MAX_CARDS) break;
            }
            return out;
        } catch (Exception e) {
            log.warn("Failed to parse Growth Adviser JSON (len={}): {}", cleaned.length(), e.getMessage());
            return List.of();
        }
    }

    private GrowthCard parseCard(JsonNode node, Long sellerId) {
        if (!node.isObject()) return null;
        String title = text(node, "title");
        String description = text(node, "description");
        if (title == null || description == null) return null;

        GrowthCard.CardType type = enumOr(GrowthCard.CardType.class,
                text(node, "type"), GrowthCard.CardType.OPERATIONS);
        GrowthCard.Priority priority = enumOr(GrowthCard.Priority.class,
                text(node, "priority"), GrowthCard.Priority.MEDIUM);

        String actionUrl = text(node, "actionUrl");
        if (actionUrl != null && !actionUrl.startsWith("/")) {
            // Block external URLs — actionUrl is a path inside the seller app.
            actionUrl = null;
        }

        Integer confidence = node.hasNonNull("confidence") && node.get("confidence").isNumber()
                ? Math.max(0, Math.min(100, node.get("confidence").asInt()))
                : null;

        return GrowthCard.builder()
                .sellerId(sellerId)
                .type(type)
                .title(truncate(title, 200))
                .description(truncate(description, 2000))
                .priority(priority)
                .impact(truncate(text(node, "impact"), 64))
                .cta(truncate(text(node, "cta"), 64))
                .actionUrl(truncate(actionUrl, 255))
                .confidenceScore(confidence)
                .status(GrowthCard.Status.ACTIVE)
                .build();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private boolean hasActivePremium(Long sellerId) {
        return subscriptionService.getActive(sellerId)
                .map(this::isPaidPlan)
                .orElse(false);
    }

    private boolean isPaidPlan(Subscription sub) {
        if (sub == null || !"ACTIVE".equalsIgnoreCase(sub.getStatus())) return false;
        String plan = sub.getPlanName();
        if (plan == null) return false;
        String norm = plan.trim().toUpperCase();
        return !norm.isEmpty() && !norm.equals("FREE") && !norm.equals("TRIAL");
    }

    private Duration freeCooldown() {
        return Duration.ofHours(Math.max(1, props.getGrowthCardCooldownHoursFree()));
    }

    private Duration premiumCooldown() {
        return Duration.ofHours(Math.max(1, props.getGrowthCardCooldownHoursPremium()));
    }

    private void persistLog(Seller seller, String response, String model) {
        try {
            logRepository.save(AiChatLog.builder()
                    .principalEmail(seller.getEmail())
                    .principalRole("SELLER")
                    .model(model)
                    .prompt(truncate("growth-card sync for sellerId=" + seller.getId(), PROMPT_LOG_CAP))
                    .response(truncate(response, RESPONSE_LOG_CAP))
                    .cost(BigDecimal.ZERO)
                    .responseType("growth_card_sync")
                    .build());
        } catch (Exception e) {
            log.warn("Growth card log persist failed: {}", e.getMessage());
        }
    }

    private static String stripFence(String s) {
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int closing = s.lastIndexOf("```");
            if (firstNewline > 0 && closing > firstNewline) {
                return s.substring(firstNewline + 1, closing).trim();
            }
        }
        return s;
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) return null;
        String v = node.get(field).asText().trim();
        return v.isEmpty() ? null : v;
    }

    private static String truncate(String s, int cap) {
        if (s == null) return null;
        return s.length() <= cap ? s : s.substring(0, cap);
    }

    private static String safe(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    private static String format(BigDecimal v) {
        return v == null ? "0" : v.toPlainString();
    }

    private static String computeMargin(BigDecimal selling, BigDecimal cost) {
        if (selling == null || cost == null || selling.signum() <= 0) return "n/a";
        BigDecimal margin = selling.subtract(cost)
                .multiply(BigDecimal.valueOf(100))
                .divide(selling, 1, java.math.RoundingMode.HALF_UP);
        return margin + "%";
    }

    private static String festivalForToday() {
        return switch (LocalDate.now().getMonthValue()) {
            case 1 -> "Makar Sankranti / Republic Day";
            case 3 -> "Holi season";
            case 4 -> "Akshaya Tritiya / Eid";
            case 8 -> "Raksha Bandhan / Independence Day";
            case 9 -> "Ganesh Chaturthi / Onam";
            case 10 -> "Navratri / Diwali run-up";
            case 11 -> "Diwali / wedding season";
            case 12 -> "Christmas / New Year";
            default -> "Regular trading month";
        };
    }
}
