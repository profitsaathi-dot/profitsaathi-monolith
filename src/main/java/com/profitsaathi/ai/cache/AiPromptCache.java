package com.profitsaathi.ai.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitsaathi.ai.AiChatResponse;
import com.profitsaathi.ai.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed cache for text chat completions. Image flows must bypass
 * this cache — each image is a distinct payload and the verification
 * pipeline is non-idempotent.
 *
 * Keys are namespaced as {@code ai:prompt:{md5-of-prompt}} to avoid
 * colliding with other cache namespaces and to keep keys bounded in size.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiPromptCache {

    private static final String NAMESPACE = "ai:prompt:";

    private final StringRedisTemplate redis;
    private final AiProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public Optional<AiChatResponse> get(String model, String prompt) {
        String json;
        try {
            json = redis.opsForValue().get(key(model, prompt));
        } catch (Exception e) {
            log.warn("Redis get failed: {}", e.getMessage());
            return Optional.empty();
        }
        if (json == null) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(json, AiChatResponse.class));
        } catch (Exception e) {
            log.warn("Cached payload was unparseable, evicting: {}", e.getMessage());
            redis.delete(key(model, prompt));
            return Optional.empty();
        }
    }

    public void put(String model, String prompt, AiChatResponse value) {
        try {
            redis.opsForValue().set(
                    key(model, prompt),
                    mapper.writeValueAsString(value),
                    Duration.ofSeconds(props.getCacheTtlSeconds()));
        } catch (Exception e) {
            // Cache failures are best-effort — never fail the request because of them.
            log.warn("Redis put failed: {}", e.getMessage());
        }
    }

    /** Hash both model + prompt so the same prompt across models doesn't collide. */
    private static String key(String model, String prompt) {
        int h = ((model == null ? 0 : model.hashCode()) * 31) ^ (prompt == null ? 0 : prompt.hashCode());
        return NAMESPACE + Integer.toHexString(h);
    }
}
