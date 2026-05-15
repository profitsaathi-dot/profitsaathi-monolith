package com.profitsaathi.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class AuthDtos {

    private AuthDtos() {}

    public record SellerSignupRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank String name,
            String storeName,
            String language
    ) {}

    public record CustomerSignupRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank String name,
            String language
    ) {}

    public record AdminSignupRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank String name,
            @NotBlank String registrationSecret
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    //oauth
    public record oauthLoginRequest(
            @NotBlank @Email String email
    ) {}

    public record PasskeyCredentialResponse(
            String credentialId,
            String publicKey,
            int counter
    ) {}

    public record PasskeyStatusResponse(
            boolean enabled,
            List<String> credentialIds
    ) {}

    public record PasskeyLoginRequest(
            @NotBlank String credentialId,
            Integer counter
    ) {}

    public record PasskeyRegisterRequest(
            @NotBlank String credentialId,
            @NotBlank String publicKey,
            Integer counter,
            String deviceType,
            Boolean backedUp,
            List<String> transports
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            String role,
            Long subjectId,
            String email
    ) {}
}
