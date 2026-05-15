package com.profitsaathi.seller.smartPricing.Controller;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import com.profitsaathi.seller.smartPricing.DTO.PricingRequest;
import com.profitsaathi.seller.smartPricing.DTO.PricingResponse;
import com.profitsaathi.seller.smartPricing.Service.PricingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/price-adviser")
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @PostMapping("/sync")
    public PricingResponse getCalculation(@AuthenticationPrincipal AuthenticatedPrincipal me,@RequestBody PricingRequest request) {
        return pricingService.calculate(me,request);
    }
}
