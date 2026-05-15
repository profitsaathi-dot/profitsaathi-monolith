package com.profitsaathi.seller.aichat.imagegen;

import com.profitsaathi.seller.aichat.AiProvider;
import com.profitsaathi.seller.aichat.MissingApiKeyException;

/**
 * Provider-agnostic image-generation contract used by {@link ImageGenRouter}.
 * Distinct from chat ({@code ChatProvider}) because the wire format and the
 * underlying model class (diffusion vs LLM) are different.
 *
 * Implementations should throw:
 *   - {@link MissingApiKeyException} when {@code apiKey} is null/blank.
 *   - {@link IllegalArgumentException} when the provider rejects the call
 *     in a way the seller can fix (bad key, quota exceeded).
 *   - {@link IllegalStateException} for everything else, so the router
 *     knows it can try the next provider.
 */
public interface ImageGenProvider {

    AiProvider provider();

    /**
     * @param apiKey provider key (required — all current providers are keyed)
     * @param prompt the image description
     */
    ImageGenResult generate(String apiKey, String prompt);
}
