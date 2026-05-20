package com.profitsaathi.seller.review;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Product review endpoints.
 * Public endpoints for submitting and viewing reviews.
 * Seller endpoints for managing reviews.
 */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductReviewService reviewService;

    /**
     * Submit a review for a delivered order (public endpoint)
     * POST /api/v1/reviews/order/{orderNo}
     */
    @PostMapping("/order/{orderNo}")
    public ResponseEntity<?> submitReview(
            @PathVariable String orderNo,
            @Valid @RequestBody ReviewRequest request) {
        return reviewService.submitReview(orderNo, request);
    }

    /**
     * Check if an order can be reviewed (public endpoint)
     * GET /api/v1/reviews/order/{orderNo}/can-review
     */
    @GetMapping("/order/{orderNo}/can-review")
    public ResponseEntity<?> canReview(@PathVariable String orderNo) {
        return reviewService.canReview(orderNo);
    }

    /**
     * Get reviews for a product (public endpoint)
     * GET /api/v1/reviews/product/{productId}
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<?> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return reviewService.getProductReviews(productId, page, size);
    }

    /**
     * Get rating statistics for a product (public endpoint)
     * GET /api/v1/reviews/product/{productId}/stats
     */
    @GetMapping("/product/{productId}/stats")
    public ResponseEntity<?> getProductStats(@PathVariable Long productId) {
        return ResponseEntity.ok(reviewService.getProductRatingStats(productId));
    }

    /**
     * Get seller's product reviews (seller endpoint)
     * GET /api/v1/reviews/seller
     */
    @GetMapping("/seller")
    public ResponseEntity<?> getSellerReviews(
            @AuthenticationPrincipal AuthenticatedPrincipal me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return reviewService.getSellerReviews(me.subjectId(), page, size);
    }

    /**
     * Moderate a review (approve/hide) (seller endpoint)
     * PATCH /api/v1/reviews/{reviewId}/moderate
     */
    @PatchMapping("/{reviewId}/moderate")
    public ResponseEntity<?> moderateReview(
            @AuthenticationPrincipal AuthenticatedPrincipal me,
            @PathVariable Long reviewId,
            @RequestParam boolean approved) {
        return reviewService.moderateReview(me.subjectId(), reviewId, approved);
    }
}
