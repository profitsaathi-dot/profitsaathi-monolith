package com.profitsaathi.seller.order;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import com.profitsaathi.seller.dynamicprice.DynamicPriceListing;
import com.profitsaathi.seller.dynamicprice.DynamicPriceService;
import com.profitsaathi.seller.offer.Offer;
import com.profitsaathi.seller.offer.OfferRepository;
import com.profitsaathi.seller.payment.Payment;
import com.profitsaathi.seller.payment.PaymentRepository;
import com.profitsaathi.seller.product.Product;
import com.profitsaathi.seller.product.ProductRepository;
import com.profitsaathi.seller.shipping.ShippingVendorService;
import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final SellerRepository sellerRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OfferRepository offerRepository;
    private final DynamicPriceService dynamicPriceService;
    private final ShippingVendorService shippingVendorService;
    private final PaymentRepository paymentRepository;
    private final OrderNotificationService orderNotificationService;

    @Transactional
    public ResponseEntity<?> createOrder(AuthenticatedPrincipal me, OrderRequest request) {
        if (request.getOrderNumber() == null || request.getOrderNumber().isEmpty()) {

            String dpToken = request.getDynamicPriceToken();
            DynamicPriceListing listing = null;
            Product product;

            if (dpToken != null && !dpToken.isEmpty()) {
                listing = dynamicPriceService.requireActive(dpToken);
                product = listing.getProduct();
                if (product == null) throw new RuntimeException("Listing has no product");
                request.setProductId(product.getId());
            } else {
                product = fetchActiveProductOrThrow(request.getProductId());
            }

            Seller seller = listing != null ? product.getSeller() : resolveOrderSeller(me, request, product);
            Offer offer = listing != null ? null : resolveOffer(request.getOfferId());

            Order order = buildOrder(request, product, seller, offer, listing);
            orderRepository.save(order);

            if (listing != null) {
                dynamicPriceService.markUsed(listing, order);
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Order Created", "orderId", order.getOrderNo()));
        }

        Optional<Order> existing = orderRepository.findByOrderNo(request.getOrderNumber());
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Order already exists", "orderId", existing.get().getOrderNo()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Order bad request"));
    }

    private Product fetchActiveProductOrThrow(Long productId) {
        Product product = productRepository.findByIdAndStatus(productId, "ACTIVE");
        if (product == null) throw new RuntimeException("Active product not found");
        return product;
    }

    private Seller resolveOrderSeller(AuthenticatedPrincipal me, OrderRequest request, Product product) {
        if ("DIRECT".equalsIgnoreCase(request.getPurchaseType())) {
            return product.getSeller();
        }
        if (me == null || me.subjectId() == null) {
            throw new RuntimeException("Not authenticated");
        }
        return sellerRepository.findById(me.subjectId())
                .orElseThrow(() -> new RuntimeException("Seller not registered"));
    }

    private Offer resolveOffer(Long offerId) {
        if (offerId == null) return null;
        return offerRepository.findByIdAndActiveTrue(offerId).orElse(null);
    }

    private Order buildOrder(OrderRequest request,
                             Product product,
                             Seller seller,
                             Offer offer,
                             DynamicPriceListing listing) {
        Order order = new Order();
        order.setSeller(seller);
        order.setProduct(product);
        order.setOffer(offer);

        order.setCustomerName(request.getCustomerName());
        order.setPhoneNumber(request.getPhoneNumber());
        order.setAddress(request.getAddress());
        order.setQuantity(request.getQuantity());
        order.setComments(request.getComments());

        order.setStatus("CREATED");
        order.setOrderNo(generateOrderNumber());
        order.setPublicToken(UUID.randomUUID().toString().replace("-", ""));

        BigDecimal quantity = BigDecimal.valueOf(request.getQuantity());
        BigDecimal costPrice = product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;

        BigDecimal sellingPrice;
        if (listing != null) {
            sellingPrice = listing.getPrice();
            order.setOfferApplied(false);
        } else if (offer != null) {
            sellingPrice = offer.getPrice();
            order.setOfferApplied(true);
        } else {
            sellingPrice = product.getSellingPrice();
            order.setOfferApplied(false);
        }

        order.setUnitPrice(sellingPrice);
        order.setCostPrice(costPrice);
        order.setTotalCost(costPrice.multiply(quantity));
        order.setProfit(sellingPrice.subtract(costPrice).multiply(quantity));

        order.setOrderStatus("PENDING");
        order.setPaymentStatus("INITIATED");
        order.setCreatedAt(LocalDateTime.now());

        return order;
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> listOwnerOrders(AuthenticatedPrincipal me,
                                             int page, int size,
                                             String status, Long productId, String search) {
        Seller seller = sellerRepository.findById(me.subjectId())
                .orElseThrow(() -> new RuntimeException("Seller not registered"));

        Pageable pageable = PageRequest.of(page, size);
        String statusFilter = (status != null && !status.isBlank()) ? status : "";
        String searchFilter = (search != null && !search.isBlank()) ? search.trim() : "";

        Page<Order> orders = orderRepository.searchOwnerOrders(
                seller, statusFilter, productId, searchFilter, pageable);

        return ResponseEntity.ok(Map.of(
                "content", orders.getContent(),
                "currentPage", orders.getNumber(),
                "totalItems", orders.getTotalElements(),
                "totalPages", orders.getTotalPages()));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getPublicTracking(String publicToken) {
        return orderRepository.findByPublicToken(publicToken)
                .<ResponseEntity<?>>map(o -> ResponseEntity.ok(buildTrackingPayload(o)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Order not found")));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getPublicTrackingByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Order ID is required"));
        }
        return orderRepository.findByOrderNoIgnoreCase(orderNo.trim())
                .<ResponseEntity<?>>map(o -> ResponseEntity.ok(buildTrackingPayload(o)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No order found with that ID")));
    }

    private Map<String, Object> buildTrackingPayload(Order o) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderNo", o.getOrderNo());
        body.put("customerName", o.getCustomerName());
        body.put("orderStatus", o.getOrderStatus());
        body.put("paymentStatus", o.getPaymentStatus());
        body.put("quantity", o.getQuantity());
        body.put("createdAt", o.getCreatedAt());
        body.put("shippingVendor", o.getShippingVendor());
        body.put("trackingId", o.getTrackingId());

        if (o.getShippingVendor() != null && o.getTrackingId() != null
                && !o.getTrackingId().isBlank()) {
            String url = shippingVendorService.resolveTrackingUrl(o.getShippingVendor(), o.getTrackingId());
            body.put("trackingUrl", url);
        }

        if (o.getProduct() != null) {
            Map<String, Object> product = new LinkedHashMap<>();
            product.put("id", o.getProduct().getId());
            product.put("name", o.getProduct().getName());
            body.put("product", product);
        }

        paymentRepository.findFirstByOrderIdOrderByIdDesc(o.getOrderNo())
                .filter(p -> p.getRefundedAt() != null
                        || (p.getRefundId() != null && !p.getRefundId().isBlank()))
                .ifPresent(p -> {
                    Map<String, Object> refund = new LinkedHashMap<>();
                    refund.put("refundId", p.getRefundId());
                    refund.put("refundAmount", p.getRefundAmount());
                    refund.put("refundedAt", p.getRefundedAt());
                    refund.put("refundReason", p.getRefundReason());
                    refund.put("paymentType", p.getPaymentType());
                    refund.put("hasProof",
                            p.getRefundProofUrl() != null && !p.getRefundProofUrl().isBlank());
                    body.put("refund", refund);
                });
        return body;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getPublicRefundProof(String publicToken, String orderNo) {
        Optional<Order> orderOpt;
        if (publicToken != null && !publicToken.isBlank()) {
            orderOpt = orderRepository.findByPublicToken(publicToken);
        } else if (orderNo != null && !orderNo.isBlank()) {
            orderOpt = orderRepository.findByOrderNoIgnoreCase(orderNo.trim());
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "Lookup key is required"));
        }
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Order not found"));
        }
        Order order = orderOpt.get();

        Optional<Payment> paymentOpt = paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getOrderNo())
                .filter(p -> p.getRefundProofUrl() != null && !p.getRefundProofUrl().isBlank());
        if (paymentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No refund proof available"));
        }

        String storedUrl = paymentOpt.get().getRefundProofUrl();
        Path uploadRoot = Paths.get("uploads/payment").toAbsolutePath().normalize();
        Path filePath = Paths.get("uploads/payment",
                        storedUrl.substring(storedUrl.lastIndexOf('/') + 1))
                .toAbsolutePath().normalize();
        if (!filePath.startsWith(uploadRoot) || !Files.exists(filePath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Refund proof file is missing"));
        }

        String contentType;
        try {
            contentType = Files.probeContentType(filePath);
        } catch (Exception ignored) {
            contentType = null;
        }
        if (contentType == null) {
            contentType = URLConnection.guessContentTypeFromName(filePath.getFileName().toString());
        }
        if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;

        Resource body = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .body(body);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getOrderById(AuthenticatedPrincipal me, Long id) {
        return orderRepository.findByIdAndSeller_Id(id, me.subjectId())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Order not found")));
    }

    @Transactional
    public ResponseEntity<?> updateOrder(AuthenticatedPrincipal me, Long id, OrderUpdateRequest body) {
        Optional<Order> found = orderRepository.findByIdAndSeller_Id(id, me.subjectId());
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Order not found"));
        }
        Order order = found.get();
        String previousOrderStatus = order.getOrderStatus();

        // REFUND must go through the dedicated refund flow.
        if ("REFUND".equalsIgnoreCase(body.getPaymentStatus())
                && !"REFUND".equalsIgnoreCase(order.getPaymentStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Refund must go through the refund flow",
                            "code", "REFUND_FLOW_REQUIRED"));
        }

        String requestedOrderStatus = body.getOrderStatus() == null ? "" : body.getOrderStatus().toUpperCase();
        boolean enteringRefundState =
                ("CANCELLED".equals(requestedOrderStatus) && !"CANCELLED".equalsIgnoreCase(order.getOrderStatus()))
                || ("DELIVERY_FAILED".equals(requestedOrderStatus) && !"DELIVERY_FAILED".equalsIgnoreCase(order.getOrderStatus()));
        if (enteringRefundState) {
            String currentPay = order.getPaymentStatus() == null ? "" : order.getPaymentStatus().toUpperCase();
            if ("PAID".equals(currentPay)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Process refund before "
                                        + ("CANCELLED".equals(requestedOrderStatus)
                                                ? "cancelling" : "marking delivery failed for")
                                        + " a paid order",
                                "code", "REFUND_REQUIRED"));
            }
        }

        if (body.getCustomerName() != null) order.setCustomerName(body.getCustomerName().trim());
        if (body.getPhoneNumber() != null && !body.getPhoneNumber().isEmpty()) {
            order.setPhoneNumber(body.getPhoneNumber().trim());
        }
        if (body.getAddress() != null) order.setAddress(body.getAddress().trim());
        if (body.getComments() != null) order.setComments(body.getComments());
        if (body.getOrderStatus() != null) order.setOrderStatus(body.getOrderStatus());
        if (body.getPaymentStatus() != null) {
            order.setPaymentStatus(body.getPaymentStatus());
            syncPaymentStatus(order.getOrderNo(), body.getPaymentStatus());
        }
        if (body.getShippingVendor() != null) {
            String v = body.getShippingVendor().trim();
            order.setShippingVendor(v.isEmpty() ? null : v);
        }
        if (body.getTrackingId() != null) {
            String tid = body.getTrackingId().trim();
            order.setTrackingId(tid.isEmpty() ? null : tid);
        }

        orderRepository.save(order);

        boolean nowCancelled = "CANCELLED".equalsIgnoreCase(order.getOrderStatus())
                && !"CANCELLED".equalsIgnoreCase(previousOrderStatus);
        if (nowCancelled) {
            orderNotificationService.notifyOrderCancelled(order);
        }
        return ResponseEntity.ok(order);
    }

    private void syncPaymentStatus(String orderNo, String orderPaymentStatus) {
        if (orderNo == null || orderPaymentStatus == null) return;
        Optional<Payment> latest = paymentRepository.findFirstByOrderIdOrderByIdDesc(orderNo);
        if (latest.isEmpty()) return;

        String mapped = switch (orderPaymentStatus.toUpperCase()) {
            case "PAID" -> "SUCCESS";
            case "FAILED" -> "FAILED";
            case "REFUND" -> "REFUNDED";
            case "INITIATED" -> "PENDING";
            default -> null;
        };
        if (mapped == null) return;

        Payment p = latest.get();
        if (!mapped.equals(p.getStatus())) {
            p.setStatus(mapped);
            paymentRepository.save(p);
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> listPublicOrders(int page, int size,
                                              String status, Long productId,
                                              String search, String phoneNumber) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders;
        if (status != null && productId != null) {
            orders = orderRepository.findByPhoneNumberAndStatusAndProductId(
                    phoneNumber, status, productId, pageable);
        } else if (status != null) {
            orders = orderRepository.findByPhoneNumberAndStatus(phoneNumber, status, pageable);
        } else if (productId != null) {
            orders = orderRepository.findByPhoneNumberAndProductId(phoneNumber, productId, pageable);
        } else {
            orders = orderRepository.findByPhoneNumber(phoneNumber, pageable);
        }

        return ResponseEntity.ok(Map.of(
                "content", orders.getContent(),
                "currentPage", orders.getNumber(),
                "totalItems", orders.getTotalElements(),
                "totalPages", orders.getTotalPages()));
    }
}
