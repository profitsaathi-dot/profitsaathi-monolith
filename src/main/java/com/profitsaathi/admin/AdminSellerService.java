package com.profitsaathi.admin;

import com.profitsaathi.seller.order.OrderRepository;
import com.profitsaathi.seller.product.ProductRepository;
import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-facing seller operations. Only patches profile fields — auth
 * credentials, payment, language/theme/accent are owned by the seller and
 * intentionally not exposed here.
 */
@Service
@RequiredArgsConstructor
public class AdminSellerService {

    private final SellerRepository sellerRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public List<Seller> list(Seller.Status status) {
        return status == null ? sellerRepository.findAll() : sellerRepository.findByStatus(status);
    }

    public Seller get(Long id) {
        return sellerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Seller not found: " + id));
    }

    @Transactional
    public Seller update(Long id, AdminSellerUpdateRequest req) {
        Seller seller = get(id);
        if (req.getName() != null)       seller.setName(req.getName().trim());
        if (req.getStoreName() != null)  seller.setStoreName(req.getStoreName().trim());
        if (req.getSellerType() != null) seller.setSellerType(req.getSellerType().trim());
        if (req.getMobile() != null)     seller.setMobile(req.getMobile().trim());
        if (req.getStatus() != null)     seller.setStatus(req.getStatus());
        return sellerRepository.save(seller);
    }

    /**
     * Single round-trip aggregation for the admin "seller report" view.
     * Aggregates over both orders and products tables; query count is fixed
     * (not proportional to row count) so this stays cheap as data grows.
     */
    public SellerReport report(Long sellerId) {
        Seller seller = get(sellerId);

        long totalOrders = orderRepository.countBySeller_Id(sellerId);
        BigDecimal totalRevenue = orderRepository.sumTotalCostBySeller(sellerId);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;
        BigDecimal aov = totalOrders == 0
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);

        Map<String, Long> byOrderStatus = bucketize(
                orderRepository.countByOrderStatusForSeller(sellerId));
        Map<String, Long> byPaymentStatus = bucketize(
                orderRepository.countByPaymentStatusForSeller(sellerId));

        SellerReport.OrderStats orderStats = new SellerReport.OrderStats(
                totalOrders,
                totalRevenue,
                aov,
                orderRepository.lastOrderAtBySeller(sellerId),
                byOrderStatus,
                byPaymentStatus);

        long totalProducts = productRepository.countBySellerId(sellerId);
        long activeProducts = productRepository.countBySellerIdAndStatus(sellerId, "ACTIVE");
        SellerReport.ProductStats productStats = new SellerReport.ProductStats(
                totalProducts,
                activeProducts,
                Math.max(0, totalProducts - activeProducts));

        return new SellerReport(seller, orderStats, productStats);
    }

    private static Map<String, Long> bucketize(List<OrderRepository.OrderStatusCount> rows) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (rows == null) return out;
        for (OrderRepository.OrderStatusCount r : rows) {
            // Guard against null statuses showing up as a "null" bucket key.
            String key = r.getStatus() == null ? "UNKNOWN" : r.getStatus();
            out.merge(key, r.getCount() == null ? 0L : r.getCount(), Long::sum);
        }
        return out;
    }
}
