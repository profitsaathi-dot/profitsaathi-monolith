package com.profitsaathi.seller.dynamicprice;

import com.profitsaathi.seller.order.Order;
import com.profitsaathi.seller.product.Product;
import com.profitsaathi.seller.product.ProductRepository;
import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class DynamicPriceService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int DEFAULT_EXPIRY_HOURS = 24;
    private static final int MAX_EXPIRY_HOURS = 24 * 30;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_USED = "USED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final DynamicPriceListingRepository repository;
    private final ProductRepository productRepository;
    private final SellerRepository sellerRepository;

    @Transactional
    public Map<String, Object> create(Long sellerId, DynamicPriceCreateRequest request) {
        if (request.getProductId() == null) throw new IllegalArgumentException("productId is required");
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be greater than 0");
        }

        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new RuntimeException("Seller not registered"));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getSeller() == null || !product.getSeller().getId().equals(seller.getId())) {
            throw new RuntimeException("Product does not belong to this seller");
        }

        int hours = request.getExpiryHours() != null && request.getExpiryHours() > 0
                ? Math.min(request.getExpiryHours(), MAX_EXPIRY_HOURS)
                : DEFAULT_EXPIRY_HOURS;

        DynamicPriceListing listing = new DynamicPriceListing();
        listing.setSeller(seller);
        listing.setProduct(product);
        listing.setPrice(request.getPrice());
        listing.setCustomerName(trimToNull(request.getCustomerName()));
        listing.setNote(trimToNull(request.getNote()));
        listing.setStatus(STATUS_ACTIVE);
        listing.setPublicToken(generateToken());
        listing.setExpiresAt(LocalDateTime.now().plusHours(hours));
        repository.save(listing);
        return toOwnerMap(listing);
    }

    // toOwnerMap → productSummary touches l.getProduct() (lazy @ManyToOne)
    // and p.getImagePaths() (lazy @ElementCollection). With open-in-view=false
    // the session must stay open while we build the response map.
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMine(Long sellerId) {
        return repository.findBySeller_IdOrderByIdDesc(sellerId).stream()
                .map(this::toOwnerMap)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> getPublic(String token) {
        DynamicPriceListing listing = repository.findByPublicToken(token)
                .orElseThrow(() -> new RuntimeException("Listing not found"));

        if (STATUS_ACTIVE.equals(listing.getStatus())
                && listing.getExpiresAt() != null
                && listing.getExpiresAt().isBefore(LocalDateTime.now())) {
            listing.setStatus(STATUS_EXPIRED);
            repository.save(listing);
        }
        return toPublicMap(listing);
    }

    public DynamicPriceListing requireActive(String token) {
        DynamicPriceListing listing = repository.findByPublicToken(token)
                .orElseThrow(() -> new RuntimeException("Listing not found"));
        if (!STATUS_ACTIVE.equals(listing.getStatus())) {
            throw new RuntimeException("Listing is no longer available (" + listing.getStatus() + ")");
        }
        if (listing.getExpiresAt() != null && listing.getExpiresAt().isBefore(LocalDateTime.now())) {
            listing.setStatus(STATUS_EXPIRED);
            repository.save(listing);
            throw new RuntimeException("Listing has expired");
        }
        return listing;
    }

    @Transactional
    public void markUsed(DynamicPriceListing listing, Order order) {
        listing.setStatus(STATUS_USED);
        listing.setOrder(order);
        listing.setUsedAt(LocalDateTime.now());
        repository.save(listing);
    }

    @Transactional
    public Map<String, Object> cancel(Long sellerId, Long id) {
        DynamicPriceListing listing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Listing not found"));

        if (listing.getSeller() == null || !sellerId.equals(listing.getSeller().getId())) {
            throw new RuntimeException("Listing not found");
        }
        if (!STATUS_ACTIVE.equals(listing.getStatus())) {
            throw new IllegalStateException(
                    "Only active listings can be cancelled (current: " + listing.getStatus() + ")");
        }

        listing.setStatus(STATUS_CANCELLED);
        repository.save(listing);
        return toOwnerMap(listing);
    }

    private String generateToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return "dp_" + HexFormat.of().formatHex(bytes);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Map<String, Object> toOwnerMap(DynamicPriceListing l) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", l.getId());
        map.put("publicToken", l.getPublicToken());
        map.put("price", l.getPrice());
        map.put("customerName", l.getCustomerName());
        map.put("note", l.getNote());
        map.put("status", l.getStatus());
        map.put("expiresAt", l.getExpiresAt());
        map.put("createdAt", l.getCreatedAt());
        map.put("usedAt", l.getUsedAt());
        Map<String, Object> productMap = productSummary(l.getProduct());
        if (productMap != null) map.put("product", productMap);
        return map;
    }

    private Map<String, Object> toPublicMap(DynamicPriceListing l) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("publicToken", l.getPublicToken());
        map.put("price", l.getPrice());
        map.put("customerName", l.getCustomerName());
        map.put("note", l.getNote());
        map.put("status", l.getStatus());
        map.put("expiresAt", l.getExpiresAt());
        Map<String, Object> productMap = productSummary(l.getProduct());
        if (productMap != null) map.put("product", productMap);
        return map;
    }

    private Map<String, Object> productSummary(Product p) {
        if (p == null) return null;
        Map<String, Object> productMap = new HashMap<>();
        productMap.put("id", p.getId());
        productMap.put("name", p.getName());
        productMap.put("description", p.getDescription());
        int imageCount = p.getImagePaths() != null ? p.getImagePaths().size() : 0;
        int mainIndex = p.getMainImageIndex() != null ? p.getMainImageIndex() : 0;
        productMap.put("imageCount", imageCount);
        productMap.put("mainImageIndex", mainIndex);
        productMap.put("mainImageUrl",
                imageCount > 0
                        ? "/api/v1/products/" + p.getId() + "/image?index=" + mainIndex
                        : null);
        return productMap;
    }
}
