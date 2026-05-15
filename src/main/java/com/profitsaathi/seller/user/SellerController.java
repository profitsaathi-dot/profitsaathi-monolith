package com.profitsaathi.seller.user;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import com.profitsaathi.seller.store.OnboardRequest;
import com.profitsaathi.seller.store.PaymentSettingsRequest;
import com.profitsaathi.seller.store.PreferencesRequest;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Map;

/**
 * Seller account-management endpoints. Replaces the old Keycloak-coupled
 * {@code UserController} from the seller-app — all identity comes from the
 * JWT principal, never from the request body.
 */
@RestController
@RequestMapping("/api/v1/seller")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerController {

    private final SellerRepository sellerRepository;
    private final SellerService sellerService;

    @GetMapping("/me")
    public ResponseEntity<Seller> me(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        return ResponseEntity.of(sellerRepository.findById(me.subjectId()));
    }

    @PatchMapping("/onboard")
    public ResponseEntity<?> onboard(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                     @Valid @RequestBody OnboardRequest body) {
        try {
            return ResponseEntity.ok(sellerService.onboard(me.subjectId(), body));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @PatchMapping("/preferences")
    public ResponseEntity<?> preferences(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                         @Valid @RequestBody PreferencesRequest body) {
        if (body.getLanguage() == null && body.getTheme() == null && body.getAccent() == null
                && body.getWeeklyReportOptIn() == null && body.getMonthlyReportOptIn() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message",
                            "Provide at least one preference field to update"));
        }
        try {
            return ResponseEntity.ok(sellerService.updatePreferences(me.subjectId(), body));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @PatchMapping("/payment")
    public ResponseEntity<?> payment(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                     @Valid @RequestBody PaymentSettingsRequest body) {
        try {
            return ResponseEntity.ok(sellerService.updatePayment(me.subjectId(), body));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @PostMapping("/payment/qr")
    public ResponseEntity<?> uploadQrCode(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                          @RequestParam("image") MultipartFile image) {
        try {
            String filename = sellerService.saveQrCode(me.subjectId(), image);
            return ResponseEntity.ok(Map.of("paymentQRCode", filename));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @GetMapping("/payment/qr")
    public ResponseEntity<Resource> getMyQrCode(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        try {
            Map.Entry<String, InputStreamResource> entry = sellerService.getMyQrCode(me.subjectId());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(entry.getKey()))
                    .cacheControl(CacheControl.noStore())
                    .body(entry.getValue());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
