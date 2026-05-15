package com.profitsaathi.seller.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenRouterAIService {

    @Value("${ai.openrouter.api-key:}")
    private String apiKey;

    @Value("${ai.openrouter.site-url:}")
    private String siteUrl;

    @Value("${ai.openrouter.site-name:ProfitSaathi}")
    private String siteName;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

    public String generateSummary(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.add("HTTP-Referer", siteUrl);
        headers.add("X-OpenRouter-Title", siteName);

        Map<String, Object> message = Map.of("role", "user", "content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "openai/gpt-4o-mini");
        body.put("messages", List.of(message));
        body.put("max_tokens", 200);
        body.put("temperature", 0.7);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(OPENROUTER_URL, request, Map.class);

        Map responseBody = response.getBody();
        if (responseBody == null) throw new RuntimeException("Empty response from OpenRouter");

        List choices = (List) responseBody.get("choices");
        Map firstChoice = (Map) choices.get(0);
        Map messageMap = (Map) firstChoice.get("message");
        return messageMap.get("content").toString();
    }
}
