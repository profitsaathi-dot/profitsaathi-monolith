package com.profitsaathi.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only WhatsApp/WAHA overview endpoint. Returns the consolidated
 * read-model the dashboard renders — server health + active sessions.
 */
@RestController
@RequestMapping("/api/v1/admin/whatsapp")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminWhatsAppController {

    private final AdminWhatsAppService adminWhatsAppService;

    @GetMapping("/status")
    public WhatsAppOverview status() {
        return adminWhatsAppService.overview();
    }
}
