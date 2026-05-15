package com.profitsaathi.ai.provider;

import com.profitsaathi.ai.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiProvider implements LlmProvider {

    private final WebClient webClient;
    private final AiProperties props;

    @Override
    public boolean supports(String model) {
        return model != null && model.startsWith("gpt");
    }

    @Override
    @SuppressWarnings("unchecked")
    public LlmResult chat(String model, String prompt) {
        if (props.getOpenaiKey().isEmpty()) {
            throw new IllegalStateException("ai.openai-key not configured");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );
        Map<String, Object> resp = webClient.post()
                .uri(props.getOpenaiUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + props.getOpenaiKey())
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(props.getRequestTimeoutSeconds()));
        return parseChatCompletion(resp);
    }

    /** OpenAI-style {choices:[{message:{content}}], usage:{total_tokens}}. */
    @SuppressWarnings("unchecked")
    static LlmResult parseChatCompletion(Map<String, Object> resp) {
        if (resp == null) throw new IllegalStateException("Empty response from provider");
        List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("No choices in provider response: " + resp);
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = message == null ? "" : String.valueOf(message.getOrDefault("content", ""));
        Map<String, Object> usage = (Map<String, Object>) resp.getOrDefault("usage", Map.of());
        long tokens = ((Number) usage.getOrDefault("total_tokens", 0)).longValue();
        return new LlmResult(content, tokens);
    }
}
