package com.profitsaathi.ai;

import com.profitsaathi.ai.cache.AiPromptCache;
import com.profitsaathi.ai.cost.AiCostTracker;
import com.profitsaathi.ai.log.AiChatLog;
import com.profitsaathi.ai.log.AiChatLogRepository;
import com.profitsaathi.ai.provider.LlmResult;
import com.profitsaathi.ai.provider.LlmRouter;
import com.profitsaathi.ai.provider.OpenRouterVisionProvider;
import com.profitsaathi.auth.AuthenticatedPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Orchestration layer for the AI module — mirrors the Python ChatService:
 *
 * <ol>
 *   <li>Cache lookup (text only).</li>
 *   <li>Route to provider via {@link LlmRouter}.</li>
 *   <li>Compute cost from {@link AiCostTracker}.</li>
 *   <li>Persist a {@link AiChatLog} row.</li>
 *   <li>Cache the response (text only) and return.</li>
 * </ol>
 *
 * Image-bearing requests (payment verification) bypass the cache and use
 * {@link OpenRouterVisionProvider} directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final int PROMPT_LOG_CAP = 4000;
    private static final int RESPONSE_LOG_CAP = 8000;

    private final LlmRouter router;
    private final OpenRouterVisionProvider visionProvider;
    private final AiCostTracker costTracker;
    private final AiPromptCache cache;
    private final AiChatLogRepository logRepository;
    private final AiProperties props;

    public AiChatResponse chat(AiChatRequest req, AuthenticatedPrincipal me) {
        String model = req.model() == null || req.model().isBlank()
                ? props.getDefaultModel()
                : req.model();

        // Vision-payment requests funnel through the dedicated entry — see
        // verifyPayment() below. The chat endpoint itself only handles text.
        return cache.get(model, req.message())
                .orElseGet(() -> {
                    LlmResult result = router.chat(model, req.message());
                    BigDecimal cost = costTracker.calculate(model, result.tokens());
                    AiChatResponse response = AiChatResponse.text(result.response(), cost);
                    persist(req.message(), result.response(), model, cost, "text", me);
                    cache.put(model, req.message(), response);
                    return response;
                });
    }

    public AiChatResponse verifyPayment(byte[] imageBytes,
                                        Double expectedAmount,
                                        String expectedReceiver,
                                        AuthenticatedPrincipal me) {
        PaymentVerificationResult result = visionProvider.verifyPayment(
                imageBytes, expectedAmount, expectedReceiver, null);
        AiChatResponse response = AiChatResponse.paymentVerification(result);
        persist(
                "IMAGE_PAYMENT_VERIFICATION",
                String.valueOf(result),
                "vision-payment",
                BigDecimal.ZERO,
                "payment_verification",
                me);
        return response;
    }

    private void persist(String prompt,
                         String response,
                         String model,
                         BigDecimal cost,
                         String type,
                         AuthenticatedPrincipal me) {
        try {
            logRepository.save(AiChatLog.builder()
                    .principalEmail(me == null ? null : me.email())
                    .principalRole(me == null ? null : me.role())
                    .model(model)
                    .prompt(truncate(prompt, PROMPT_LOG_CAP))
                    .response(truncate(response, RESPONSE_LOG_CAP))
                    .cost(cost)
                    .responseType(type)
                    .build());
        } catch (Exception e) {
            // Logging must never block the user-facing response.
            log.warn("Failed to persist AiChatLog: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int cap) {
        if (s == null) return null;
        if (s.length() <= cap) return s;
        return s.substring(0, cap - 3) + "...";
    }
}
