package com.profitsaathi.ai.provider;

import com.profitsaathi.ai.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Local-model provider over Ollama (default {@code phi3}). Used for
 * cost-free pricing-advice generations — see the system prompt in the
 * payload below for the original Python intent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaProvider implements LlmProvider {

    private static final String SYSTEM_PROMPT =
            "You are an expert pricing advisor helping Indian small businesses "
                    + "maximize profit while staying competitive.";

    private final WebClient webClient;
    private final AiProperties props;

    @Override
    public boolean supports(String model) {
        return model != null && model.startsWith("phi3");
    }

    @Override
    @SuppressWarnings("unchecked")
    public LlmResult chat(String model, String prompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "system", SYSTEM_PROMPT,
                "prompt", prompt,
                "stream", false,
                "options", Map.of(
                        "temperature", 0.2,
                        "top_p", 0.9,
                        "num_predict", 80,
                        "repeat_penalty", 1.1
                )
        );
        Map<String, Object> resp = webClient.post()
                .uri(props.getOllamaUrl() + "/api/generate")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(props.getRequestTimeoutSeconds()));
        if (resp == null) throw new IllegalStateException("Empty response from Ollama");
        String content = String.valueOf(resp.getOrDefault("response", ""));
        long tokens = ((Number) resp.getOrDefault("eval_count", 0)).longValue();
        return new LlmResult(content, tokens);
    }
}
