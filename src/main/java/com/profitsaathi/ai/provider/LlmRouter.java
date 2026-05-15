package com.profitsaathi.ai.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Routes a chat request to the right text provider by model prefix:
 * <ul>
 *   <li>{@code gpt*} → {@link OpenAiProvider}</li>
 *   <li>{@code gemini*} → {@link GeminiProvider}</li>
 *   <li>{@code phi3*} → {@link OllamaProvider}</li>
 *   <li>otherwise → {@link OpenRouterProvider} (fallback)</li>
 * </ul>
 *
 * Constructor injection picks providers by type rather than by scanning a
 * collection so the routing logic is explicit and grep-friendly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmRouter {

    private final OpenAiProvider openAi;
    private final GeminiProvider gemini;
    private final OllamaProvider ollama;
    private final OpenRouterProvider openRouter;

    public LlmResult chat(String model, String prompt) {
        LlmProvider chosen = pick(model);
        log.debug("Routing model={} → {}", model, chosen.getClass().getSimpleName());
        return chosen.chat(model, prompt);
    }

    private LlmProvider pick(String model) {
        if (openAi.supports(model)) return openAi;
        if (gemini.supports(model)) return gemini;
        if (ollama.supports(model)) return ollama;
        return openRouter;
    }
}
