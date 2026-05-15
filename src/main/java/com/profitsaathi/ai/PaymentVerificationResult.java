package com.profitsaathi.ai;

import java.util.List;

/**
 * Result of the vision payment-verification pipeline. Mirrors the Python
 * service's contract: AI extraction → backend validation → decision.
 *
 * decision is one of: APPROVED | REVIEW | REJECTED.
 */
public record PaymentVerificationResult(
        boolean isValid,
        String decision,
        List<String> issues,
        double confidence,
        Object extracted
) {
    public static PaymentVerificationResult fail(String reason) {
        return new PaymentVerificationResult(
                false,
                "REJECTED",
                List.of(reason),
                0.0,
                null);
    }
}
