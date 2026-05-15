package com.profitsaathi.seller.ai;

import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleGenAIConfig {

    @Value("${ai.google.api-key:}")
    private String apiKey;

    @Bean
    public Client googleGenAIClient() {
        return Client.builder()
                .apiKey(apiKey)
                .build();
    }
}
