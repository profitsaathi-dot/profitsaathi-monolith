package com.profitsaathi.seller.coupon;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService service;

    @PostMapping
    public Coupon create(@RequestBody CouponRequest req) {
        return service.create(req);
    }

    @PostMapping("/apply")
    public BigDecimal apply(@RequestBody ApplyCouponRequest req) {
        return service.applyCoupon(req);
    }

    @PostMapping("/mark-used")
    public void markUsed(@RequestParam String code,
                         @RequestParam Long product_id,
                         @RequestParam String orderId) {
        service.markUsed(code, product_id, orderId);
    }
}
