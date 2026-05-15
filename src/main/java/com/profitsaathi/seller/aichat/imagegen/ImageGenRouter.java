package com.profitsaathi.seller.aichat.imagegen;

import com.profitsaathi.seller.aichat.AiProvider;
import com.profitsaathi.seller.aichat.AllAiProvidersFailedException;
import com.profitsaathi.seller.aichat.MissingApiKeyException;
import com.profitsaathi.seller.aichat.NoAiProviderConfiguredException;
import com.profitsaathi.seller.user.Seller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Routes an image-generation call through a fixed quality ranking:
 *
 *   1. {@link NvidiaImageGenProvider} — best quality (FLUX schnell), free credits
 *   2. {@link GeminiImageGenProvider} — paid but excellent
 *
 * Unlike chat (where the seller picks a primary), image-gen ordering is
 * fixed because the trade-offs are quality-driven, not preference-driven.
 * We always try NVIDIA first when its key is configured ("high task" per
 * the product spec), Gemini next.
 *
 * OpenRouter and Pollinations are intentionally absent: OpenRouter's free
 * image-gen tier is unreliable; Pollinations moved to a paid Pollen-credits
 * model so it no longer qualifies as a free fallback.
 *
 * If the seller has no key for either provider we throw
 * {@link NoAiProviderConfiguredException} so the controller can return 412
 * and the UI can prompt the seller to configure one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageGenRouter {

    private final NvidiaImageGenProvider nvidia;
    private final NvidiaImageEditProvider nvidiaEdit;
    private final GeminiImageGenProvider gemini;

    /**
     * @param providerUsed identifier shown to the seller — either
     *                     {@link AiProvider#NVIDIA} or {@link AiProvider#GEMINI}
     *                     (always one of {@link AiProvider#name()}).
     */
    public record Result(String providerUsed, ImageGenResult image) {}

    /**
     * Image-to-image edit. Tries NVIDIA FLUX Kontext first (free under NIM
     * credits), then Gemini (paid). Either provider's failure falls through
     * to the next; if no key is configured for either, throws
     * {@link NoAiProviderConfiguredException} so the UI points the seller
     * to Settings.
     *
     * NVIDIA goes first because the design goal is "edit works on a free
     * NIM key" — Gemini image-edit needs paid Google billing.
     */
    public Result editImage(Seller seller, String prompt, String inputMime, String inputBase64) {
        List<String> failures = new ArrayList<>();
        boolean anyKey = false;

        // 1. NVIDIA FLUX Kontext — free under NIM credits, preferred.
        if (seller.getNvidiaApiKey() != null && !seller.getNvidiaApiKey().isBlank()) {
            anyKey = true;
            try {
                ImageGenResult img = nvidiaEdit.edit(seller.getNvidiaApiKey(), prompt, inputMime, inputBase64);
                return new Result(AiProvider.NVIDIA.name(), img);
            } catch (MissingApiKeyException ignored) {
            } catch (RuntimeException e) {
                failures.add("NVIDIA: " + e.getMessage());
                log.warn("[image-edit] NVIDIA failed, falling back: {}", e.getMessage());
            }
        }

        // 2. Gemini image-edit — paid, last resort.
        if (seller.getGeminiApiKey() != null && !seller.getGeminiApiKey().isBlank()) {
            anyKey = true;
            try {
                ImageGenResult img = gemini.edit(seller.getGeminiApiKey(), prompt, inputMime, inputBase64);
                return new Result(AiProvider.GEMINI.name(), img);
            } catch (MissingApiKeyException ignored) {
            } catch (RuntimeException e) {
                failures.add("Gemini: " + e.getMessage());
                log.warn("[image-edit] Gemini failed: {}", e.getMessage());
            }
        }

        if (!anyKey) throw new NoAiProviderConfiguredException();
        throw new AllAiProvidersFailedException(failures);
    }

    public Result generate(Seller seller, String prompt) {
        List<String> failures = new ArrayList<>();
        boolean anyKeyConfigured = false;

        // 1. NVIDIA NIM FLUX — preferred when the seller has a key.
        if (seller.getNvidiaApiKey() != null && !seller.getNvidiaApiKey().isBlank()) {
            anyKeyConfigured = true;
            try {
                ImageGenResult img = nvidia.generate(seller.getNvidiaApiKey(), prompt);
                return new Result(AiProvider.NVIDIA.name(), img);
            } catch (MissingApiKeyException ignored) {
                // Defensive — skip silently.
            } catch (RuntimeException e) {
                failures.add("NVIDIA: " + e.getMessage());
                log.warn("[image-gen] NVIDIA failed, falling back: {}", e.getMessage());
            }
        }

        // 2. Gemini image generation.
        if (seller.getGeminiApiKey() != null && !seller.getGeminiApiKey().isBlank()) {
            anyKeyConfigured = true;
            try {
                ImageGenResult img = gemini.generate(seller.getGeminiApiKey(), prompt);
                return new Result(AiProvider.GEMINI.name(), img);
            } catch (MissingApiKeyException ignored) {
            } catch (RuntimeException e) {
                failures.add("Gemini: " + e.getMessage());
                log.warn("[image-gen] Gemini failed, falling back: {}", e.getMessage());
            }
        }

        if (!anyKeyConfigured) {
            throw new NoAiProviderConfiguredException();
        }
        throw new AllAiProvidersFailedException(failures);
    }
}
