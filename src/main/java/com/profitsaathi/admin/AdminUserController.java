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
import java.util.List;
import java.util.Objects;

/**
 * Admin-only endpoints for managing other admins. Listing, lookup, and
 * profile patches. Creation goes through {@code POST /api/v1/auth/signup/admin}
 * which already enforces the registration secret.
 *
 * Self-modification guard: an admin can't change their own status, to
 * avoid accidentally locking themselves out of the console.
 */
@RestController
@RequestMapping("/api/v1/admin/admins")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public List<Admin> list(@RequestParam(value = "status", required = false) Admin.Status status) {
        return adminUserService.list(status);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(adminUserService.get(id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody AdminUserUpdateRequest body,
                                    @AuthenticationPrincipal AuthenticatedPrincipal me) {
        if (body.getName() == null && body.getStatus() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message",
                            "Provide at least one of name, status"));
        }
        if (body.getStatus() != null && me != null && Objects.equals(me.subjectId(), id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("message",
                            "You can't change your own status — ask another admin."));
        }
        try {
            return ResponseEntity.ok(adminUserService.update(id, body));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }
}
