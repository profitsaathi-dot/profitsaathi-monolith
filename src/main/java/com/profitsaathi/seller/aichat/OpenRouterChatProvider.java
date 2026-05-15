package com.profitsaathi.seller.aichat;

import com.profitsaathi.ai.AiProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenRouter chat provider. Uses one of OpenRouter's free vision-capable
 * models so the same path works for text-only and image+text turns.
 * The seller can override the model later via app config without touching
 * code, but for now we hardcode a sensible free default.
 */
@Component
public class OpenRouterChatProvider extends OpenAiCompatibleChatClient {

    /**
     * Llama 3.2 11B Vision is OpenRouter's free workhorse for image+text
     * — small enough to be free, large enough to give useful captions /
     * hashtag suggestions for product photos.
     */
    private static final String MODEL = "openrouter/free";

    public OpenRouterChatProvider(WebClient webClient, AiProperties props) {
        super(webClient, props);
    }

    @Override
    public AiProvider provider() { return AiProvider.OPENROUTER; }

    @Override
    protected String chatCompletionsUrl() {
        return props.getOpenrouterUrl().replaceAll("/+$", "") + "/chat/completions";
    }

    @Override
    protected String model() { return MODEL; }

    @Override
    protected void customHeaders(Consumer<Map.Entry<String, String>> sink) {
        // OpenRouter requires HTTP-Referer / X-Title for analytics. They're
        // not auth — using app-level values is fine even when the API key
        // is per-seller.
        sink.accept(Map.entry("HTTP-Referer", props.getSiteUrl()));
        sink.accept(Map.entry("X-Title", props.getSiteName()));
    }
}
