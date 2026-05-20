package com.profitsaathi.seller.review;

import com.profitsaathi.seller.order.Order;
import com.profitsaathi.seller.order.OrderRepository;
import com.profitsaathi.seller.product.Product;
import com.profitsaathi.seller.product.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductReviewService {

    private final ProductReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    /**
     * Submit a review for a delivered order.
     * Only allows review if order status is DELIVERED.
     */
    @Transactional
    @CacheEvict(value = {"productReviews", "productRatings"}, allEntries = true)
    public ResponseEntity<?> submitReview(String orderNo, ReviewRequest request) {
        // Validate input
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Rating must be between 1 and 5"));
        }

        // Find order by order number
        Order order = orderRepository.findByOrderNoIgnoreCase(orderNo)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        // Check if order is delivered
        if (!"DELIVERED".equalsIgnoreCase(order.getOrderStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Can only review delivered orders"));
        }

        // Check if already reviewed
        if (reviewRepository.existsByOrderId(order.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "You have already reviewed this order"));
        }

        // Create review
        ProductReview review = new ProductReview();
        review.setProduct(order.getProduct());
        review.setOrder(order);
        review.setCustomerName(order.getCustomerName());
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setVerified(true);
        review.setApproved(true); // Auto-approve, can add moderation later

        reviewRepository.save(review);

        log.info("Review submitted: orderId={}, productId={}, rating={}",
                order.getId(), order.getProduct().getId(), request.getRating());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Review submitted successfully",
                        "reviewId", review.getId()
                ));
    }

    /**
     * Get reviews for a product (public endpoint)
     */
    @Transactional(readOnly = true)
    public ResponseEntity<?> getProductReviews(Long productId, int page, int size) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        // Use cached method
        Map<String, Object> result = getProductReviewsCached(productId, page, size);
        
        return ResponseEntity.ok(result);
    }

    /**
     * Internal cached method - caches the Map, not ResponseEntity
     */
    @Cacheable(value = "productReviews", key = "#productId + '_' + #page + '_' + #size")
    public Map<String, Object> getProductReviewsCached(Long productId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductReview> reviews = reviewRepository.findApprovedByProductId(productId, pageable);

        // Get rating statistics
        Map<String, Object> stats = getProductRatingStats(productId);

        return Map.of(
                "reviews", reviews.getContent().stream()
                        .map(this::toReviewDTO)
                        .collect(Collectors.toList()),
                "currentPage", reviews.getNumber(),
                "totalItems", reviews.getTotalElements(),
                "totalPages", reviews.getTotalPages(),
                "stats", stats
        );
    }

    /**
     * Get rating statistics for a product
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "productRatings", key = "#productId")
    public Map<String, Object> getProductRatingStats(Long productId) {
        Double avgRating = reviewRepository.getAverageRating(productId);
        long totalReviews = reviewRepository.countByProductId(productId);
        List<ProductReviewRepository.RatingCount> distribution =
                reviewRepository.getRatingDistribution(productId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("averageRating", avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);
        stats.put("totalReviews", totalReviews);

        // Rating distribution (5 stars to 1 star)
        Map<Integer, Long> dist = new HashMap<>();
        for (int i = 5; i >= 1; i--) {
            dist.put(i, 0L);
        }
        for (ProductReviewRepository.RatingCount rc : distribution) {
            dist.put(rc.getRating(), rc.getCount());
        }
        stats.put("distribution", dist);

        return stats;
    }

    /**
     * Check if order can be reviewed
     */
    @Transactional(readOnly = true)
    public ResponseEntity<?> canReview(String orderNo) {
        Order order = orderRepository.findByOrderNoIgnoreCase(orderNo)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        boolean isDelivered = "DELIVERED".equalsIgnoreCase(order.getOrderStatus());
        boolean alreadyReviewed = reviewRepository.existsByOrderId(order.getId());

        return ResponseEntity.ok(Map.of(
                "canReview", isDelivered && !alreadyReviewed,
                "isDelivered", isDelivered,
                "alreadyReviewed", alreadyReviewed,
                "productId", order.getProduct().getId(),
                "productName", order.getProduct().getName()
        ));
    }

    /**
     * Get seller's product reviews (for seller dashboard)
     */
    @Transactional(readOnly = true)
    public ResponseEntity<?> getSellerReviews(Long sellerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductReview> reviews = reviewRepository.findBySellerIdOrderByCreatedAtDesc(sellerId, pageable);

        return ResponseEntity.ok(Map.of(
                "reviews", reviews.getContent().stream()
                        .map(this::toSellerReviewDTO)
                        .collect(Collectors.toList()),
                "currentPage", reviews.getNumber(),
                "totalItems", reviews.getTotalElements(),
                "totalPages", reviews.getTotalPages()
        ));
    }

    /**
     * Moderate review (approve/reject)
     */
    @Transactional
    @CacheEvict(value = {"productReviews", "productRatings"}, allEntries = true)
    public ResponseEntity<?> moderateReview(Long sellerId, Long reviewId, boolean approved) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found"));

        // Verify seller owns the product
        if (!review.getProduct().getSeller().getId().equals(sellerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Not authorized"));
        }

        review.setApproved(approved);
        reviewRepository.save(review);

        return ResponseEntity.ok(Map.of(
                "message", approved ? "Review approved" : "Review hidden",
                "reviewId", reviewId
        ));
    }

    // DTO conversion methods

    private Map<String, Object> toReviewDTO(ProductReview review) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", review.getId());
        dto.put("customerName", review.getCustomerName());
        dto.put("rating", review.getRating());
        dto.put("comment", review.getComment());
        dto.put("verified", review.getVerified());
        dto.put("createdAt", review.getCreatedAt().toString()); // Convert to string
        return dto;
    }

    private Map<String, Object> toSellerReviewDTO(ProductReview review) {
        Map<String, Object> dto = toReviewDTO(review);
        dto.put("productId", review.getProduct().getId());
        dto.put("productName", review.getProduct().getName());
        dto.put("approved", review.getApproved());
        return dto;
    }
}
