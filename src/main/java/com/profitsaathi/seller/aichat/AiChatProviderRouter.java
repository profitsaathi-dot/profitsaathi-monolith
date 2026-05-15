package com.profitsaathi.seller.aichat;

import com.profitsaathi.seller.user.Seller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Routes a chat call through the seller's preferred provider, then falls
 * back through the others on failure. Order:
 *
 *   1. Seller's chosen primary (defaults to {@link AiProvider#GEMINI}).
 *   2. The two remaining providers in a fixed Gemini → OpenRouter → NVIDIA cycle.
 *
 * Providers without a configured API key are skipped silently. Providers
 * that fail with {@link IllegalArgumentException} (bad key / quota) are
 * counted as a fallback-eligible failure but their error is preserved so we
 * can surface it if everything else also fails. Network / 5xx failures
 * ({@link IllegalStateException}) likewise fall through.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiChatProviderRouter {

    private final GeminiChatProvider gemini;
    private final OpenRouterChatProvider openRouter;
    private final NvidiaChatProvider nvidia;

    public record Result(AiProvider providerUsed, String reply) {}

    public Result chat(Seller seller, List<ChatTurn> history, ChatTurn current) {
        Map<AiProvider, ChatProvider> impls = new EnumMap<>(AiProvider.class);
        impls.put(AiProvider.GEMINI, gemini);
        impls.put(AiProvider.OPENROUTER, openRouter);
        impls.put(AiProvider.NVIDIA, nvidia);

        // Preserve insertion order so callers see "preferred provider tried first".
        Set<AiProvider> order = new LinkedHashSet<>();
        AiProvider primary = AiProvider.fromString(seller.getAiPrimaryProvider());
        if (primary != null) order.add(primary);
        // Stable fallback order — Gemini first (best free tier), then OpenRouter, then NVIDIA.
        order.add(AiProvider.GEMINI);
        order.add(AiProvider.OPENROUTER);
        order.add(AiProvider.NVIDIA);

        List<String> failures = new ArrayList<>();
        boolean anyKeyConfigured = false;

        for (AiProvider p : order) {
            String key = keyFor(seller, p);
            if (key == null || key.isBlank()) {
                log.debug("[router] {} skipped — no key configured", p);
                continue;
            }
            anyKeyConfigured = true;
            try {
                String reply = impls.get(p).chat(key, seller.getLanguage(), history, current);
                if (reply == null || reply.isBlank()) {
                    failures.add(p.label() + ": empty reply");
                    continue;
                }
                return new Result(p, reply);
            } catch (MissingApiKeyException e) {
                // Shouldn't reach here since we already checked the key,
                // but keep it defensive.
                continue;
            } catch (IllegalArgumentException | IllegalStateException e) {
                failures.add(p.label() + ": " + e.getMessage());
                log.warn("[router] {} failed, falling back: {}", p, e.getMessage());
            }
        }

        if (!anyKeyConfigured) {
            throw new NoAiProviderConfiguredException();
        }
        throw new AllAiProvidersFailedException(failures);
    }

    private static String keyFor(Seller s, AiProvider p) {
        return switch (p) {
            case GEMINI     -> s.getGeminiApiKey();
            case OPENROUTER -> s.getOpenrouterApiKey();
            case NVIDIA     -> s.getNvidiaApiKey();
        };
    }
}
