package com.profitsaathi.ai.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitsaathi.ai.AiProperties;
import com.profitsaathi.ai.PaymentVerificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Three-stage payment-verification pipeline for upload-payment-screenshot
 * flows:
 *
 * <ol>
 *   <li>Vision LLM extracts a strict JSON schema from the image.</li>
 *   <li>{@link #safeParse} tolerates markdown fences / whitespace.</li>
 *   <li>{@link #validate} layers backend rules (status, txn id, amount,
 *       receiver, confidence) before deciding APPROVED / REVIEW / REJECTED.</li>
 * </ol>
 *
 * Mirrors the Python {@code OpenRouterVisionClient}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenRouterVisionProvider {

    private static final String VERIFICATION_PROMPT = """
            You are a payment verification system.

            Analyze the image and extract payment details.

            Return ONLY valid JSON. No explanation.

            Schema:
            {
              "amount": number,
              "receiver": string,
              "upi_id": string,
              "transaction_id": string,
              "date_time": string,
              "status": "SUCCESS" | "FAILED" | "PENDING" | "UNKNOWN",
              "is_valid": boolean,
              "confidence": number (0-1),
              "issues": string[]
            }

            Rules:
            - status must be SUCCESS to be valid
            - transaction_id must exist
            - amount must be clearly visible
            - detect fake/edited screenshots
            - detect blur, cropping, UI mismatch

            If unsure:
            - is_valid = false
            - add reason in issues
            """;

    private final WebClient webClient;
    private final AiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentVerificationResult verifyPayment(byte[] imageBytes,
                                                   Double expectedAmount,
                                                   String expectedReceiver,
                                                   String model) {
        try {
            String raw = analyzeImage(model == null ? props.getVisionModel() : model, imageBytes);
            Map<String, Object> data = safeParse(raw);
            if (data == null) {
                return PaymentVerificationResult.fail("Invalid AI JSON response");
            }
            return validate(data, expectedAmount, expectedReceiver);
        } catch (Exception e) {
            log.error("Vision verification failed", e);
            return PaymentVerificationResult.fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String analyzeImage(String model, byte[] imageBytes) {
        if (props.getOpenrouterKey().isEmpty()) {
            throw new IllegalStateException("ai.openrouter-key not configured");
        }
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", VERIFICATION_PROMPT),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", "Extract payment details from this image"),
                                Map.of("type", "image_url",
                                        "image_url", Map.of(
                                                "url", "data:image/png;base64," + base64))
                        ))
                )
        );
        Map<String, Object> resp = webClient.post()
                .uri(props.getOpenrouterUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + props.getOpenrouterKey())
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(props.getRequestTimeoutSeconds()));
        if (resp == null) throw new IllegalStateException("Empty response from vision provider");
        List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("Vision provider error: " + resp.get("error"));
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return message == null ? "" : String.valueOf(message.getOrDefault("content", ""));
    }

    /** Strips markdown fences and parses JSON; returns null on any failure. */
    Map<String, Object> safeParse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replace("```json", "").replace("```", "").strip();
        }
        try {
            return objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("JSON parse failed for vision response: {}", raw);
            return null;
        }
    }

    /** Backend rules layered on top of the AI extraction. */
    @SuppressWarnings("unchecked")
    PaymentVerificationResult validate(Map<String, Object> data,
                                       Double expectedAmount,
                                       String expectedReceiver) {
        List<String> issues = new ArrayList<>();
        Object existingIssues = data.get("issues");
        if (existingIssues instanceof List<?> list) {
            for (Object o : list) issues.add(String.valueOf(o));
        }

        // Rule 1: status must be SUCCESS
        if (!"SUCCESS".equals(String.valueOf(data.get("status")))) {
            issues.add("Payment not successful");
        }

        // Rule 2: transaction id mandatory
        Object txnId = data.get("transaction_id");
        if (txnId == null || String.valueOf(txnId).isBlank()) {
            issues.add("Missing transaction ID");
        }

        // Rule 3: amount check
        if (expectedAmount != null) {
            try {
                double amount = Double.parseDouble(String.valueOf(data.getOrDefault("amount", 0)));
                if (Math.abs(amount - expectedAmount) > 1.0) {
                    issues.add("Amount mismatch");
                }
            } catch (NumberFormatException e) {
                issues.add("Invalid amount format");
            }
        }

        // Rule 4: receiver check
        if (expectedReceiver != null && !expectedReceiver.isBlank()) {
            String receiver = String.valueOf(data.getOrDefault("receiver", "")).toLowerCase();
            if (!receiver.contains(expectedReceiver.toLowerCase())) {
                issues.add("Receiver mismatch");
            }
        }

        // Rule 5: confidence threshold
        double confidence;
        try {
            confidence = Double.parseDouble(String.valueOf(data.getOrDefault("confidence", 0)));
        } catch (NumberFormatException e) {
            confidence = 0.0;
            issues.add("Invalid confidence value");
        }
        if (confidence < 0.7) issues.add("Low confidence OCR");

        boolean isValid = issues.isEmpty();
        return new PaymentVerificationResult(
                isValid,
                decision(isValid, confidence),
                issues,
                confidence,
                data);
    }

    static String decision(boolean isValid, double confidence) {
        if (isValid && confidence > 0.85) return "APPROVED";
        if (confidence < 0.5) return "REJECTED";
        return "REVIEW";
    }
}
