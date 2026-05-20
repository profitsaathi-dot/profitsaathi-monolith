package com.profitsaathi.config;


import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;

import java.time.Duration;

/**
 * Redis configuration with connection pooling and proper connection handling.
 * 
 * Fixes:
 * - Uses SPRING_REDIS_URL environment variable correctly
 * - Parses redis:// URLs properly
 * - Configures connection timeouts
 * - Enables SSL for production
 * - Adds connection pooling for better performance
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.url:redis://localhost:6379}")
    private String redisUrl;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${spring.data.redis.timeout:6000}")
    private long timeout;
    
    @Value("${spring.data.redis.pool.max-active:20}")
    private int poolMaxActive;
    
    @Value("${spring.data.redis.pool.max-idle:10}")
    private int poolMaxIdle;
    
    @Value("${spring.data.redis.pool.min-idle:5}")
    private int poolMinIdle;

    /**
     * Client resources for connection pooling.
     * Shared across all connections for efficiency.
     */
    @Bean(destroyMethod = "shutdown")
    public ClientResources clientResources() {
        return DefaultClientResources.create();
    }

    /**
     * Redis connection factory with connection pooling.
     * Supports both redis:// and rediss:// (SSL) URLs.
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory(ClientResources clientResources) {
        log.info("Configuring Redis connection: url={}, ssl={}, poolMaxActive={}", 
                redisUrl, sslEnabled, poolMaxActive);

        // Parse Redis URL
        RedisStandaloneConfiguration config = parseRedisUrl(redisUrl);

        // Configure connection pool
        @SuppressWarnings("rawtypes")
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxTotal(poolMaxActive);
        poolConfig.setMaxIdle(poolMaxIdle);
        poolConfig.setMinIdle(poolMinIdle);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);

        // Configure Lettuce client with timeouts and pooling
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofMillis(timeout))
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .build();

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfig =
                LettucePoolingClientConfiguration.builder()
                        .commandTimeout(Duration.ofMillis(timeout))
                        .poolConfig(poolConfig)
                        .clientResources(clientResources)
                        .clientOptions(clientOptions);

        // Enable SSL if configured
        if (sslEnabled || redisUrl.startsWith("rediss://")) {
            clientConfig.useSsl();
            log.info("Redis SSL enabled");
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig.build());
        
        // Test connection on startup
        try {
            factory.afterPropertiesSet();
            factory.getConnection().ping();
            log.info("Redis connection successful with connection pooling");
        } catch (Exception e) {
            log.error("Redis connection failed: {}", e.getMessage());
            log.warn("Application will continue without Redis caching");
        }

        return factory;
    }

    /**
     * Parse Redis URL in format: redis://[password@]host:port[/database]
     */
    private RedisStandaloneConfiguration parseRedisUrl(String url) {
        try {
            // Remove protocol
            String cleanUrl = url.replaceFirst("^rediss?://", "");

            String password = null;
            String hostPort = cleanUrl;
            int database = 0;

            // Extract password if present
            if (cleanUrl.contains("@")) {
                String[] parts = cleanUrl.split("@", 2);
                password = parts[0];
                hostPort = parts[1];
            }

            // Extract database if present
            if (hostPort.contains("/")) {
                String[] parts = hostPort.split("/", 2);
                hostPort = parts[0];
                database = Integer.parseInt(parts[1]);
            }

            // Extract host and port
            String[] hostPortParts = hostPort.split(":", 2);
            String host = hostPortParts[0];
            int port = hostPortParts.length > 1 ? Integer.parseInt(hostPortParts[1]) : 6379;

            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
            config.setDatabase(database);
            if (password != null && !password.isEmpty()) {
                config.setPassword(password);
            }

            log.info("Parsed Redis config: host={}, port={}, database={}, hasPassword={}",
                    host, port, database, password != null);

            return config;
        } catch (Exception e) {
            log.error("Failed to parse Redis URL: {}", e.getMessage());
            // Fallback to localhost
            return new RedisStandaloneConfiguration("localhost", 6379);
        }
    }

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(mapper);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(serializer)
                );
    }

    /**
     * RedisTemplate for manual cache operations
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        
        template.afterPropertiesSet();
        return template;
    }
}
