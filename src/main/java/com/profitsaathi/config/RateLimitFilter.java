package com.profitsaathi.config;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Rate limiting filter that applies to all API requests.
 * 
 * Uses Bucket4j with Redis backend for distributed rate limiting.
 * Rate limits are applied per IP address.
 * 
 * Response headers:
 * - X-Rate-Limit-Remaining: Number of requests remaining
 * - X-Rate-Limit-Retry-After-Seconds: Seconds to wait before retrying (on 429)
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    @Autowired(required = false)
    private ProxyManager<String> proxyManager;
    private final Supplier<BucketConfiguration> bucketConfiguration;
    private final RateLimitConfig rateLimitConfig;

    @Value("${rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        // Skip rate limiting if disabled
        if (!rateLimitEnabled || proxyManager == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip rate limiting for health checks and actuator endpoints
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.equals("/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get client identifier (IP address)
        String clientId = getClientId(request);
        String bucketKey = "rate_limit:" + clientId;

        try {
            // Resolve bucket for this client
            Bucket bucket = rateLimitConfig.resolveBucket(bucketKey, proxyManager, bucketConfiguration);

            // Try to consume 1 token
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            if (probe.isConsumed()) {
                // Request allowed
                response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
                filterChain.doFilter(request, response);
            } else {
                // Rate limit exceeded
                long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
                
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
                response.setContentType("application/json");
                response.getWriter().write(String.format(
                    "{\"error\":\"Rate limit exceeded\",\"message\":\"Too many requests. Please try again in %d seconds.\",\"retryAfter\":%d}",
                    waitForRefill, waitForRefill
                ));

                log.warn("Rate limit exceeded for client: {} on path: {}", clientId, path);
            }
        } catch (Exception e) {
            // If rate limiting fails, allow the request (fail open)
            log.error("Rate limiting error for client {}: {}", clientId, e.getMessage());
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Get client identifier from request.
     * Checks X-Forwarded-For header first (for proxied requests), then falls back to remote address.
     */
    private String getClientId(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
