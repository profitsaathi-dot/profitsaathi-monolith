package com.profitsaathi.admin;

import com.profitsaathi.customer.user.CustomerRepository;
import com.profitsaathi.seller.user.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin-only platform statistics. Light aggregation queries — anything
 * heavier should move to a materialized view or scheduled snapshot.
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController {

    private final SellerRepository sellerRepository;
    private final CustomerRepository customerRepository;
    private final AdminRepository adminRepository;
    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard")
    public AdminDashboard dashboard(@RequestParam(value = "days", defaultValue = "30") int days) {
        return adminDashboardService.load(days);
    }

    @GetMapping("/users")
    public Map<String, Object> users() {
        long sellers = sellerRepository.count();
        long customers = customerRepository.count();
        long admins = adminRepository.count();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", sellers + customers + admins);
        body.put("sellers", sellers);
        body.put("customers", customers);
        body.put("admins", admins);
        return body;
    }
}
