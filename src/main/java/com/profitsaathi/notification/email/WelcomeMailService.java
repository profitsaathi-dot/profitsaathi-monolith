package com.profitsaathi.notification.email;

import com.profitsaathi.customer.user.Customer;
import com.profitsaathi.seller.user.Seller;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Replaces the old Kafka-driven welcome mail flow ({@code MailService} in both
 * apps). Now invoked synchronously from {@code AuthService.signup*} — but the
 * {@link EmailService#send} method is itself {@code @Async}, so the request
 * still returns instantly.
 */
@Service
@RequiredArgsConstructor
public class WelcomeMailService {

    private final EmailService emailService;

    @Value("${profitsaathi.public-api-url}")
    private String publicUrl;

    public void sendWelcomeSeller(Seller seller) {
        emailService.send(EmailRequest.builder()
                .to(List.of(seller.getEmail()))
                .subject("Welcome to ProfitSaathi")
                .templateName("welcome")
                .variables(Map.of(
                        "name", seller.getName() == null ? "Seller" : seller.getName(),
                        "email", seller.getEmail(),
                        "dashboardUrl", publicUrl + "/seller/dashboard"))
                .build());
    }

    public void sendWelcomeCustomer(Customer customer) {
        emailService.send(EmailRequest.builder()
                .to(List.of(customer.getEmail()))
                .subject("Welcome to ProfitSaathi")
                .templateName("welcome")
                .variables(Map.of(
                        "name", customer.getName() == null ? "Customer" : customer.getName(),
                        "email", customer.getEmail(),
                        "dashboardUrl", publicUrl + "/account"))
                .build());
    }
}
