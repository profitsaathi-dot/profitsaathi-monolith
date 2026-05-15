package com.profitsaathi.seller.aichat;

import com.profitsaathi.ai.AiProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * NVIDIA NIM chat provider. NIM speaks the OpenAI {@code /v1/chat/completions}
 * dialect with a different base URL; the seller signs up at
 * <a href="https://build.nvidia.com/">build.nvidia.com</a> for free credits
 * and pastes the {@code nvapi-...} key into Settings.
 */
@Component
public class NvidiaChatProvider extends OpenAiCompatibleChatClient {

    private static final String BASE_URL = "https://integrate.api.nvidia.com/v1";

    /**
     * Llama 4 Maverick — Meta's flagship multimodal MoE model. Strong at
     * captions / hashtags / Reel-song suggestions and accepts image_url
     * parts via the OpenAI-compatible chat endpoint. The other vision-capable
     * choice on NIM is Scout (smaller, faster, lower quota); the text-only
     * models — Llama 3.3 70B, Qwen 2.5 Coder, Gemma 4 — won't see uploaded
     * product photos and would silently drop the image part.
     */
    private static final String MODEL = "meta/llama-4-maverick-17b-128e-instruct";

    public NvidiaChatProvider(WebClient webClient, AiProperties props) {
        super(webClient, props);
    }

    @Override
    public AiProvider provider() { return AiProvider.NVIDIA; }

    @Override
    protected String chatCompletionsUrl() { return BASE_URL + "/chat/completions"; }

    @Override
    protected String model() { return MODEL; }
}
