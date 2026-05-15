package com.profitsaathi.ai.provider;

/**
 * Common contract every text LLM provider implements. The router picks one
 * by model-prefix; all of them produce a {@link LlmResult}.
 *
 * Implementations should be stateless and Spring-managed so the router can
 * inject them as a single bag.
 */
public interface LlmProvider {

    /** True when this provider claims a given model name (by prefix or exact match). */
    boolean supports(String model);

    /**
     * Run a single-turn chat. Throws on transport / API errors so the
     * router can surface a 502 with the upstream message.
     */
    LlmResult chat(String model, String prompt);
}
