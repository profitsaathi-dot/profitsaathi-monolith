package com.profitsaathi.seller.shipping;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/shipping-vendors")
@AllArgsConstructor
public class ShippingVendorController {

    private final ShippingVendorService service;

    @GetMapping
    public ResponseEntity<?> list() {
        try {
            return ResponseEntity.ok(service.listAll());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @GetMapping("/{code}")
    public ResponseEntity<?> get(@PathVariable String code) {
        try {
            return ResponseEntity.ok(service.getOrThrow(code));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @GetMapping("/{code}/url")
    public ResponseEntity<?> resolveUrl(@PathVariable String code,
                                        @RequestParam("trackingId") String trackingId) {
        try {
            String url = service.resolveTrackingUrl(code, trackingId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", code);
            body.put("trackingId", trackingId);
            body.put("url", url);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Collections.singletonMap("message", e.getMessage()));
        }
    }
}
