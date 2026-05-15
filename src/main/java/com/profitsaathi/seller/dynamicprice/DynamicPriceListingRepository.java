package com.profitsaathi.seller.dynamicprice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DynamicPriceListingRepository extends JpaRepository<DynamicPriceListing, Long> {
    Optional<DynamicPriceListing> findByPublicToken(String publicToken);
    List<DynamicPriceListing> findBySeller_IdOrderByIdDesc(Long sellerId);
}
