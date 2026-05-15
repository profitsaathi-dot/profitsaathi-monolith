package com.profitsaathi.seller.aichat;

/**
 * The three LLM providers a seller can plug into. Stored as a string on
 * the {@code sellers.ai_primary_provider} column, never as the ordinal,
 * so reorder is safe.
 */
public enum AiProvider {

    GEMINI("Gemini"),
    OPENROUTER("OpenRouter"),
    NVIDIA("NVIDIA NIM");

    private final String label;

    AiProvider(String label) { this.label = label; }

    public String label() { return label; }

    /** Lenient parse for request bodies — null/empty/bad → null. */
    public static AiProvider fromString(String s) {
        if (s == null) return null;
        try { return AiProvider.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
