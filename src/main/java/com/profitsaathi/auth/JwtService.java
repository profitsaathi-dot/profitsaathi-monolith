package com.profitsaathi.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        byte[] secretBytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "security.jwt.secret must be at least 32 bytes for HS256; got " + secretBytes.length);
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public Tokens issue(Credentials c) {
        String refreshTokenId = UUID.randomUUID().toString();
        return new Tokens(
                buildAccess(c),
                buildRefresh(c, refreshTokenId),
                refreshTokenId,
                props.getAccessTtlMinutes() * 60);
    }

    public String buildAccess(Credentials c) {
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofMinutes(props.getAccessTtlMinutes()));
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(String.valueOf(c.getId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(Map.of(
                        "role", c.getRole().name(),
                        "email", c.getEmail(),
                        "sid", c.getSubjectId() == null ? -1L : c.getSubjectId(),
                        "type", "access"
                ))
                .signWith(key)
                .compact();
    }

    public String buildRefresh(Credentials c, String refreshTokenId) {
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofDays(props.getRefreshTtlDays()));
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(String.valueOf(c.getId()))
                .id(refreshTokenId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(Map.of(
                        "role", c.getRole().name(),
                        "type", "refresh"
                ))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public record Tokens(String accessToken, String refreshToken, String refreshTokenId, long accessTtlSeconds) {}
}
