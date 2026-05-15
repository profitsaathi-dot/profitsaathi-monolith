package com.profitsaathi.seller.aichat;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import com.profitsaathi.seller.aichat.AiChatDtos.GeneratedImageResponse;
import com.profitsaathi.seller.aichat.AiChatDtos.GenerateImageRequest;
import com.profitsaathi.seller.aichat.AiChatDtos.ProviderStatus;
import com.profitsaathi.seller.aichat.AiChatDtos.SaveKeyRequest;
import com.profitsaathi.seller.aichat.AiChatDtos.SendRequest;
import com.profitsaathi.seller.aichat.AiChatDtos.SendResponse;
import com.profitsaathi.seller.aichat.AiChatDtos.SessionDetail;
import com.profitsaathi.seller.aichat.AiChatDtos.SessionSummary;
import com.profitsaathi.seller.aichat.AiChatDtos.SetPrimaryRequest;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * HTTP surface for Saathi AI. Mounted at {@code /api/v1/ai/saathi/**} so it
 * doesn't collide with the monolith's older {@code com.profitsaathi.ai.AiController}.
 * Every route is gated by:
 *   - {@code @PreAuthorize("hasRole('SELLER')")} on the class for auth
 *   - {@link SaathiKillSwitchInterceptor} for the global on/off flag
 *
 * The controller is thin on purpose — orchestration lives in
 * {@link SaathiAiService}, mapping of exceptions to HTTP codes lives here.
 */
@RestController
@RequestMapping("/api/v1/ai/saathi")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class AiChatController {

    private final SaathiAiService service;

    // ─────────────────────────────────────────────────────────────────────
    // Chat
    // ─────────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> send(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                  @RequestBody SendRequest body) {
        try {
            return ResponseEntity.ok(service.send(me, body));
        } catch (NoAiProviderConfiguredException e) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(Map.of("message", e.getMessage(), "code", "NO_PROVIDER_CONFIGURED"));
        } catch (AllAiProvidersFailedException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "message", "Every configured AI provider failed. Check your API keys in Settings.",
                    "code", "ALL_PROVIDERS_FAILED",
                    "failures", e.getFailures()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Sessions
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/sessions")
    public List<SessionSummary> sessions(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        return service.listSessions(me);
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<?> session(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                     @PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.getSession(me, id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<?> deleteSession(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                           @PathVariable Long id) {
        try {
            service.deleteSession(me, id);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Provider keys
    // ─────────────────────────────────────────────────────────────────────

    /** Returns which provider keys are on file + which is primary. */
    @GetMapping("/providers")
    public ResponseEntity<?> providers(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        try {
            return ResponseEntity.ok(service.providerStatus(me));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /** Save (or clear) the key for a single provider. Pass {@code apiKey: ""} to remove. */
    @PutMapping("/providers/key")
    public ResponseEntity<?> saveApiKey(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                        @RequestBody SaveKeyRequest body) {
        AiProvider provider = AiProvider.fromString(body.provider());
        if (provider == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Unknown provider — must be one of GEMINI, OPENROUTER, NVIDIA."));
        }
        try {
            ProviderStatus status = service.saveApiKey(me, provider, body.apiKey());
            return ResponseEntity.ok(status);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /** Choose which provider the router tries first. */
    @PutMapping("/providers/primary")
    public ResponseEntity<?> setPrimary(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                        @RequestBody SetPrimaryRequest body) {
        AiProvider provider = AiProvider.fromString(body.provider());
        if (provider == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Unknown provider."));
        }
        try {
            return ResponseEntity.ok(service.setPrimaryProvider(me, provider));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Image generation / edit
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generate (no input image) or edit (with input image) — same endpoint,
     * dispatched by presence of {@code imageBase64} in the body. Returns
     * metadata only; bytes are fetched via {@link #image(AuthenticatedPrincipal, Long)}.
     */
    @PostMapping("/image")
    public ResponseEntity<?> generateImage(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                           @RequestBody GenerateImageRequest body) {
        try {
            GeneratedImageResponse resp = service.generateImage(
                    me, body.sessionId(), body.prompt(), body.imageMime(), body.imageBase64());
            return ResponseEntity.ok(resp);
        } catch (NoAiProviderConfiguredException e) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(Map.of("message", e.getMessage(), "code", "NO_PROVIDER_CONFIGURED"));
        } catch (AllAiProvidersFailedException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "message", "All image-gen providers failed.",
                    "code", "ALL_PROVIDERS_FAILED",
                    "failures", e.getFailures()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /** Streams bytes of a previously-generated image; no-store cache so leaked URLs can't be replayed. */
    @GetMapping("/image/{messageId}")
    public ResponseEntity<?> image(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                   @PathVariable Long messageId) {
        try {
            SaathiAiService.StoredImage img = service.loadImage(me, messageId);
            InputStreamResource body = new InputStreamResource(service.openImageStream(img));
            String mime = img.mimeType() == null ? "image/png" : img.mimeType();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mime))
                    .cacheControl(CacheControl.noStore())
                    .body(body);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Could not read image"));
        }
    }
}
