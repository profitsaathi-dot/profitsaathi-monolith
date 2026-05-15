package com.profitsaathi.otp;

import com.profitsaathi.customer.user.Customer;
import com.profitsaathi.customer.user.CustomerRepository;
import com.profitsaathi.notification.email.EmailRequest;
import com.profitsaathi.notification.email.EmailService;
import com.profitsaathi.notification.whatsapp.WhatsAppMessage;
import com.profitsaathi.notification.whatsapp.WhatsAppService;
import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-process OTP service. Replaces the old Kafka producers — sends email/WhatsApp
 * directly via {@link EmailService} / {@link WhatsAppService}, both of which
 * are themselves async + retryable.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int MAX_ATTEMPTS = 3;
    private static final int EXPIRY_MINUTES = 5;

    private final OtpRepository repository;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final SellerRepository sellerRepository;
    private final CustomerRepository customerRepository;

    @Transactional
    public void sendEmailOtp(List<String> emails, List<String> cc, String name) {
        String primaryEmail = emails.get(0);
        invalidatePreviousOtps(primaryEmail, true);

        String otp = generateOtp();
        saveOtpEntity(primaryEmail, null, otp);

        emailService.send(EmailRequest.builder()
                .to(emails)
                .cc(cc)
                .subject("Your Secure OTP Code")
                .templateName("otp")
                .variables(Map.of(
                        "name", name == null ? "" : name,
                        "otp", otp,
                        "expiry", EXPIRY_MINUTES))
                .build());
    }

    @Transactional
    public void sendWhatsAppOtp(String chatId, String session) {
        invalidatePreviousOtps(chatId, false);

        String otp = generateOtp();
        saveOtpEntity(null, chatId, otp);

        whatsAppService.send(WhatsAppMessage.builder()
                .session(session)
                .chatId(chatId)
                .otp(otp)
                .expiry(EXPIRY_MINUTES)
                .build());
    }

    @Transactional
    public void verifyOtp(String identifier, String enteredOtp, boolean isEmail) {
        Optional<OtpEntity> opt = isEmail
                ? repository.findTopByEmailOrderByIdDesc(identifier)
                : repository.findTopByMobilenumberOrderByIdDesc(identifier);

        OtpEntity entity = opt.orElseThrow(() ->
                new OtpVerificationException("No OTP found for this identifier."));

        if (entity.isVerified()) {
            throw new OtpVerificationException("OTP has already been used.");
        }
        if (LocalDateTime.now().isAfter(entity.getExpiryTime())) {
            throw new OtpVerificationException("OTP has expired. Please request a new one.");
        }
        if (entity.getAttempts() >= MAX_ATTEMPTS) {
            throw new OtpVerificationException("Maximum verification attempts exceeded. Request a new OTP.");
        }
        if (!entity.getOtp().equals(enteredOtp)) {
            entity.setAttempts(entity.getAttempts() + 1);
            repository.save(entity);
            throw new OtpVerificationException(
                    "Invalid OTP. Attempts remaining: " + (MAX_ATTEMPTS - entity.getAttempts()));
        }

        entity.setVerified(true);
        repository.save(entity);

        if (isEmail) markEmailVerified(identifier);
    }

    /**
     * Email verification stamp — try seller first, then customer. Either way the
     * action is idempotent and atomic with the OTP-success row in the same tx.
     */
    private void markEmailVerified(String email) {
        Optional<Seller> seller = sellerRepository.findByEmail(email);
        if (seller.isPresent()) {
            Seller s = seller.get();
            if (s.getEmailVerifiedAt() == null) {
                s.setEmailVerifiedAt(LocalDateTime.now());
                sellerRepository.save(s);
            }
            return;
        }
        // Customer side has no email_verified_at column today — nothing to stamp.
        Optional<Customer> customer = customerRepository.findByEmail(email);
        customer.ifPresent(c -> {
            // Placeholder: extend Customer with emailVerifiedAt if/when the buyer
            // app needs the same gating semantics as the seller app.
        });
    }

    private static String generateOtp() {
        return String.valueOf(new SecureRandom().nextInt(900000) + 100000);
    }

    private void saveOtpEntity(String email, String mobileNumber, String otp) {
        OtpEntity entity = new OtpEntity();
        entity.setEmail(email != null ? email : "");
        entity.setMobilenumber(mobileNumber != null ? mobileNumber : "");
        entity.setOtp(otp);
        entity.setExpiryTime(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES));
        entity.setAttempts(0);
        entity.setVerified(false);
        repository.save(entity);
    }

    private void invalidatePreviousOtps(String identifier, boolean isEmail) {
        Optional<OtpEntity> existing = isEmail
                ? repository.findTopByEmailOrderByIdDesc(identifier)
                : repository.findTopByMobilenumberOrderByIdDesc(identifier);

        existing.ifPresent(entity -> {
            if (!entity.isVerified() && LocalDateTime.now().isBefore(entity.getExpiryTime())) {
                entity.setExpiryTime(LocalDateTime.now());
                repository.save(entity);
            }
        });
    }
}
