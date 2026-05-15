package com.profitsaathi.seller.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SellerRepository extends JpaRepository<Seller, Long> {
    Optional<Seller> findByEmail(String email);
    Optional<Seller> findByPublicToken(String publicToken);
    boolean existsByEmail(String email);
    List<Seller> findByStatus(Seller.Status status);

    long countByStatus(Seller.Status status);
    long countByCreatedAtAfter(java.time.LocalDateTime since);
}
