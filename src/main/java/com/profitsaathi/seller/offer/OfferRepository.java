package com.profitsaathi.seller.offer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long> {
    Optional<Offer> findByIdAndActiveTrue(Long id);
    List<Offer> findByProduct_IdAndActiveTrue(Long productId);
}
