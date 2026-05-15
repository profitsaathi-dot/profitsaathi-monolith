package com.profitsaathi.seller.aichat.imagegen;

import com.profitsaathi.ai.AiProperties;
import com.profitsaathi.seller.aichat.AiProvider;
import com.profitsaathi.seller.aichat.MissingApiKeyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini image generation via the v1beta {@code generateContent} API with
 * {@code responseModalities=[IMAGE, TEXT]}. Uses the same Gemini key the
 * seller already configured for chat.
 *
 * The model id is hardcoded but easy to swap if Google ships a successor —
 * the wire format is stable across Gemini image-capable models.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiImageGenProvider implements ImageGenProvider {

    /**
     * Image-output Gemini model. Google's recommended "go-to" as of 2026 is
     * {@code gemini-3.1-flash-image-preview}. Other valid swaps:
     *   - {@code gemini-3-pro-image-preview}  — higher quality, more expensive
     *   - {@code gemini-2.5-flash-image}      — older, cheaper
     *
     * All three are PAID — there is no free tier for Gemini image generation.
     * Sellers wanting a truly free path should configure NVIDIA NIM instead;
     * Gemini fires only when the seller has paid Gemini credits on their key.
     *
     * If you swap this name to a chat-only model (e.g. {@code gemini-2.5-flash}),
     * the API returns 200 with text but no inline_data and we fail with
     * "model may not support image output" — exactly the symptom this constant
     * was tuned to avoid.
     */
    private static final String MODEL = "gemini-3.1-flash-image-preview";

    /**
     * Image generation/edit is materially slower than chat — Gemini image
     * models commonly take 15–60s, and the global 60s
     * {@code ai.request-timeout-seconds} default leaves no headroom. 180s
     * matches the NVIDIA image-provider timeout so the router experience
     * is consistent across providers.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(180);

    private final WebClient webClient;
    private final AiProperties props;

    @Override
    public AiProvider provider() { return AiProvider.GEMINI; }

    @Override
    public ImageGenResult generate(String apiKey, String prompt) {
        return call(apiKey, prompt, null, null);
    }

    /**
     * Image-to-image edit. Same {@code generateContent} call as
     * {@link #generate(String, String)}, but with the seller's source image
     * sent as an additional {@code inline_data} part in the user content.
     * Gemini interprets the text as edit instructions ("enhance lighting",
     * "remove background", "make studio-quality", …) rather than a fresh
     * subject prompt.
     */
    public ImageGenResult edit(String apiKey, String prompt, String inputMime, String inputBase64) {
        if (inputBase64 == null || inputBase64.isBlank()) {
            throw new IllegalArgumentException("Edit requires the source image bytes.");
        }
        return call(apiKey, prompt, inputMime, inputBase64);
    }

    private ImageGenResult call(String apiKey, String prompt, String inputMime, String inputBase64) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new MissingApiKeyException(provider());
        }

        // Build the user content. When an input image is present, Gemini wants
        // {inline_data: {mime_type, data}} BEFORE the text part — that's the
        // documented "describe what to change" pattern.
        List<Map<String, Object>> parts = new java.util.ArrayList<>(2);
        if (inputBase64 != null && !inputBase64.isBlank()) {
            parts.add(Map.of("inline_data", Map.of(
                    "mime_type", inputMime == null ? "image/jpeg" : inputMime,
                    "data", inputBase64)));
        }
        parts.add(Map.of("text", prompt));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", parts)));
        body.put("generationConfig", Map.of(
                "responseModalities", List.of("IMAGE", "TEXT"),
                "temperature", 0.9));

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("generativelanguage.googleapis.com")
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(MODEL))
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(TIMEOUT);
            return parseResponse(resp);
        } catch (WebClientResponseException e) {
            String errBody = e.getResponseBodyAsString();
            log.error("Gemini image-gen HTTP {} body={}", e.getStatusCode(), LogScrub.truncate(errBody));
            int s = e.getStatusCode().value();
            // 429 with "free_tier" in the body = key has no paid billing.
            // Surface this verbatim so sellers know which knob to twist
            // (enable billing on Google AI Studio, or use NVIDIA instead).
            if (s == 429 && errBody != null && errBody.contains("free_tier")) {
                throw new IllegalArgumentException(
                        "Gemini image generation requires paid billing on your Google AI Studio key — "
                                + "the free tier doesn't include image models. "
                                + "Enable billing, or set NVIDIA NIM as your primary AI provider in Settings.");
            }
            if (s == 400 || s == 401 || s == 403 || s == 404) {
                throw new IllegalArgumentException(
                        "Gemini rejected the image request — your key may not have access to '"
                                + MODEL + "'. The model is paid on Google AI Studio.");
            }
            throw new IllegalStateException("Gemini image-gen failed: " + e.getStatusCode());
        }
    }

    @SuppressWarnings("unchecked")
    private static ImageGenResult parseResponse(Map<String, Object> resp) {
        if (resp == null) throw new IllegalStateException("Empty response from Gemini");
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) resp.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini returned no candidates: " + resp.getOrDefault("error", resp));
        }
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) throw new IllegalStateException("Gemini candidate missing content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null) throw new IllegalStateException("Gemini candidate missing parts");

        for (Map<String, Object> part : parts) {
            Map<String, Object> inline = (Map<String, Object>) part.get("inline_data");
            if (inline == null) inline = (Map<String, Object>) part.get("inlineData");
            if (inline == null) continue;
            Object data = inline.get("data");
            Object mime = inline.getOrDefault("mime_type", inline.get("mimeType"));
            if (data instanceof String s && !s.isBlank()) {
                String mimeStr = mime instanceof String ms ? ms : "image/png";
                return new ImageGenResult(mimeStr, Base64.getDecoder().decode(s));
            }
        }
        // Common cause: someone changed MODEL to a chat-only Gemini variant.
        // Image-output models are listed in the MODEL constant comment.
        throw new IllegalStateException(
                "Gemini response carried text only — '" + MODEL + "' did not return image bytes. "
                + "Either your API key lacks access to this paid image model, or the model id was swapped for a chat-only one.");
    }
}
