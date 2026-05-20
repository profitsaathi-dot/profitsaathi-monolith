package com.profitsaathi.seller.review;

import com.profitsaathi.seller.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    /**
     * Check if customer has already reviewed this order
     */
    boolean existsByOrderId(Long orderId);

    /**
     * Get review by order ID
     */
    Optional<ProductReview> findByOrderId(Long orderId);

    /**
     * Get all approved reviews for a product (for display on product page)
     */
    @Query("""
        SELECT r FROM ProductReview r
        WHERE r.product.id = :productId
          AND r.approved = true
        ORDER BY r.createdAt DESC
    """)
    Page<ProductReview> findApprovedByProductId(@Param("productId") Long productId, Pageable pageable);

    /**
     * Get all reviews for a product (for seller dashboard)
     */
    Page<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    /**
     * Get average rating for a product
     */
    @Query("""
        SELECT AVG(r.rating)
        FROM ProductReview r
        WHERE r.product.id = :productId
          AND r.approved = true
    """)
    Double getAverageRating(@Param("productId") Long productId);

    /**
     * Get rating distribution for a product
     */
    @Query("""
        SELECT r.rating AS rating, COUNT(r) AS count
        FROM ProductReview r
        WHERE r.product.id = :productId
          AND r.approved = true
        GROUP BY r.rating
        ORDER BY r.rating DESC
    """)
    List<RatingCount> getRatingDistribution(@Param("productId") Long productId);

    /**
     * Get total review count for a product
     */
    @Query("""
        SELECT COUNT(r)
        FROM ProductReview r
        WHERE r.product.id = :productId
          AND r.approved = true
    """)
    long countByProductId(@Param("productId") Long productId);

    /**
     * Get all reviews for a seller's products
     */
    @Query("""
        SELECT r FROM ProductReview r
        WHERE r.product.seller.id = :sellerId
        ORDER BY r.createdAt DESC
    """)
    Page<ProductReview> findBySellerIdOrderByCreatedAtDesc(@Param("sellerId") Long sellerId, Pageable pageable);

    interface RatingCount {
        Integer getRating();
        Long getCount();
    }
}
