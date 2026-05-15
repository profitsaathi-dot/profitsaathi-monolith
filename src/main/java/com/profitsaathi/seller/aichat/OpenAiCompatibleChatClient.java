package com.profitsaathi.seller.aichat;

import com.profitsaathi.ai.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Shared transport for any provider that speaks the OpenAI-compatible
 * {@code /v1/chat/completions} contract. OpenRouter and NVIDIA NIM both
 * use this wire format, so the only thing that varies between them is the
 * base URL, the model id, and optional vendor headers (HTTP-Referer for
 * OpenRouter, etc.). Subclasses provide all three.
 */
@Slf4j
@RequiredArgsConstructor
abstract class OpenAiCompatibleChatClient implements ChatProvider {

    protected final WebClient webClient;
    protected final AiProperties props;

    /** Full URL of the {@code /chat/completions} endpoint, e.g. https://openrouter.ai/api/v1/chat/completions */
    protected abstract String chatCompletionsUrl();

    /** Model id to call. Free-tier preferred. */
    protected abstract String model();

    /** Optional vendor headers — OpenRouter needs HTTP-Referer / X-Title. */
    protected void customHeaders(Consumer<Map.Entry<String, String>> sink) { }

    @Override
    public String chat(String apiKey, String language, List<ChatTurn> history, ChatTurn current) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new MissingApiKeyException(provider());
        }

        List<Map<String, Object>> messages = new ArrayList<>(history.size() + 2);
        messages.add(Map.of("role", "system", "content", ChatSystemPrompt.forLanguage(language)));
        for (ChatTurn t : history) messages.add(toMessage(t));
        messages.add(toMessage(current));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("messages", messages);
        body.put("temperature", 0.8);
        body.put("max_tokens", 1024);

        try {
            var spec = webClient.post()
                    .uri(chatCompletionsUrl())
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");
            customHeaders(h -> spec.header(h.getKey(), h.getValue()));

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = spec
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(props.getRequestTimeoutSeconds()));
            return extractText(resp);
        } catch (WebClientResponseException e) {
            log.error("{} HTTP {} body={}", provider(), e.getStatusCode(),
                    com.profitsaathi.seller.aichat.imagegen.LogScrub.truncate(e.getResponseBodyAsString()));
            int s = e.getStatusCode().value();
            if (s == 400 || s == 401 || s == 403) {
                throw new IllegalArgumentException(
                        provider().label() + " rejected the request — verify your API key and free-tier quota.");
            }
            throw new IllegalStateException(provider().label() + " call failed: " + e.getStatusCode());
        }
    }

    /**
     * OpenAI chat-completions {@code messages[]} entry. When the turn carries
     * an image, the {@code content} becomes an array of {text, image_url}
     * parts (a data: URL) instead of a plain string.
     */
    protected Map<String, Object> toMessage(ChatTurn t) {
        String role = t.isUser() ? "user" : "assistant";
        if (!t.hasImage()) {
            return Map.of("role", role, "content", t.text() == null ? "" : t.text());
        }
        List<Map<String, Object>> parts = new ArrayList<>(2);
        if (t.text() != null && !t.text().isBlank()) {
            parts.add(Map.of("type", "text", "text", t.text()));
        }
        String mime = t.imageMime() == null ? "image/jpeg" : t.imageMime();
        parts.add(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", "data:" + mime + ";base64," + t.imageBase64())));
        return Map.of("role", role, "content", parts);
    }

    @SuppressWarnings("unchecked")
    protected static String extractText(Map<String, Object> resp) {
        if (resp == null) throw new IllegalStateException("Empty response from provider");
        List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("Provider returned no choices: " + resp.getOrDefault("error", resp));
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) return "";
        Object content = message.get("content");
        if (content instanceof String s) return s;
        if (content instanceof List<?> list) {
            // Some providers return content as a parts-array. Join the text parts.
            // Note: `part` is Map<?, ?> here, so its value type is a wildcard capture —
            // getOrDefault won't accept a typed default. Use get + null-check instead.
            StringBuilder sb = new StringBuilder();
            for (Object o : list) {
                if (o instanceof Map<?, ?> part && "text".equals(part.get("type"))) {
                    Object text = part.get("text");
                    if (text != null) sb.append(text);
                }
            }
            return sb.toString();
        }
        return "";
    }
}
