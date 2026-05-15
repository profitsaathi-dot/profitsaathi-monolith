package com.profitsaathi.usagetracking;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usage")
public class UsageTrackingController {

    private final UsageTrackingService service;

    public UsageTrackingController(UsageTrackingService service) {
        this.service = service;
    }

    @PostMapping("/increment")
    public UsageTracking increment(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                   @RequestParam String featureName) {
        return service.incrementUsage(me, featureName);
    }
}
