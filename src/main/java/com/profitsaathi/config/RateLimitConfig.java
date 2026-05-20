package com.profitsaathi.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Rate limiting configuration using Bucket4j with Redis backend.
 * 
 * Provides distributed rate limiting across multiple application instances.
 * Protects API endpoints from abuse and ensures fair resource allocation.
 */
@Slf4j
@Configuration
public class RateLimitConfig {

    @Value("${rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${rate-limit.requests-per-minute:100}")
    private long requestsPerMinute;

    @Value("${rate-limit.burst-capacity:20}")
    private long burstCapacity;

    @Value("${spring.data.redis.url:redis://localhost:6379}")
    private String redisUrl;

    /**
     * Creates a ProxyManager for distributed rate limiting using Redis.
     * This allows rate limits to work across multiple application instances.
     */


    @Bean
    @ConditionalOnProperty(
            prefix = "rate-limit",
            name = "enabled",
            havingValue = "true"
    )
    public ProxyManager<String> proxyManager() {
        // Ensure Redis URL has proper scheme
        String normalizedRedisUrl = redisUrl;
        if (!redisUrl.startsWith("redis://") && !redisUrl.startsWith("rediss://")) {
            normalizedRedisUrl = "redis://" + redisUrl;
            log.info("Normalized Redis URL from '{}' to '{}'", redisUrl, normalizedRedisUrl);
        }

        RedisClient redisClient = RedisClient.create(normalizedRedisUrl);

        StatefulRedisConnection<String, byte[]> connection =
                redisClient.connect(
                        RedisCodec.of(
                                StringCodec.UTF8,
                                ByteArrayCodec.INSTANCE
                        )
                );

        log.info("Rate limiting enabled");

        return LettuceBasedProxyManager
                .builderFor(connection)
                .build();
    }

    /**
     * Creates a bucket configuration supplier for rate limiting.
     * 
     * Configuration:
     * - Base rate: configurable requests per minute
     * - Burst capacity: allows short bursts above the base rate
     * - Refill: tokens refill gradually over time
     */
    @Bean
    public Supplier<BucketConfiguration> bucketConfiguration() {
        return () -> {
            // Base bandwidth: X requests per minute
            Bandwidth baseLimit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();

            // Burst bandwidth: allows short bursts
            Bandwidth burstLimit = Bandwidth.builder()
                .capacity(burstCapacity)
                .refillGreedy(burstCapacity, Duration.ofSeconds(10))
                .build();

            return BucketConfiguration.builder()
                .addLimit(baseLimit)
                .addLimit(burstLimit)
                .build();
        };
    }

    /**
     * Helper method to resolve bucket for a given key (e.g., IP address, user ID).
     */
    public Bucket resolveBucket(String key, ProxyManager<String> proxyManager, 
                                Supplier<BucketConfiguration> configSupplier) {
        if (proxyManager == null) {
            // Rate limiting disabled, return unlimited bucket
            return Bucket.builder()
                .addLimit(Bandwidth.simple(Long.MAX_VALUE, Duration.ofDays(1)))
                .build();
        }
        
        return proxyManager.builder().build(key, configSupplier);
    }
}
