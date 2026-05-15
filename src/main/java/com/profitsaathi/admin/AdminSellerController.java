package com.profitsaathi.admin;

import com.profitsaathi.seller.user.Seller;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * Admin-only seller management. Listing, lookup, and profile patches.
 * Creating a seller still goes through {@code POST /api/v1/auth/signup/seller}
 * because that endpoint provisions the credentials row + welcome mail in one
 * transaction — duplicating it here would just be a parallel implementation.
 */
@RestController
@RequestMapping("/api/v1/admin/sellers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSellerController {

    private final AdminSellerService adminSellerService;

    @GetMapping
    public List<Seller> list(@RequestParam(value = "status", required = false) Seller.Status status) {
        return adminSellerService.list(status);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(adminSellerService.get(id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<?> report(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(adminSellerService.report(id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody AdminSellerUpdateRequest body) {
        if (body.getName() == null && body.getStoreName() == null
                && body.getSellerType() == null && body.getMobile() == null
                && body.getStatus() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message",
                            "Provide at least one of name, storeName, sellerType, mobile, status"));
        }
        try {
            return ResponseEntity.ok(adminSellerService.update(id, body));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }
}
