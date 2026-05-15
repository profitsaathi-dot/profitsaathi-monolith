package com.profitsaathi.seller.payment;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.profitsaathi.auth.AuthenticatedPrincipal;
import com.profitsaathi.seller.order.Order;
import com.profitsaathi.seller.order.OrderRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final FileStorageService fileStorageService;
    private final RazorpayService razorpayService;
    private final RefundService refundService;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "orderId", required = false) String orderId,
                                    @RequestParam(value = "paymentType", required = false) String paymentType,
                                    @RequestParam(value = "amount", required = false) Double amount) throws Exception {
        String url = fileStorageService.saveFile(file);

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(amount);
        payment.setPaymentType(paymentType);
        payment.setProofImageUrl(url);
        payment.setStatus("PENDING");
        Payment saved = paymentRepository.save(payment);

        if (orderId != null && !orderId.isBlank()) {
            orderRepository.findByOrderNo(orderId).ifPresent(order -> {
                order.setPaymentStatus("PENDING_REVIEW");
                orderRepository.save(order);
            });
        }

        return ResponseEntity.ok(Map.of(
                "url", url,
                "paymentId", saved.getId(),
                "status", saved.getStatus()));
    }

    @PostMapping("/order/{orderId}/refund")
    public ResponseEntity<?> refund(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                    @PathVariable Long orderId,
                                    @RequestBody(required = false) RefundRequest body) {
        return refundService.initiateRefund(me, orderId, body == null ? new RefundRequest() : body);
    }

    @PostMapping("/order/{orderId}/refund/proof")
    public ResponseEntity<?> uploadRefundProof(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                               @PathVariable Long orderId,
                                               @RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "amount", required = false) Double amount,
                                               @RequestParam(value = "reason", required = false) String reason,
                                               @RequestParam(value = "finalOrderStatus", required = false) String finalOrderStatus) throws Exception {
        return refundService.uploadProof(me, orderId, file, amount, reason, finalOrderStatus);
    }

    @GetMapping("/order-id/{orderId}")
    public ResponseEntity<?> getByOrderId(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                          @PathVariable Long orderId) {
        Optional<Order> orderOpt = orderRepository.findByIdAndSeller_Id(orderId, me.subjectId());
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Order not found"));
        }
        return paymentRepository.findFirstByOrderIdOrderByIdDesc(orderOpt.get().getOrderNo())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("message", "No payment uploaded")));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody PaymentVerifyRequest request) {
        try {
            return ResponseEntity.ok(razorpayService.verifyAndSave(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/qr")
    public void generateQR(@RequestParam BigDecimal amount, HttpServletResponse response) throws Exception {
        String upiId = "ashwinachu9525@okicici";
        String name = URLEncoder.encode("Gemini Shop", StandardCharsets.UTF_8);

        String upiUrl = "upi://pay?pa=" + upiId
                + "&pn=" + name
                + "&am=" + amount
                + "&cu=INR";

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(upiUrl, BarcodeFormat.QR_CODE, 300, 300);

        response.setContentType("image/png");
        OutputStream os = response.getOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", os);
    }
}
