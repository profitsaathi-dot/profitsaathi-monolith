package com.profitsaathi.seller.order;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/order")
@AllArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<?> add(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                 @RequestBody OrderRequest order) {
        return orderService.createOrder(me, order);
    }

    @PostMapping("/owner")
    public ResponseEntity<?> owner(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                   @RequestBody OrderRequest order) {
        return orderService.createOrder(me, order);
    }

    @GetMapping
    public ResponseEntity<?> get(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) Long productId,
                                 @RequestParam(required = false) String search) {
        return orderService.listOwnerOrders(me, page, size, status, productId, search);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                     @PathVariable Long id) {
        return orderService.getOrderById(me, id);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                    @PathVariable Long id,
                                    @Valid @RequestBody OrderUpdateRequest body) {
        return orderService.updateOrder(me, id, body);
    }

    @GetMapping("/public")
    public ResponseEntity<?> getPublic(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) Long productId,
                                       @RequestParam(required = false) String search,
                                       @RequestParam String number) {
        if (number == null || number.trim().isEmpty()) {
            throw new IllegalArgumentException("Number cannot be empty");
        }
        return orderService.listPublicOrders(page, size, status, productId, search, number);
    }

    @GetMapping("/track/{publicToken}")
    public ResponseEntity<?> publicTrack(@PathVariable String publicToken) {
        return orderService.getPublicTracking(publicToken);
    }

    @GetMapping("/track-by-no/{orderNo}")
    public ResponseEntity<?> publicTrackByOrderNo(@PathVariable String orderNo) {
        return orderService.getPublicTrackingByOrderNo(orderNo);
    }

    @GetMapping("/track/{publicToken}/refund-proof")
    public ResponseEntity<?> publicRefundProofByToken(@PathVariable String publicToken) {
        return orderService.getPublicRefundProof(publicToken, null);
    }

    @GetMapping("/track-by-no/{orderNo}/refund-proof")
    public ResponseEntity<?> publicRefundProofByOrderNo(@PathVariable String orderNo) {
        return orderService.getPublicRefundProof(null, orderNo);
    }
}
