package com.profitsaathi.config;

import com.profitsaathi.auth.JwtAuthenticationFilter;
import com.profitsaathi.auth.JwtProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Value("${allowed.origins:*}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter) throws Exception {
        http
                .csrf(c -> c.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public auth endpoints
                        .requestMatchers("/api/v1/auth/signup/**",
                                         "/api/v1/auth/login",
                                         "/api/v1/auth/passkeys/login",
                                         "/api/v1/auth/oauth/google",
                                         "/api/v1/auth/refresh",
                                         "/api/v1/auth/forgot-password",
                                         "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/passkeys/credential/*").permitAll()

                        // Public Store endpoints (used get store details in user)
                        .requestMatchers("/api/v1/store/**",
                                         "/api/v1/store/info/**",
                                "/api/v1/store//public/**",
                                "/api/v1/store/payment-qr/**").permitAll()


                        .requestMatchers("/api/v1/dynamic-prices/**",
                                "/api/v1/dynamic-prices/mine/**").permitAll()

                        // Public Store endpoints (used pre-login for signup verification)
                        .requestMatchers("/api/v1/otp/send/**",
                                "/api/v1/otp/verify/**").permitAll()

                        // Public storefront browsing (used by buyers without login)
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/offers/product/**").permitAll()

                        // Public order tracking (token-protected at controller level)
                        .requestMatchers("/api/v1/order/track/**",
                                         "/api/v1/order/track-by-no/**").permitAll()

                        // Public review endpoints (anyone can submit/view reviews)
                        .requestMatchers(HttpMethod.POST, "/api/v1/reviews/order/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/reviews/order/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/reviews/product/**").permitAll()

                        // Public order creation — buyers placing orders from a
                        // seller's storefront (purchaseType=DIRECT) bypass auth
                        // entirely; the seller is resolved from the product
                        // owner inside OrderService.resolveOrderSeller. When a
                        // logged-in seller places an order from their dashboard
                        // they send a JWT on the same endpoint and the service
                        // resolves them via me.subjectId. Both /order and
                        // /order/owner accept either mode. Matches the old
                        // monolith's `POST /api/v1/order/**` permitAll rule.
                        .requestMatchers(HttpMethod.POST, "/api/v1/order/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/order").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/order/public/**").permitAll()

                        // Public payment verify + manual proof upload
                        .requestMatchers("/api/v1/payment/verify",
                                         "/api/v1/payment/upload").permitAll()

                        // WAHA inbound webhook
                        .requestMatchers("/api/v1/whatsapp/webhook").permitAll()
                        // OPEN WA
                        .requestMatchers("/api/v1/whatsapp/open/webhook").permitAll()

                        // Health/metrics
                        .requestMatchers("/actuator/health/**",
                                         "/actuator/info",
                                         "/actuator/prometheus").permitAll()

                        // OpenAPI / Swagger UI — keep public so the admin
                        // app's API explorer can load the spec without
                        // forcing a CORS-laden auth round-trip.
                        .requestMatchers("/v3/api-docs/**",
                                         "/swagger-ui/**",
                                         "/swagger-ui.html").permitAll()

                        // Admin surface — explicit URL-level role gate so
                        // role failures show up at the filter chain rather
                        // than depending on every controller remembering its
                        // @PreAuthorize. Class-level annotations stay as
                        // documentation + a second line of defence.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // AI surface — admin-only views explicit at URL level.
                        // /api/v1/ai/chat + /verify-payment fall through to
                        // .anyRequest().authenticated() (any signed-in user).
                        .requestMatchers("/api/v1/ai/admin/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (origins.isEmpty() || origins.contains("*")) {
            cfg.addAllowedOriginPattern("*");
        } else {
            cfg.setAllowedOrigins(origins);
        }
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.setExposedHeaders(List.of("Authorization", "Content-Disposition"));

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
