package com.profitsaathi.ai.growthadviser;

import com.profitsaathi.ai.AiProperties;
import com.profitsaathi.ai.growthadviser.GrowthCardSyncService.CooldownActiveException;
import com.profitsaathi.auth.AuthenticatedPrincipal;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST surface for the card-driven Growth Adviser.
 *
 *   GET    /api/v1/ai/growth-adviser/cards?status=ACTIVE
 *   POST   /api/v1/ai/growth-adviser/sync           — manual refresh (rate-limited)
 *   PATCH  /api/v1/ai/growth-adviser/cards/{id}     — body: { "status": "READ"|"DONE"|"DISMISSED" }
 *   GET    /api/v1/ai/growth-adviser/status         — last sync + cooldown countdown
 *
 * All endpoints require the caller to be an authenticated SELLER.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/growth-adviser")
@RequiredArgsConstructor
public class GrowthCardController {

    private final GrowthCardService cardService;
    private final GrowthCardSyncService syncService;
    private final AiProperties props;

    @GetMapping("/cards")
    public ResponseEntity<?> listCards(
            @RequestParam(value = "status", defaultValue = "ACTIVE") String status,
            @AuthenticationPrincipal AuthenticatedPrincipal me) {
        ResponseEntity<?> sellerCheck = ensureSeller(me);
        if (sellerCheck != null) return sellerCheck;

        GrowthCard.Status parsed;
        try {
            parsed = GrowthCard.Status.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid status: " + status));
        }
        return ResponseEntity.ok(cardService.listForSeller(me.subjectId(), parsed));
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        ResponseEntity<?> sellerCheck = ensureSeller(me);
        if (sellerCheck != null) return sellerCheck;

        try {
            GrowthCardSyncService.SyncResult result = syncService.sync(me.subjectId(), "manual");
            return ResponseEntity.ok(result);
        } catch (CooldownActiveException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", e.getMessage());
            body.put("retryAfterSeconds", e.retryAfter().getSeconds());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
        } catch (GrowthCardSyncService.SellerNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Growth card sync failed for sellerId={}", me.subjectId(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", e.getMessage() == null ? "Sync failed" : e.getMessage()));
        }
    }

    @PatchMapping("/cards/{id}")
    public ResponseEntity<?> patch(@PathVariable("id") Long id,
                                   @RequestBody @NotNull Map<String, String> body,
                                   @AuthenticationPrincipal AuthenticatedPrincipal me) {
        ResponseEntity<?> sellerCheck = ensureSeller(me);
        if (sellerCheck != null) return sellerCheck;

        String statusStr = body == null ? null : body.get("status");
        if (statusStr == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "status is required"));
        }
        GrowthCard.Status status;
        try {
            status = GrowthCard.Status.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid status: " + statusStr));
        }
        try {
            return ResponseEntity.ok(cardService.updateStatus(me.subjectId(), id, status));
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        ResponseEntity<?> sellerCheck = ensureSeller(me);
        if (sellerCheck != null) return sellerCheck;

        // We don't know the seller's tier here without round-tripping the
        // subscription service — return the FREE cooldown so the UI shows
        // the worst-case wait. The actual sync call enforces the real
        // tier-specific cooldown server-side.
        return ResponseEntity.ok(cardService.statusFor(
                me.subjectId(), props.getGrowthCardCooldownHoursFree()));
    }

    private ResponseEntity<?> ensureSeller(AuthenticatedPrincipal me) {
        if (me == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required"));
        }
        if (!me.isSeller()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Growth Adviser is available to sellers only"));
        }
        return null;
    }
}
