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
import java.util.Map;

/**
 * NVIDIA NIM FLUX schnell — fastest of the FLUX family (4 steps), the
 * highest-quality option from the providers ProfitSaathi already supports.
 * Reuses the seller's NVIDIA NIM API key.
 *
 * Note: NIM uses a different host for image-generation (genai) endpoints
 * than for chat completions. {@link com.profitsaathi.seller.aichat.NvidiaChatProvider}
 * hits {@code integrate.api.nvidia.com}; this provider hits
 * {@code ai.api.nvidia.com}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NvidiaImageGenProvider implements ImageGenProvider {

    private static final String ENDPOINT =
            "https://ai.api.nvidia.com/v1/genai/black-forest-labs/flux.1-schnell";

    /**
     * Image generation is materially slower than chat — FLUX cold-starts on
     * NIM commonly take 30–90s, and the global {@code ai.request-timeout-seconds}
     * default of 60s is too tight. Use a dedicated 180s cap so the router
     * doesn't fall back to a paid Gemini call on a NIM cold-start.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(180);

    private final WebClient webClient;
    private final AiProperties props;

    @Override
    public AiProvider provider() { return AiProvider.NVIDIA; }

    @Override
    public ImageGenResult generate(String apiKey, String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new MissingApiKeyException(provider());
        }
        // FLUX schnell is guidance-distilled — it rejects any cfg_scale > 0
        // (NIM returns 422 if you try). It also doesn't accept `aspect_ratio`;
        // use explicit width/height instead. Keep this payload minimal so NIM
        // schema bumps don't surprise us.
        Map<String, Object> body = Map.of(
                "prompt", prompt,
                "width",  1024,
                "height", 1024,
                "seed",   System.currentTimeMillis() % Integer.MAX_VALUE,
                "steps",  4
        );
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = webClient.post()
                    .uri(ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(TIMEOUT);
            return parseResponse(resp);
        } catch (WebClientResponseException e) {
            log.error("NVIDIA FLUX HTTP {} body={}", e.getStatusCode(), LogScrub.truncate(e.getResponseBodyAsString()));
            int s = e.getStatusCode().value();
            if (s == 400 || s == 401 || s == 403 || s == 422) {
                throw new IllegalArgumentException(
                        "NVIDIA NIM rejected the image request — verify your key and remaining free credits.");
            }
            throw new IllegalStateException("NVIDIA NIM image-gen failed: " + e.getStatusCode());
        }
    }

    /**
     * NIM's response shape has varied between revisions. We tolerate the two
     * commonly seen forms: {@code {"image": "<base64>"}} and the older
     * {@code {"artifacts":[{"base64":"<base64>", "finishReason":"SUCCESS"}]}}.
     */
    @SuppressWarnings("unchecked")
    private static ImageGenResult parseResponse(Map<String, Object> resp) {
        if (resp == null) throw new IllegalStateException("Empty response from NVIDIA NIM");

        Object image = resp.get("image");
        if (image instanceof String s && !s.isBlank()) {
            return ImageGenResult.png(Base64.getDecoder().decode(s));
        }

        Object artifacts = resp.get("artifacts");
        if (artifacts instanceof java.util.List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> art) {
                Object b64 = art.get("base64");
                if (b64 instanceof String s2 && !s2.isBlank()) {
                    return ImageGenResult.png(Base64.getDecoder().decode(s2));
                }
            }
        }

        throw new IllegalStateException(
                "NVIDIA NIM image response missing image bytes: " + resp.keySet());
    }
}
