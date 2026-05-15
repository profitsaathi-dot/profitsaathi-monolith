package com.profitsaathi.seller.payment;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import com.profitsaathi.seller.order.Order;
import com.profitsaathi.seller.order.OrderNotificationService;
import com.profitsaathi.seller.order.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentAuditLogRepository auditRepository;
    private final FileStorageService fileStorageService;
    private final RazorpayConfig razorpayConfig;
    private final OrderNotificationService orderNotificationService;

    private static final String ACTION_INITIATED = "CANCEL_REFUND_INITIATED";
    private static final String ACTION_RZP_OK    = "RAZORPAY_REFUND_SUCCESS";
    private static final String ACTION_RZP_FAIL  = "RAZORPAY_REFUND_FAILED";
    private static final String ACTION_PROOF     = "REFUND_PROOF_UPLOADED";
    private static final String ACTION_BLOCKED   = "REFUND_GUARD_BLOCKED";

    @Transactional
    public ResponseEntity<?> initiateRefund(AuthenticatedPrincipal me, Long orderId, RefundRequest req) {
        Optional<Order> orderOpt = orderRepository.findByIdAndSeller_Id(orderId, me.subjectId());
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Order not found"));
        }
        Order order = orderOpt.get();

        Optional<Payment> latest = paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getOrderNo());
        if (latest.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "requiresProof", false,
                    "alreadyRefunded", false,
                    "noPayment", true,
                    "message", "No payment recorded — cancel without refund"));
        }
        Payment payment = latest.get();

        if ("REFUNDED".equalsIgnoreCase(payment.getStatus()) || payment.getRefundedAt() != null) {
            audit(order.getOrderNo(), payment.getId(), ACTION_BLOCKED, me.email(),
                    "Refund blocked: already refunded at " + payment.getRefundedAt());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message", "Payment already refunded",
                            "alreadyRefunded", true,
                            "refundId", payment.getRefundId(),
                            "refundedAt", payment.getRefundedAt()));
        }

        if (!"SUCCESS".equalsIgnoreCase(payment.getStatus())
                && !"PAID".equalsIgnoreCase(payment.getStatus())) {
            audit(order.getOrderNo(), payment.getId(), ACTION_BLOCKED, me.email(),
                    "Refund blocked: payment status is " + payment.getStatus());
            return ResponseEntity.ok(Map.of(
                    "requiresProof", false,
                    "alreadyRefunded", false,
                    "noPayment", true,
                    "message", "Payment was never collected — cancel without refund"));
        }

        double maxAmount = payment.getAmount() != null ? payment.getAmount() : 0d;
        double amount = req.getAmount() != null ? req.getAmount() : maxAmount;
        if (amount <= 0 || amount > maxAmount) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Refund amount must be between 0 and " + maxAmount));
        }

        audit(order.getOrderNo(), payment.getId(), ACTION_INITIATED, me.email(),
                "amount=" + amount + ", reason=" + safe(req.getReason())
                        + ", paymentType=" + payment.getPaymentType());

        String finalStatus = resolveFinalOrderStatus(req.getFinalOrderStatus());
        String type = payment.getPaymentType() == null ? "" : payment.getPaymentType().toUpperCase();
        if ("ONLINE".equals(type)) {
            return processRazorpayRefund(order, payment, amount, req.getReason(), finalStatus, me.email());
        }

        return ResponseEntity.ok(Map.of(
                "requiresProof", true,
                "alreadyRefunded", false,
                "paymentId", payment.getId(),
                "paymentType", payment.getPaymentType(),
                "amount", amount,
                "message", "Upload a refund proof to finalize"));
    }

    private String resolveFinalOrderStatus(String requested) {
        if (requested == null) return "CANCELLED";
        String upper = requested.toUpperCase();
        return ("DELIVERY_FAILED".equals(upper) || "CANCELLED".equals(upper))
                ? upper : "CANCELLED";
    }

    @Transactional
    public ResponseEntity<?> uploadProof(AuthenticatedPrincipal me,
                                         Long orderId,
                                         MultipartFile file,
                                         Double amount,
                                         String reason,
                                         String finalOrderStatus) throws IOException {
        Optional<Order> orderOpt = orderRepository.findByIdAndSeller_Id(orderId, me.subjectId());
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Order not found"));
        }
        Order order = orderOpt.get();

        Optional<Payment> latest = paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getOrderNo());
        if (latest.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No payment to refund"));
        }
        Payment payment = latest.get();

        if ("REFUNDED".equalsIgnoreCase(payment.getStatus()) || payment.getRefundedAt() != null) {
            audit(order.getOrderNo(), payment.getId(), ACTION_BLOCKED, me.email(),
                    "Proof upload blocked: already refunded");
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Payment already refunded", "alreadyRefunded", true));
        }

        String url = fileStorageService.saveFile(file);
        double maxAmount = payment.getAmount() != null ? payment.getAmount() : 0d;
        double effective = amount != null && amount > 0 && amount <= maxAmount ? amount : maxAmount;

        payment.setRefundProofUrl(url);
        payment.setRefundAmount(effective);
        payment.setRefundReason(reason);
        payment.setStatus("REFUNDED");
        payment.setRefundedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        String finalStatus = resolveFinalOrderStatus(finalOrderStatus);
        order.setPaymentStatus("REFUND");
        order.setOrderStatus(finalStatus);
        orderRepository.save(order);

        audit(order.getOrderNo(), payment.getId(), ACTION_PROOF, me.email(),
                "url=" + url + ", amount=" + effective + ", reason=" + safe(reason)
                        + ", finalOrderStatus=" + finalStatus);

        if ("CANCELLED".equalsIgnoreCase(finalStatus)) {
            orderNotificationService.notifyOrderCancelled(order);
        }

        return ResponseEntity.ok(Map.of(
                "refundProofUrl", url,
                "refundAmount", effective,
                "status", payment.getStatus()));
    }

    private ResponseEntity<?> processRazorpayRefund(Order order,
                                                    Payment payment,
                                                    double amount,
                                                    String reason,
                                                    String finalOrderStatus,
                                                    String actorEmail) {
        if (!razorpayConfig.isConfigured()) {
            audit(order.getOrderNo(), payment.getId(), ACTION_RZP_FAIL, actorEmail,
                    "Razorpay credentials not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Razorpay is not configured on the server"));
        }
        String rzpPaymentId = payment.getRazorpayPaymentId();
        if (rzpPaymentId == null || rzpPaymentId.isBlank()) {
            audit(order.getOrderNo(), payment.getId(), ACTION_RZP_FAIL, actorEmail,
                    "No razorpayPaymentId on the payment row");
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Razorpay payment id missing — cannot auto-refund"));
        }

        long amountPaise = Math.round(amount * 100d);
        String body = "{\"amount\":" + amountPaise + ",\"speed\":\"normal\"}";
        String basic = Base64.getEncoder().encodeToString(
                (razorpayConfig.getKeyId() + ":" + razorpayConfig.getKeySecret())
                        .getBytes(StandardCharsets.UTF_8));

        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.razorpay.com/v1/payments/" + rzpPaymentId + "/refund"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        try {
            HttpResponse<String> resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            String respBody = resp.body();

            if (sc < 200 || sc >= 300) {
                audit(order.getOrderNo(), payment.getId(), ACTION_RZP_FAIL, actorEmail,
                        "HTTP " + sc + " — " + truncate(respBody, 1500));
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of(
                                "message", "Razorpay refund failed",
                                "status", sc,
                                "details", truncate(respBody, 800)));
            }

            String refundId = extractJsonString(respBody, "id");
            payment.setRefundId(refundId);
            payment.setRefundAmount(amount);
            payment.setRefundReason(reason);
            payment.setStatus("REFUNDED");
            payment.setRefundedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            order.setPaymentStatus("REFUND");
            order.setOrderStatus(finalOrderStatus);
            orderRepository.save(order);

            audit(order.getOrderNo(), payment.getId(), ACTION_RZP_OK, actorEmail,
                    "refundId=" + refundId + ", amount=" + amount
                            + ", finalOrderStatus=" + finalOrderStatus);

            if ("CANCELLED".equalsIgnoreCase(finalOrderStatus)) {
                orderNotificationService.notifyOrderCancelled(order);
            }

            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("refundId", refundId);
            ok.put("refundAmount", amount);
            ok.put("status", "REFUNDED");
            return ResponseEntity.ok(ok);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            audit(order.getOrderNo(), payment.getId(), ACTION_RZP_FAIL, actorEmail,
                    "Network error: " + e.getClass().getSimpleName() + " " + safe(e.getMessage()));
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "Razorpay request failed: " + e.getMessage()));
        }
    }

    private void audit(String orderNo, Long paymentId, String action,
                       String actorEmail, String details) {
        PaymentAuditLog log = new PaymentAuditLog();
        log.setOrderNo(orderNo);
        log.setPaymentId(paymentId);
        log.setAction(action);
        log.setActorEmail(actorEmail);
        log.setDetails(truncate(details, 1990));
        log.setCreatedAt(LocalDateTime.now());
        auditRepository.save(log);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String extractJsonString(String body, String key) {
        if (body == null) return null;
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        return m.find() ? m.group(1) : null;
    }
}
