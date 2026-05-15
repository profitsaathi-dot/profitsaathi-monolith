package com.profitsaathi.seller.ai;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeminiAIService {

    private final Client client;

    public String generateSummary(String prompt) {
        GenerateContentResponse response = client.models.generateContent(
                "gemini-3-flash-preview",
                prompt,
                null);
        return response.text();
    }
}
