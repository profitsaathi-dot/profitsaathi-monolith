package com.profitsaathi.seller.product;

import com.profitsaathi.seller.user.Seller;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findBySellerId(Long sellerId, Pageable pageable);

    Product findByIdAndStatus(Long id, String status);

    /** Paginated status filter — used by the pricing scheduler to walk
     *  ACTIVE products in batches instead of loading every product. */
    Page<Product> findByStatus(String status, Pageable pageable);

    List<Product> findBySellerIdAndStatus(Long sellerId, String status);

    List<Product> findBySellerIdOrderByIdDesc(Long sellerId);

    Page<Product> findByUpdatedAtAfter(LocalDateTime time, Pageable pageable);

    Optional<Product> findByNameIgnoreCaseAndSellerId(String name, Long sellerId);

    Optional<Product> findByNameIgnoreCaseAndSellerIdAndIdNot(String name, Long sellerId, Long id);

    Page<Product> findByDescriptionContainingIgnoreCaseAndSellerId(String description,
                                                                   Long sellerId,
                                                                   Pageable pageable);

    boolean existsBySeller(Seller seller);

    Optional<Product> findByPublicTokenAndStatus(String token, String status);

    // ── Aggregations used by the admin "seller report" view ──────────────
    long countBySellerId(Long sellerId);

    long countBySellerIdAndStatus(Long sellerId, String status);
}
