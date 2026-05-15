package com.profitsaathi.seller.smartPricing.ai;



import com.profitsaathi.seller.aichat.*;
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

import static com.profitsaathi.seller.aichat.AiProvider.*;

/**
 * Simple AI text router without chat history.
 *
 * Features:
 * - Uses seller preferred provider first
 * - Automatic fallback support
 * - No chat history required
 * - Returns plain AI response
 * - Skips missing API keys
 * - Handles provider failures gracefully
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTextProviderRouter {

    private final GeminiChatProvider gemini;
    private final OpenRouterChatProvider openRouter;
    private final NvidiaChatProvider nvidia;

    public record Result(
            AiProvider providerUsed,
            String reply
    ) {}

    public Result ask(
            Seller seller,
            String prompt
    ) {

        Map<AiProvider, ChatProvider> impls =
                new EnumMap<>(AiProvider.class);

        impls.put(AiProvider.GEMINI, gemini);
        impls.put(AiProvider.OPENROUTER, openRouter);
        impls.put(AiProvider.NVIDIA, nvidia);

        // Provider order
        Set<AiProvider> order =
                new LinkedHashSet<>();

        AiProvider primary =
                AiProvider.fromString(
                        seller.getAiPrimaryProvider()
                );

        if (primary != null) {
            order.add(primary);
        }

        // Stable fallback order
        order.add(AiProvider.GEMINI);
        order.add(AiProvider.OPENROUTER);
        order.add(AiProvider.NVIDIA);

        List<String> failures =
                new ArrayList<>();

        boolean anyKeyConfigured = false;

        for (AiProvider provider : order) {

            String key =
                    keyFor(seller, provider);

            // Skip if no key
            if (key == null || key.isBlank()) {

                log.debug(
                        "[router] {} skipped — no API key",
                        provider
                );

                continue;
            }

            anyKeyConfigured = true;

            try {

                ChatProvider chatProvider =
                        impls.get(provider);

                String response =
                        chatProvider.chat(
                                key,
                                seller.getLanguage(),
                                List.of(), // no history
                                ChatTurn.user(prompt)
                        );

                if (response == null
                        || response.isBlank()) {

                    failures.add(
                            provider.label()
                                    + ": empty response"
                    );

                    continue;
                }

                log.info(
                        "[router] success using {}",
                        provider
                );

                return new Result(
                        provider,
                        response
                );

            } catch (
                    MissingApiKeyException e
            ) {

                log.warn(
                        "[router] {} missing API key",
                        provider
                );

            } catch (
                    IllegalArgumentException
                    | IllegalStateException e
            ) {

                failures.add(
                        provider.label()
                                + ": "
                                + e.getMessage()
                );

                log.warn(
                        "[router] {} failed, fallback triggered: {}",
                        provider,
                        e.getMessage()
                );
            }
        }

        if (!anyKeyConfigured) {
            throw new NoAiProviderConfiguredException();
        }

        throw new AllAiProvidersFailedException(
                failures
        );
    }

    private static String keyFor(
            Seller seller,
            AiProvider provider
    ) {

        return switch (provider) {

            case GEMINI ->
                    seller.getGeminiApiKey();

            case OPENROUTER ->
                    seller.getOpenrouterApiKey();

            case NVIDIA ->
                    seller.getNvidiaApiKey();
        };
    }
}
