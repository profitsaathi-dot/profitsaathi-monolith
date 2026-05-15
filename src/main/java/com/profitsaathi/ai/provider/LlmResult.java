package com.profitsaathi.ai.provider;

/**
 * Common shape every text LLM provider returns. tokens is best-effort —
 * providers that don't expose usage data return 0 (which CostTracker
 * silently treats as a free request rather than throwing).
 */
public record LlmResult(String response, long tokens) {}
