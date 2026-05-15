package com.profitsaathi.seller.store;

import com.profitsaathi.config.ProductConfig;
import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

/**
 * Public store endpoints — no auth required. A customer landing on a shared
 * product link looks the seller up by `publicToken` so they can contact the
 * seller via WhatsApp or pay against the seller's UPI QR.
 */
@RestController
@RequestMapping("/api/v1/store")
@RequiredArgsConstructor
public class StoreController {

    private final SellerRepository sellerRepository;
    private final ProductConfig productConfig;

    @GetMapping("/info")
    public ResponseEntity<?> info(@RequestParam("token") String token) {
        return sellerRepository.findByPublicToken(token)
                .map(this::toResponse)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        Collections.singletonMap("message", "Store not found")));
    }

    /** Alias kept for legacy clients that call /public. */
    @GetMapping("/public")
    public ResponseEntity<?> publicInfo(@RequestParam("token") String token) {
        return info(token);
    }

    @GetMapping("/payment-qr")
    public ResponseEntity<?> paymentQr(@RequestParam("token") String token) {
        try {
            Seller seller = sellerRepository.findByPublicToken(token).orElse(null);
            if (seller == null
                    || seller.getPaymentQRCode() == null
                    || seller.getPaymentQRCode().isBlank()) {
                return ResponseEntity.notFound().build();
            }
            Path path = Paths.get(productConfig.getUploadDir(), seller.getPaymentQRCode());
            if (!Files.exists(path)) return ResponseEntity.notFound().build();

            String contentType = Files.probeContentType(path);
            if (contentType == null || contentType.isBlank()) contentType = "image/png";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .cacheControl(CacheControl.noStore())
                    .body(new InputStreamResource(Files.newInputStream(path)));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private StoreInfoResponse toResponse(Seller seller) {
        boolean isBankAccount = "BANK_ACCOUNT".equalsIgnoreCase(seller.getPaymentType());
        return StoreInfoResponse.builder()
                .name(seller.getName())
                .email(seller.getEmail())
                .sellerType(seller.getSellerType())
                .whatsapp(seller.getMobile())
                .storeName(seller.getStoreName())
                .paymentType(seller.getPaymentType())
                .paymentQRCode(seller.getPaymentQRCode())
                .bankAccountNumber(isBankAccount ? seller.getBankAccountNumber() : null)
                .bankAccountIfsc(isBankAccount ? seller.getBankAccountIfsc() : null)
                .build();
    }
}
