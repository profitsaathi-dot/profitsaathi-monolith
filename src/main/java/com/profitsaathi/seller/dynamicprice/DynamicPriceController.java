package com.profitsaathi.seller.dynamicprice;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dynamic-prices")
@AllArgsConstructor
public class DynamicPriceController {

    private final DynamicPriceService service;

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                    @RequestBody DynamicPriceCreateRequest body) {
        try {
            Map<String, Object> created = service.create(me.subjectId(), body);
            return ResponseEntity.status(201).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Collections.singletonMap("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @GetMapping("/mine")
    public ResponseEntity<?> mine(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        try {
            List<Map<String, Object>> result = service.listMine(me.subjectId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @GetMapping("/public")
    public ResponseEntity<?> getPublic(@RequestParam String token) {
        try {
            return ResponseEntity.ok(service.getPublic(token));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                    @PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.cancel(me.subjectId(), id));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Collections.singletonMap("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Collections.singletonMap("message", e.getMessage()));
        }
    }
}
