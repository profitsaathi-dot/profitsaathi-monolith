package com.profitsaathi.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        boolean hasBearer = header != null && header.startsWith("Bearer ");

        // Temporary diagnostic — every request to /api/v1/admin/** logs whether
        // a bearer token is present. Remove once the access_denied issue is
        // root-caused.
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/v1/admin")) {
            log.info("[admin-request] {} hasBearer={}", path, hasBearer);
        }

        if (hasBearer && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                if (!"access".equals(claims.get("type", String.class))) {
                    throw new JwtException("Wrong token type for resource access");
                }

                String role = claims.get("role", String.class);
                Long credentialsId = Long.valueOf(claims.getSubject());
                Number sidNum = claims.get("sid", Number.class);
                Long subjectId = sidNum == null ? null : sidNum.longValue();
                String email = claims.get("email", String.class);

                AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                        credentialsId, subjectId, email, role);

                String authority = "ROLE_" + role;
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(new SimpleGrantedAuthority(authority)));
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(auth);

                if (path != null && path.startsWith("/api/v1/admin")) {
                    log.info("[admin-request] {} principal email={} role={} authority={}",
                            path, email, role, authority);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"invalid_token\",\"message\":\""
                        + ex.getMessage().replace("\"", "'") + "\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
