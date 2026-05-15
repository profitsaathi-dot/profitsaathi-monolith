package com.profitsaathi.seller.coupon;

import com.profitsaathi.seller.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, String> {
    long countByCouponIdAndProduct(String couponId, Product product);
}
