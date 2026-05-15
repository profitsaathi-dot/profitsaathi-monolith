package com.profitsaathi.seller.aichat;

/**
 * Raised when the seller hasn't configured an API key for ANY provider.
 * The controller maps this to 412 Precondition Failed so the UI can show
 * a "configure your AI keys in Settings" empty state.
 */
public class NoAiProviderConfiguredException extends RuntimeException {

    public NoAiProviderConfiguredException() {
        super("No AI provider configured. Add at least one API key (Gemini, OpenRouter or NVIDIA NIM) in Settings.");
    }
}
