package com.profitsaathi.seller.whatsapp.OpenWA;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import com.profitsaathi.seller.whatsapp.SendMessageRequest;
import com.profitsaathi.seller.whatsapp.WhatsAppSessionService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/whatsapp/open")
@AllArgsConstructor
public class OpenWhatsAppSessionController {

    private final OpenWAWhatsAppSessionService service;

    @PostMapping("/connect")
    public ResponseEntity<?> connect(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        try {
            return ResponseEntity.ok(service.connect(me.subjectId()));
        } catch (Exception e) {
            log.error("/whatsapp/open/connect failed", e);
            return ResponseEntity.status(500).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        try {
            return ResponseEntity.ok(service.status(me.subjectId()));
        } catch (Exception e) {
            log.error("/whatsapp/status failed", e);
            return ResponseEntity.status(500).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @PostMapping("/disconnect")
    public ResponseEntity<?> disconnect(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        try {
            service.disconnect(me.subjectId());
            return ResponseEntity.ok(Collections.singletonMap("message", "Disconnected"));
        } catch (Exception e) {
            log.error("/whatsapp/disconnect failed", e);
            return ResponseEntity.status(500).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @PostMapping("/restart")
    public ResponseEntity<?> restart(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        try {
            return ResponseEntity.ok(service.restart(me.subjectId()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Collections.singletonMap("message", e.getMessage()));
        } catch (Exception e) {
            log.error("/whatsapp/restart failed", e);
            return ResponseEntity.status(500).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @PostMapping("/send")
    public ResponseEntity<?> send(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                  @RequestBody SendMessageRequest body) {
        try {
            service.sendText(me.subjectId(), body.getTo(), body.getText());
            return ResponseEntity.ok(Collections.singletonMap("message", "Sent"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(400).body(Collections.singletonMap("message", e.getMessage()));
        } catch (Exception e) {
            log.error("/whatsapp/send failed", e);
            return ResponseEntity.status(500).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    /**
     * Public — WAHA POSTs here. We don't authenticate the call (WAHA can't
     * carry a JWT) but we DO trust nothing in the body besides the session
     * name match. SecurityConfig allow-lists this path.
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestBody Map<String, Object> payload) {
        try {
            service.handleWebhook(payload);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // Always 200 — WAHA retries on non-2xx and we don't want a poison
            // message to wedge the queue. Errors are logged inside the service.
            return ResponseEntity.ok().build();
        }
    }
}
