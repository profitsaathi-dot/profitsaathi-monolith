package com.profitsaathi.notification.log;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class NotificationLogController {

    private final EmailLogRepository emailLogRepository;
    private final WhatsAppLogRepository whatsAppLogRepository;

    @GetMapping("/email")
    public ResponseEntity<Map<String, Object>> emails(Pageable pageable) {
        return ResponseEntity.ok(toResponse(emailLogRepository.findAll(pageable)));
    }

    @GetMapping("/whatsapp")
    public ResponseEntity<Map<String, Object>> whatsapp(Pageable pageable) {
        return ResponseEntity.ok(toResponse(whatsAppLogRepository.findAll(pageable)));
    }

    private static Map<String, Object> toResponse(Page<?> page) {
        Map<String, Object> r = new HashMap<>();
        r.put("content", page.getContent());
        r.put("currentPage", page.getNumber());
        r.put("totalItems", page.getTotalElements());
        r.put("totalPages", page.getTotalPages());
        return r;
    }
}
