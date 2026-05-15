package com.profitsaathi.seller.aichat;

import lombok.Getter;

/**
 * Raised by a {@link ChatProvider} when the seller hasn't configured a key
 * for that provider. The router uses this to skip to the next provider
 * silently rather than treating it as a real failure.
 */
@Getter
public class MissingApiKeyException extends RuntimeException {

    private final AiProvider provider;

    public MissingApiKeyException(AiProvider provider) {
        super("No API key configured for " + provider.label());
        this.provider = provider;
    }
}
