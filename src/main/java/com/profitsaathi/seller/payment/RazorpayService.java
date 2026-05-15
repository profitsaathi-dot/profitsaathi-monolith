package com.profitsaathi.seller.payment;

import com.profitsaathi.seller.order.Order;
import com.profitsaathi.seller.order.OrderRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class RazorpayService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final RazorpayConfig config;

    public Payment verifyAndSave(PaymentVerifyRequest req) {
        String data = req.getRazorpayOrderId() + "|" + req.getRazorpayPaymentId();
        String expected = CryptoUtil.hmacSha256(data, config.getKeySecret());
        boolean isValid = expected.equals(req.getRazorpaySignature());

        Payment payment = new Payment();
        payment.setOrderId(req.getOrderId());
        payment.setAmount(req.getAmount());
        payment.setPaymentType("ONLINE");
        payment.setRazorpayOrderId(req.getRazorpayOrderId());
        payment.setRazorpayPaymentId(req.getRazorpayPaymentId());
        payment.setRazorpaySignature(req.getRazorpaySignature());

        if (!isValid) {
            payment.setStatus("FAILED");
            paymentRepository.save(payment);
            throw new RuntimeException("Invalid Razorpay signature");
        }

        payment.setStatus("SUCCESS");
        Payment saved = paymentRepository.save(payment);

        Order order = orderRepository.findByOrderNo(req.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));
        order.setPaymentStatus("PAID");
        order.setOrderStatus("CONFIRMED");
        orderRepository.save(order);

        return saved;
    }
}
