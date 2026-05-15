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
import java.util.Map;

/**
 * NVIDIA NIM image-to-image editing via FLUX.1 Kontext — Black Forest Labs'
 * dedicated edit model. Takes the seller's source photo + a text instruction
 * ("enhance lighting", "remove background", "studio quality") and returns
 * a re-rendered image preserving the subject.
 *
 * Free under NIM credits (same key as chat + text-to-image). This is the
 * provider that closes the "edit on free tier" gap — Gemini image-edit
 * needs paid billing, FLUX Kontext doesn't.
 *
 * NIM endpoint shape is the same family as
 * {@link NvidiaImageGenProvider} (flux.1-schnell) — only the model slug and
 * the addition of an {@code image} field change. If NVIDIA renames the
 * slug or moves to a different field for the source bytes, the {@link #ENDPOINT}
 * constant and {@link #INPUT_IMAGE_FIELD} are the two knobs to twist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NvidiaImageEditProvider {

    /**
     * Kontext-pro is the hosted production variant. Swap to {@code flux.1-kontext-dev}
     * if you want the open-weights dev model (lower quota, comparable quality).
     */
    private static final String ENDPOINT =
            "https://ai.api.nvidia.com/v1/genai/black-forest-labs/flux.1-kontext-pro";

    /**
     * NIM hasn't been entirely consistent here across model revisions —
     * the BFL Replicate API uses {@code input_image}; some NIM revisions
     * use {@code image}. We send under {@code image} (the more common one)
     * and document the swap so a 422 from NIM is a one-line fix.
     */
    private static final String INPUT_IMAGE_FIELD = "image";

    /**
     * Kontext is heavier than schnell (28 steps vs 4) and routinely takes
     * 30–90s on NIM, even more on cold-start. Override the global
     * {@code ai.request-timeout-seconds} (default 60s) here so a slow first
     * call doesn't get killed and fall back to paid Gemini.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(180);

    private final WebClient webClient;
    private final AiProperties props;

    public AiProvider provider() { return AiProvider.NVIDIA; }

    public ImageGenResult edit(String apiKey, String prompt, String inputMime, String inputBase64) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new MissingApiKeyException(provider());
        }
        if (inputBase64 == null || inputBase64.isBlank()) {
            throw new IllegalArgumentException("Edit requires the source image bytes.");
        }

        // NIM expects the image as base64 with the `data:<mime>;base64,` prefix
        // for FLUX Kontext (matches BFL's convention). Schnell text-to-image
        // doesn't take an image so the prefix doesn't apply there.
        String mime = inputMime == null ? "image/jpeg" : inputMime;
        String dataUrl = "data:" + mime + ";base64," + inputBase64;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", prompt);
        body.put(INPUT_IMAGE_FIELD, dataUrl);
        body.put("cfg_scale", 5);
        body.put("aspect_ratio", "1:1");
        body.put("seed", System.currentTimeMillis() % Integer.MAX_VALUE);
        body.put("steps", 28);

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
            // Truncate body for logs — NIM error responses can echo the
            // submitted base64 image, which would be megabytes in dev logs.
            log.error("NVIDIA Kontext HTTP {} body={}",
                    e.getStatusCode(), LogScrub.truncate(e.getResponseBodyAsString()));
            int s = e.getStatusCode().value();
            if (s == 400 || s == 401 || s == 403 || s == 422) {
                throw new IllegalArgumentException(
                        "NVIDIA NIM rejected the edit request — verify your key, remaining free credits, "
                                + "and that the source image is a clear PNG/JPG.");
            }
            throw new IllegalStateException("NVIDIA NIM image-edit failed: " + e.getStatusCode());
        }
    }

    /**
     * Same response shape as flux.1-schnell — defensively handles both the
     * {@code {"image": "<base64>"}} and the older
     * {@code {"artifacts":[{"base64":"<...>"}]}} forms NIM has shipped.
     */
    @SuppressWarnings("unchecked")
    private static ImageGenResult parseResponse(Map<String, Object> resp) {
        if (resp == null) throw new IllegalStateException("Empty response from NVIDIA NIM");

        Object image = resp.get("image");
        if (image instanceof String s && !s.isBlank()) {
            return ImageGenResult.png(Base64.getDecoder().decode(stripDataUrlPrefix(s)));
        }

        Object artifacts = resp.get("artifacts");
        if (artifacts instanceof java.util.List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> art) {
                Object b64 = art.get("base64");
                if (b64 instanceof String s2 && !s2.isBlank()) {
                    return ImageGenResult.png(Base64.getDecoder().decode(stripDataUrlPrefix(s2)));
                }
            }
        }

        throw new IllegalStateException(
                "NVIDIA NIM edit response missing image bytes: " + resp.keySet());
    }

    /**
     * Some NIM responses include the {@code data:image/png;base64,} prefix
     * even though they returned it inside a JSON string field — Base64
     * decode would choke on the prefix, so strip it first.
     */
    private static String stripDataUrlPrefix(String s) {
        int comma = s.indexOf(",");
        if (s.startsWith("data:") && comma > 0) return s.substring(comma + 1);
        return s;
    }
}
