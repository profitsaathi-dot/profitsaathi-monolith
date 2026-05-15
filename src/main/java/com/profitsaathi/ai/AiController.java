package com.profitsaathi.ai;

import com.profitsaathi.ai.log.AiChatLog;
import com.profitsaathi.ai.log.AiChatLogRepository;
import com.profitsaathi.auth.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public surface of the AI module. Three endpoint groups:
 *
 *   POST /api/v1/ai/chat              — text chat (any authenticated user)
 *   POST /api/v1/ai/verify-payment    — multipart upload, vision pipeline
 *   GET  /api/v1/ai/admin/logs        — recent chat history (ADMIN only)
 *   GET  /api/v1/ai/admin/usage       — aggregate counts + cost (ADMIN only)
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiChatService aiChatService;
    private final AiChatLogRepository logRepository;

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@Valid @RequestBody AiChatRequest req,
                                  @AuthenticationPrincipal AuthenticatedPrincipal me) {
        try {
            return ResponseEntity.ok(aiChatService.chat(req, me));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Collections.singletonMap("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @PostMapping(value = "/verify-payment", consumes = "multipart/form-data")
    public ResponseEntity<?> verifyPayment(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "expectedAmount", required = false) Double expectedAmount,
            @RequestParam(value = "expectedReceiver", required = false) String expectedReceiver,
            @AuthenticationPrincipal AuthenticatedPrincipal me) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message", "file is required"));
        }
        try {
            return ResponseEntity.ok(aiChatService.verifyPayment(
                    file.getBytes(), expectedAmount, expectedReceiver, me));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("message", "Failed to read uploaded file: " + e.getMessage()));
        }
    }

    @GetMapping("/admin/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AiChatLog> recentLogs(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "25") int size,
            @RequestParam(value = "principalEmail", required = false) String principalEmail) {
        var pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return principalEmail == null || principalEmail.isBlank()
                ? logRepository.findAllByOrderByCreatedAtDesc(pageable)
                : logRepository.findByPrincipalEmailOrderByCreatedAtDesc(principalEmail, pageable);
    }

    @GetMapping("/admin/usage")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> usage(@RequestParam(value = "days", defaultValue = "30") int days) {
        int window = Math.max(1, Math.min(days, 365));
        LocalDateTime since = LocalDateTime.now().minusDays(window);
        long calls = logRepository.countByCreatedAtAfter(since);
        var cost = logRepository.sumCostSince(since);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("windowDays", window);
        body.put("calls", calls);
        body.put("cost", cost == null ? java.math.BigDecimal.ZERO : cost);
        return body;
    }
}
