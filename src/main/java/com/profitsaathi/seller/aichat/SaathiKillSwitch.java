package com.profitsaathi.seller.aichat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Feature flag for the entire Saathi AI surface — chat, image generation,
 * image editing, and provider-management endpoints.
 *
 * Flip via the {@code ai.saathi.enabled} property (or
 * {@code AI_SAATHI_ENABLED} env var) without redeploying. When false, every
 * Saathi endpoint returns 503 with a clear "service paused" message — useful
 * during a provider outage, an unexpected cost spike, or scheduled maintenance.
 *
 * Default is {@code true} so dev environments behave as before.
 *
 * Usage: inject and call {@link #ensureEnabled()} as the first line of each
 * controller method. We don't use {@code @ConditionalOnProperty} on the
 * controller bean because that would require a Spring context restart to
 * toggle.
 */
@Slf4j
@Component
public class SaathiKillSwitch {

    @Value("${ai.saathi.enabled:true}")
    private boolean enabled;

    @PostConstruct
    void announce() {
        log.info("Saathi AI feature flag — enabled={} (toggle via ai.saathi.enabled or AI_SAATHI_ENABLED env)", enabled);
    }

    public boolean isEnabled() { return enabled; }

    /**
     * Throws {@link SaathiPausedException} (→ 503 in the controller) when
     * the feature is globally disabled. Call this at the top of every Saathi
     * controller method.
     */
    public void ensureEnabled() {
        if (!enabled) throw new SaathiPausedException();
    }

    public static class SaathiPausedException extends RuntimeException {
        public SaathiPausedException() {
            super("Saathi AI is temporarily paused by ProfitSaathi. Please try again later.");
        }
    }
}
