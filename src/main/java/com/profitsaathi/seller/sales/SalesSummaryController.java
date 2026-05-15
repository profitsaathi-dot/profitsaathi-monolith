package com.profitsaathi.seller.sales;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sales-summary")
public class SalesSummaryController {

    private final SalesSummaryService service;

    public SalesSummaryController(SalesSummaryService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        return ResponseEntity.ok(service.getDashboard(me.subjectId()));
    }
}
