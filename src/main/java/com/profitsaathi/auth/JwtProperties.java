package com.profitsaathi.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {
    /** Symmetric HS256 key — must be at least 32 bytes. */
    private String secret;
    private long accessTtlMinutes = 60;
    private long refreshTtlDays = 14;
    private String issuer = "profitsaathi";
}
