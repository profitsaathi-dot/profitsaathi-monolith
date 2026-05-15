package com.profitsaathi.seller.offer;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class OfferService {

    private final OfferRepository offerRepository;

    public OfferService(OfferRepository offerRepository) {
        this.offerRepository = offerRepository;
    }

    public Optional<Offer> getValidOffer(Long offerId) {
        return offerRepository.findByIdAndActiveTrue(offerId)
                .filter(o -> o.getEndTime() == null || o.getEndTime().isAfter(LocalDateTime.now()));
    }
}
