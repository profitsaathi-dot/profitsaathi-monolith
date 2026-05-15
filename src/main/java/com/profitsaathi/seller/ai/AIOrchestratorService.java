package com.profitsaathi.seller.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Routes AI requests to OpenRouter (cheap default) and falls back to Gemini
 * when OpenRouter rate-limits us with HTTP 429.
 */
@Service
@RequiredArgsConstructor
public class AIOrchestratorService {

    private final GeminiAIService geminiService;
    private final OpenRouterAIService openRouterService;

    public String generateAIResponse(String prompt) {
        try {
            return openRouterService.generateSummary(prompt);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                return geminiService.generateSummary(prompt);
            }
            throw e;
        }
    }
}
