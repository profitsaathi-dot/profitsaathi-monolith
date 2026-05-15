package com.profitsaathi.seller.aichat;

import com.profitsaathi.ai.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini v1beta {@code generateContent} client. Multimodal: takes
 * text + an optional inline image. Free on AI Studio's free tier with
 * generous limits on {@code gemini-2.5-flash}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiChatProvider implements ChatProvider {

    private static final String MODEL = "gemini-2.5-flash";

    private final WebClient webClient;
    private final AiProperties props;

    @Override
    public AiProvider provider() { return AiProvider.GEMINI; }

    @Override
    public String chat(String apiKey, String language, List<ChatTurn> history, ChatTurn current) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new MissingApiKeyException(provider());
        }

        List<Map<String, Object>> contents = new ArrayList<>(history.size() + 1);
        for (ChatTurn t : history) contents.add(toContent(t));
        contents.add(toContent(current));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("system_instruction", Map.of(
                "parts", List.of(Map.of("text", ChatSystemPrompt.forLanguage(language)))));
        body.put("contents", contents);
        body.put("generationConfig", Map.of(
                "temperature", 0.8,
                "maxOutputTokens", 1024));

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("generativelanguage.googleapis.com")
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(MODEL))
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(props.getRequestTimeoutSeconds()));
            return extractText(resp);
        } catch (WebClientResponseException e) {
            log.error("Gemini HTTP {} body={}", e.getStatusCode(),
                    com.profitsaathi.seller.aichat.imagegen.LogScrub.truncate(e.getResponseBodyAsString()));
            int s = e.getStatusCode().value();
            if (s == 400 || s == 401 || s == 403) {
                throw new IllegalArgumentException(
                        "Gemini rejected the request — check that your Gemini API key is valid and the model has free-tier quota.");
            }
            throw new IllegalStateException("Gemini call failed: " + e.getStatusCode());
        }
    }

    private static Map<String, Object> toContent(ChatTurn t) {
        List<Map<String, Object>> parts = new ArrayList<>(2);
        if (t.hasImage()) {
            parts.add(Map.of("inline_data", Map.of(
                    "mime_type", t.imageMime() == null ? "image/jpeg" : t.imageMime(),
                    "data", t.imageBase64())));
        }
        if (t.text() != null && !t.text().isBlank()) {
            parts.add(Map.of("text", t.text()));
        }
        // Gemini uses "user" / "model" (not "assistant").
        String role = t.isUser() ? "user" : "model";
        return Map.of("role", role, "parts", parts);
    }

    @SuppressWarnings("unchecked")
    private static String extractText(Map<String, Object> resp) {
        if (resp == null) throw new IllegalStateException("Empty response from Gemini");
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) resp.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini returned no candidates: " + resp.getOrDefault("error", resp));
        }
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) return "";
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> p : parts) {
            Object txt = p.get("text");
            if (txt != null) sb.append(txt);
        }
        return sb.toString();
    }
}
