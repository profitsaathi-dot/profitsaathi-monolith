package com.profitsaathi.seller.aichat;

import lombok.Getter;

import java.util.List;

/**
 * Every configured provider was tried and every one failed. The controller
 * maps this to 502 Bad Gateway so the UI can show a "try again" message —
 * the {@link #getFailures()} list is included in the body so the seller
 * can see which providers said what (e.g. "Gemini: bad key").
 */
@Getter
public class AllAiProvidersFailedException extends RuntimeException {

    private final List<String> failures;

    public AllAiProvidersFailedException(List<String> failures) {
        super("All configured AI providers failed: " + String.join("; ", failures));
        this.failures = List.copyOf(failures);
    }
}
