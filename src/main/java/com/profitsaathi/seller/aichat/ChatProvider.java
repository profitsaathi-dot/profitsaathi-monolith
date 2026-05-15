package com.profitsaathi.seller.aichat;

import java.util.List;

/**
 * Provider-agnostic chat contract used by {@link AiChatProviderRouter}.
 * Each implementation knows how to call a single LLM API (Gemini, OpenRouter,
 * NVIDIA NIM, …) with the seller's own API key.
 *
 * Implementations should throw:
 *   - {@link MissingApiKeyException} when {@code apiKey} is null/blank.
 *   - {@link IllegalArgumentException} when the provider rejects the call
 *     in a way the seller can fix (bad key, quota exceeded on their account).
 *   - {@link IllegalStateException} for everything else (5xx, network, etc.)
 *     so the router knows it can try the next provider.
 */
public interface ChatProvider {

    /** Stable identifier used to map seller preferences and stored keys. */
    AiProvider provider();

    /** Human-readable label for logs / error messages. */
    default String label() { return provider().label(); }

    /**
     * @param apiKey   seller's key for this provider
     * @param language ISO 639-1 of the seller's preferred reply language
     * @param history  prior turns, oldest first
     * @param current  the new user turn (text + optional image)
     */
    String chat(String apiKey, String language, List<ChatTurn> history, ChatTurn current);
}
