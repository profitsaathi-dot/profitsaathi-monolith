package com.profitsaathi.seller.subscription;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SubscriptionService {

    private final SubscriptionRepository repository;

    public SubscriptionService(SubscriptionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Subscription save(Subscription subscription) {
        return repository.save(subscription);
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> getActive(Long sellerId) {
        return repository.findBySellerIdAndStatus(sellerId, "ACTIVE");
    }
}
