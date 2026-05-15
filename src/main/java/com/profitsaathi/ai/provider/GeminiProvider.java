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
public class GeminiProvider implements LlmProvider {

    private final WebClient webClient;
    private final AiProperties props;

    @Override
    public boolean supports(String model) {
        return model != null && model.startsWith("gemini");
    }

    @Override
    @SuppressWarnings("unchecked")
    public LlmResult chat(String model, String prompt) {
        if (props.getGeminiKey().isEmpty()) {
            throw new IllegalStateException("ai.gemini-key not configured");
        }
        // Gemini's REST API takes the model name in the path: /models/{model}:generateContent.
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                ))
        );
        Map<String, Object> resp = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("generativelanguage.googleapis.com")
                        .path("/v1beta/models/{model}:generateContent")
                        .queryParam("key", props.getGeminiKey())
                        .build(model))
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(props.getRequestTimeoutSeconds()));
        if (resp == null) throw new IllegalStateException("Empty response from Gemini");
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) resp.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            log.error("Gemini unexpected response: {}", resp);
            throw new IllegalStateException("Gemini error: " + resp.getOrDefault("error", resp));
        }
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        String text = parts == null || parts.isEmpty()
                ? ""
                : String.valueOf(parts.get(0).getOrDefault("text", ""));
        // Gemini doesn't return a token count in v1beta — leave it 0.
        return new LlmResult(text, 0L);
    }
}
