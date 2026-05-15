package com.profitsaathi.ai.provider;

import com.profitsaathi.ai.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Catch-all provider for OpenRouter-served models (Claude, Mistral, Llama,
 * etc.). Selected when no other provider claims the model — see
 * {@link LlmRouter}. Reasoning channel is enabled so reasoning-capable
 * models surface chain-of-thought in {@code reasoning_details}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenRouterProvider implements LlmProvider {

    private final WebClient webClient;
    private final AiProperties props;

    @Override
    public boolean supports(String model) {
        // Fallback provider — claims everything not picked up earlier in
        // the router. The router calls this last after the prefix-bound
        // providers have all returned false.
        return true;
    }

    @Override
    public LlmResult chat(String model, String prompt) {
        return chatMessages(model, List.of(Map.of("role", "user", "content", prompt)));
    }

    /**
     * Multi-message variant for callers that need a full system+history+user
     * conversation (e.g. the Growth Adviser). Bypasses {@link LlmRouter} —
     * call this directly the same way payment verification calls
     * {@link OpenRouterVisionProvider} directly.
     */
    @SuppressWarnings("unchecked")
    public LlmResult chatMessages(String model, List<Map<String, Object>> messages) {
        if (props.getOpenrouterKey().isEmpty()) {
            throw new IllegalStateException("ai.openrouter-key not configured");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "reasoning", Map.of("enabled", true)
        );
        Map<String, Object> resp = webClient.post()
                .uri(props.getOpenrouterUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + props.getOpenrouterKey())
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", props.getSiteUrl())
                .header("X-OpenRouter-Title", props.getSiteName())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(props.getRequestTimeoutSeconds()));
        if (resp == null || !resp.containsKey("choices")) {
            log.error("OpenRouter unexpected response: {}", resp);
            throw new IllegalStateException("OpenRouter error: " + (resp == null ? "null" : resp.get("error")));
        }
        return OpenAiProvider.parseChatCompletion(resp);
    }
}
