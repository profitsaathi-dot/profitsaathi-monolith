package com.profitsaathi.seller.pricing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PricingAnalysisRepository extends JpaRepository<PricingAnalysis, Long> {
    Optional<PricingAnalysis> findByProductId(Long productId);
    boolean existsByProductIdAndLastCalculatedAtAfter(Long productId, LocalDateTime dateTime);
}
