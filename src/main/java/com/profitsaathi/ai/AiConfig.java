package com.profitsaathi.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Single registration point for the AI module's configuration properties.
 * Keeping {@link AiProperties} bound here (rather than via individual
 * {@code @EnableConfigurationProperties} on each provider) makes the
 * module easier to extract later — the moment we lift this package into
 * its own service, the binding moves with it.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {
}
