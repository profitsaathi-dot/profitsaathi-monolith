package com.profitsaathi.seller.subscription;

import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    public Subscription create(@RequestBody Subscription subscription) {
        return service.save(subscription);
    }

    @GetMapping("/active/{sellerId}")
    public Optional<Subscription> getActive(@PathVariable Long sellerId) {
        return service.getActive(sellerId);
    }
}
