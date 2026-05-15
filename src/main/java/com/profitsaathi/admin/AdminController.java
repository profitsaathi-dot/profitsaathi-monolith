package com.profitsaathi.admin;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

/**
 * Admin account-management endpoints. Identity always comes from the JWT
 * principal's {@code subjectId} — never from the request body. Mirrors
 * {@link com.profitsaathi.seller.user.SellerController} for symmetry.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminRepository adminRepository;
    private final AdminService adminService;

    @GetMapping("/me")
    public ResponseEntity<Admin> me(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        return ResponseEntity.of(adminRepository.findById(me.subjectId()));
    }

    @PatchMapping("/preferences")
    public ResponseEntity<?> preferences(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                         @Valid @RequestBody AdminPreferencesRequest body) {
        if (body.getTheme() == null && body.getAccent() == null && body.getRegion() == null
                && body.getNotifyEmail() == null && body.getNotifyWhatsapp() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message",
                            "Provide at least one of theme, accent, region, notifyEmail, notifyWhatsapp"));
        }
        try {
            return ResponseEntity.ok(adminService.updatePreferences(me.subjectId(), body));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }
}
