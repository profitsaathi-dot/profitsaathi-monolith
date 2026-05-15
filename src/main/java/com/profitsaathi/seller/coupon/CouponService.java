package com.profitsaathi.seller.coupon;

import com.profitsaathi.seller.product.Product;
import com.profitsaathi.seller.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository repo;
    private final CouponUsageRepository usageRepo;
    private final ProductRepository productRepository;

    public Coupon create(CouponRequest req) {
        Coupon c = new Coupon();
        c.setCode(req.getCode());
        c.setDiscountType(DiscountType.valueOf(req.getDiscountType()));
        c.setDiscountValue(req.getDiscountValue());
        c.setMaxDiscount(req.getMaxDiscount());
        c.setMinOrderAmount(req.getMinOrderAmount());
        c.setUsageLimit(req.getUsageLimit());
        c.setPerUserLimit(req.getPerUserLimit());
        c.setExpiryDate(req.getExpiryDate());
        return repo.save(c);
    }

    public BigDecimal applyCoupon(ApplyCouponRequest req) {
        Coupon coupon = repo.findByCode(req.getCode())
                .orElseThrow(() -> new RuntimeException("Coupon not found"));

        if (Boolean.FALSE.equals(coupon.getActive())) throw new RuntimeException("Coupon inactive");
        if (coupon.getExpiryDate() != null && coupon.getExpiryDate().isBefore(LocalDateTime.now()))
            throw new RuntimeException("Coupon expired");
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit())
            throw new RuntimeException("Coupon limit reached");
        if (req.getOrderAmount().compareTo(coupon.getMinOrderAmount()) < 0)
            throw new RuntimeException("Minimum order not met");

        Product product = productRepository.findByIdAndStatus(req.getProduct_id(), "ACTIVE");

        long userUsed = usageRepo.countByCouponIdAndProduct(coupon.getId(), product);
        if (userUsed >= coupon.getPerUserLimit()) throw new RuntimeException("User limit exceeded");

        BigDecimal discount;
        if (coupon.getDiscountType() == DiscountType.FLAT) {
            discount = coupon.getDiscountValue();
        } else {
            discount = req.getOrderAmount()
                    .multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100));
            if (coupon.getMaxDiscount() != null && discount.compareTo(coupon.getMaxDiscount()) > 0) {
                discount = coupon.getMaxDiscount();
            }
        }
        return discount;
    }

    public void markUsed(String couponCode, Long productId, String orderId) {
        Coupon coupon = repo.findByCode(couponCode).orElseThrow();
        coupon.setUsedCount(coupon.getUsedCount() + 1);
        repo.save(coupon);

        Product product = productRepository.findByIdAndStatus(productId, "ACTIVE");
        CouponUsage usage = new CouponUsage();
        usage.setCouponId(coupon.getId());
        usage.setProduct(product);
        usage.setOrderId(orderId);
        usageRepo.save(usage);
    }
}
