package com.profitsaathi.seller.ai;

import com.profitsaathi.seller.user.Seller;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, Long> {
    boolean existsBySellerAndSuggestionTypeAndCreatedAtAfter(
            Seller seller, String suggestionType, LocalDateTime createdAt);

    List<AiSuggestion> findBySellerId(Long sellerId);

    /** Recent active suggestions, newest first — used by the weekly/monthly
     *  report mail flows to surface a few tips alongside the KPIs. */
    List<AiSuggestion> findBySellerAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            Seller seller, String status, LocalDateTime createdAfter,
            org.springframework.data.domain.Pageable pageable);
}
