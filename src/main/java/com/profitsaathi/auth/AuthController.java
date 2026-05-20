package com.profitsaathi.auth;

import com.profitsaathi.auth.AuthDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup/seller")
    public ResponseEntity<TokenResponse> signupSeller(@Valid @RequestBody SellerSignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signupSeller(req));
    }

    @PostMapping("/signup/customer")
    public ResponseEntity<TokenResponse> signupCustomer(@Valid @RequestBody CustomerSignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signupCustomer(req));
    }

    @PostMapping("/signup/admin")
    public ResponseEntity<TokenResponse> signupAdmin(@Valid @RequestBody AdminSignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signupAdmin(req));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }



    @PostMapping("/oauth/google")
    public TokenResponse loginoauth(@Valid @RequestBody oauthLoginRequest req1) {
        return authService.oauthogin(req1);
    }

    @GetMapping("/passkeys/me")
    public PasskeyStatusResponse myPasskeys(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        return authService.getMyPasskeys(me);
    }

    @GetMapping("/passkeys/credential/{credentialId}")
    public PasskeyCredentialResponse passkeyCredential(@PathVariable String credentialId) {
        return authService.getPasskeyCredential(credentialId);
    }

    @PostMapping("/passkeys/register")
    public PasskeyStatusResponse registerPasskey(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                                 @Valid @RequestBody PasskeyRegisterRequest req) {
        return authService.registerPasskey(me, req);
    }

    @PostMapping("/passkeys/login")
    public TokenResponse loginWithPasskey(@Valid @RequestBody PasskeyLoginRequest req) {
        return authService.loginWithPasskey(req);
    }


    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        if (me != null) authService.logout(me.credentialsId());
    }

    @GetMapping("/me")
    public AuthenticatedPrincipal me(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        return me;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthDtos.ApiResponse> forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest req) {
        return ResponseEntity.ok(authService.forgotPassword(req));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthDtos.ApiResponse> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest req) {
        return ResponseEntity.ok(authService.resetPassword(req));
    }
}
