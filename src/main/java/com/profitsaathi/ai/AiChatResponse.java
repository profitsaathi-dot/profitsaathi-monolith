package com.profitsaathi.ai;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Two-shape response. {@code type} = "text" → only response/cost are set.
 * {@code type} = "payment_verification" → the verification fields are set
 * and {@code response} is null. Mirrors the Python service's payload so
 * existing clients can switch over without touching their parsing.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiChatResponse(
        String type,
        String response,
        BigDecimal cost,
        Boolean isValid,
        String decision,
        java.util.List<String> issues,
        Double confidence,
        Object extracted
) {
    public static AiChatResponse text(String response, BigDecimal cost) {
        return new AiChatResponse("text", response, cost, null, null, null, null, null);
    }

    public static AiChatResponse paymentVerification(PaymentVerificationResult v) {
        return new AiChatResponse(
                "payment_verification",
                null,
                BigDecimal.ZERO,
                v.isValid(),
                v.decision(),
                v.issues(),
                v.confidence(),
                v.extracted());
    }
}
