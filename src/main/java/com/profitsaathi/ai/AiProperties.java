package com.profitsaathi.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the AI module — provider URLs, API keys, defaults.
 *
 * Bound from {@code ai.*} properties. Keep all secrets here so a future
 * extraction into a standalone AI service only needs to copy this bean.
 *
 * Empty keys are tolerated — providers without credentials fail at call
 * time with a clear error rather than blocking app startup. That keeps the
 * monolith bootable in dev even when third-party keys aren't set.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String openaiKey = "";
    private String openrouterKey = "";
    private String geminiKey = "";

    private String ollamaUrl = "http://localhost:11434";
    private String openrouterUrl = "https://openrouter.ai/api/v1";
    private String openaiUrl = "https://api.openai.com/v1";
    private String geminiUrl = "https://generativelanguage.googleapis.com/v1beta";

    /** Forwarded to OpenRouter as HTTP-Referer / X-OpenRouter-Title. */
    private String siteUrl = "http://localhost:8084";
    private String siteName = "ProfitSaathi";

    /** Default model when the request omits one. */
    private String defaultModel = "openai/gpt-oss-120b:free";

    /** Default vision model used by /verify-payment. */
    private String visionModel = "openrouter/free";

    /** Cache TTL for chat completions (text only — image responses bypass). */
    private long cacheTtlSeconds = 3600;

    /** Hard request timeout per provider call. */
    private long requestTimeoutSeconds = 60;

    /**
     * Model used to generate Growth Adviser cards for sellers without an
     * active paid subscription. OpenRouter's free tier is the default.
     */
    private String growthCardFreeModel = "openai/gpt-oss-120b:free";

    /**
     * Model used to generate Growth Adviser cards for sellers on a paid
     * plan. A stronger reasoning model produces noticeably sharper
     * recommendations from the same seller context.
     */
    private String growthCardPremiumModel = "anthropic/claude-3.5-sonnet";

    /**
     * Manual-sync cooldown for Free sellers, in hours. Free tier is
     * limited to one sync per day per the product spec — bumping this
     * affects both the cooldown gate and the daily scheduler's "due"
     * cutoff (anyone last-synced more than this long ago is eligible).
     */
    private int growthCardCooldownHoursFree = 24;

    /**
     * Manual-sync cooldown for Premium sellers, in hours. 6h ≈ 4 syncs/day.
     */
    private int growthCardCooldownHoursPremium = 6;
}
