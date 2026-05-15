package com.profitsaathi.auth;

import com.profitsaathi.admin.Admin;
import com.profitsaathi.admin.AdminRepository;
import com.profitsaathi.auth.AuthDtos.*;
import com.profitsaathi.customer.user.Customer;
import com.profitsaathi.customer.user.CustomerRepository;
import com.profitsaathi.notification.email.WelcomeMailService;
import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final CredentialsRepository credentialsRepo;
    private final PasskeysRepository passkeysRepo;
    private final SellerRepository sellerRepo;
    private final CustomerRepository customerRepo;
    private final AdminRepository adminRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final WelcomeMailService welcomeMailService;

    /** Shared secret required to register an ADMIN. Empty value disables the endpoint. */
    @Value("${security.admin.signup-secret:}")
    private String adminSignupSecret;

    @Transactional
    public TokenResponse signupSeller(SellerSignupRequest req) {
        String email = req.email().trim().toLowerCase();
        if (credentialsRepo.existsByEmail(email)) {
            throw new IllegalStateException("Email already registered");
        }

        Seller seller = Seller.builder()
                .name(req.name())
                .email(email)
                .storeName(req.storeName())
                .language(req.language())
                .status(Seller.Status.ACTIVE)
                .build();
        seller = sellerRepo.save(seller);

        Credentials c = Credentials.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Credentials.Role.SELLER)
                .subjectId(seller.getId())
                .enabled(true)
                .build();
        c = credentialsRepo.save(c);

        welcomeMailService.sendWelcomeSeller(seller);
        return buildTokenResponse(c);
    }

    @Transactional
    public TokenResponse signupCustomer(CustomerSignupRequest req) {
        String email = req.email().trim().toLowerCase();
        if (credentialsRepo.existsByEmail(email)) {
            throw new IllegalStateException("Email already registered");
        }

        Customer customer = Customer.builder()
                .name(req.name())
                .email(email)
                .language(req.language())
                .notifications(true)
                .status(Customer.Status.ACTIVE)
                .build();
        customer = customerRepo.save(customer);

        Credentials c = Credentials.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Credentials.Role.CUSTOMER)
                .subjectId(customer.getId())
                .enabled(true)
                .build();
        c = credentialsRepo.save(c);

        welcomeMailService.sendWelcomeCustomer(customer);
        return buildTokenResponse(c);
    }

    @Transactional
    public TokenResponse signupAdmin(AdminSignupRequest req) {
        if (adminSignupSecret == null || adminSignupSecret.isBlank()) {
            throw new IllegalStateException("Admin signup is disabled");
        }
        if (!adminSignupSecret.equals(req.registrationSecret())) {
            throw new IllegalArgumentException("Invalid admin registration secret");
        }
        String email = req.email().trim().toLowerCase();
        if (credentialsRepo.existsByEmail(email)) {
            throw new IllegalStateException("Email already registered");
        }

        Admin admin = Admin.builder()
                .name(req.name())
                .email(email)
                .status(Admin.Status.ACTIVE)
                .build();
        admin = adminRepo.save(admin);

        Credentials c = Credentials.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Credentials.Role.ADMIN)
                .subjectId(admin.getId())
                .enabled(true)
                .build();
        c = credentialsRepo.save(c);

        return buildTokenResponse(c);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        Credentials c = credentialsRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!c.isEnabled()) {
            throw new IllegalStateException("Account disabled");
        }
        if (!passwordEncoder.matches(req.password(), c.getPasswordHash())) {
            log.warn("Failed login for {}", email);
            throw new IllegalArgumentException("Invalid credentials");
        }
        return buildTokenResponse(c);
    }

    @Transactional
    public TokenResponse oauthogin(oauthLoginRequest req) {
        String email = req.email().trim().toLowerCase();
        Credentials c = credentialsRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!c.isEnabled()) {
            throw new IllegalStateException("Account disabled");
        }

        return buildTokenResponse(c);
    }

    @Transactional
    public PasskeyStatusResponse getMyPasskeys(AuthenticatedPrincipal me) {
        if (me == null || !me.isSeller()) {
            throw new IllegalStateException("Passkeys are only available for seller accounts");
        }

        List<String> credentialIds = passkeysRepo.findAllByUserId(me.subjectId()).stream()
                .map(Passkeys::getCredentialId)
                .filter(Objects::nonNull)
                .toList();

        return new PasskeyStatusResponse(!credentialIds.isEmpty(), credentialIds);
    }

    @Transactional
    public PasskeyCredentialResponse getPasskeyCredential(String credentialId) {
        Passkeys passkey = passkeysRepo.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Passkey not found"));

        return new PasskeyCredentialResponse(
                passkey.getCredentialId(),
                passkey.getPublicKey(),
                passkey.getCounter());
    }

    @Transactional
    public PasskeyStatusResponse registerPasskey(AuthenticatedPrincipal me, PasskeyRegisterRequest req) {
        if (me == null || !me.isSeller()) {
            throw new IllegalStateException("Passkeys are only available for seller accounts");
        }

        String credentialId = req.credentialId().trim();
        String publicKey = req.publicKey().trim();
        Passkeys passkey = passkeysRepo.findByCredentialId(credentialId)
                .map(existing -> {
                    if (!Objects.equals(existing.getUserId(), me.subjectId())) {
                        throw new IllegalStateException("Passkey already belongs to another account");
                    }
                    return existing;
                })
                .orElseGet(() -> Passkeys.builder()
                        .userId(me.subjectId())
                        .credentialId(credentialId)
                        .build());

        passkey.setUserId(me.subjectId());
        passkey.setCredentialId(credentialId);
        passkey.setPublicKey(publicKey);
        passkey.setCounter(req.counter() == null ? 0 : req.counter());
        passkey.setDeviceType(req.deviceType());
        passkey.setBackUp(Boolean.TRUE.equals(req.backedUp()));
        passkey.setTransports(req.transports() == null || req.transports().isEmpty()
                ? null
                : String.join(",", req.transports()));
        passkeysRepo.save(passkey);

        return getMyPasskeys(me);
    }

    @Transactional
    public TokenResponse loginWithPasskey(PasskeyLoginRequest req) {
        String credentialId = req.credentialId().trim();
        Passkeys passkey = passkeysRepo.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Passkey not found"));

        Credentials c = credentialsRepo.findByRoleAndSubjectId(Credentials.Role.SELLER, passkey.getUserId())
                .orElseThrow(() -> new IllegalStateException("Passkey is not linked to a seller account"));
        if (!c.isEnabled()) {
            throw new IllegalStateException("Account disabled");
        }

        Integer newCounter = req.counter();
        if (newCounter != null && newCounter > passkey.getCounter()) {
            passkey.setCounter(newCounter);
            passkeysRepo.save(passkey);
        }

        return buildTokenResponse(c);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest req) {
        Claims claims;
        try {
            claims = jwtService.parse(req.refreshToken());
        } catch (JwtException ex) {
            throw new IllegalArgumentException("Invalid refresh token: " + ex.getMessage());
        }
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Not a refresh token");
        }
        Long credentialsId = Long.valueOf(claims.getSubject());
        Credentials c = credentialsRepo.findById(credentialsId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown subject"));
        if (!c.isEnabled()) {
            throw new IllegalStateException("Account disabled");
        }
        // Refresh-token rotation: validate the JTI matches the one we last issued.
        String jti = claims.getId();
        if (c.getRefreshTokenId() != null && !Objects.equals(c.getRefreshTokenId(), jti)) {
            throw new IllegalArgumentException("Refresh token has been rotated");
        }
        return buildTokenResponse(c);
    }

    @Transactional
    public void logout(Long credentialsId) {
        credentialsRepo.findById(credentialsId).ifPresent(c -> {
            c.setRefreshTokenId(null);
            credentialsRepo.save(c);
        });
    }

    private TokenResponse buildTokenResponse(Credentials c) {
        JwtService.Tokens t = jwtService.issue(c);
        c.setRefreshTokenId(t.refreshTokenId());
        credentialsRepo.save(c);
        return new TokenResponse(
                t.accessToken(),
                t.refreshToken(),
                "Bearer",
                t.accessTtlSeconds(),
                c.getRole().name(),
                c.getSubjectId(),
                c.getEmail());
    }
}
