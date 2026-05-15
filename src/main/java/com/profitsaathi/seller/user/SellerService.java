package com.profitsaathi.seller.user;

import com.profitsaathi.config.ProductConfig;
import com.profitsaathi.seller.store.OnboardRequest;
import com.profitsaathi.seller.store.PaymentSettingsRequest;
import com.profitsaathi.seller.store.PreferencesRequest;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Seller account-management service. Owns onboarding, preferences, payment
 * settings, and the seller's UPI QR upload/read flow.
 *
 * Identity is always taken from {@code sellerId} (the JWT subject) — never
 * from the request body. The slugified-publicToken logic from the old
 * seller-app is preserved so existing storefront URLs keep resolving.
 */
@Service
@RequiredArgsConstructor
public class SellerService {

    private final SellerRepository sellerRepository;
    private final ProductConfig productConfig;

    @Transactional
    public Seller onboard(Long sellerId, OnboardRequest req) {
        Seller seller = mustFind(sellerId);

        String trimmedStore = req.getStoreName().trim();
        seller.setStoreName(trimmedStore);
        seller.setSellerType(req.getSellerType());
        if (req.getMobile() != null) {
            seller.setMobile(req.getMobile().trim());
        }
        if (seller.getOnboardedAt() == null) {
            seller.setOnboardedAt(LocalDateTime.now());
        }

        String slug = slugifyStoreName(trimmedStore);
        if (!slug.isEmpty() && !slug.equals(seller.getPublicToken())) {
            seller.setPublicToken(ensureUniquePublicToken(slug, seller.getId()));
        }
        return sellerRepository.save(seller);
    }

    @Transactional
    public Seller updatePreferences(Long sellerId, PreferencesRequest req) {
        Seller seller = mustFind(sellerId);
        if (req.getLanguage() != null) seller.setLanguage(req.getLanguage());
        if (req.getTheme() != null)    seller.setTheme(req.getTheme());
        if (req.getAccent() != null)   seller.setAccent(req.getAccent());
        if (req.getWeeklyReportOptIn() != null) {
            seller.setWeeklyReportOptIn(req.getWeeklyReportOptIn());
        }
        if (req.getMonthlyReportOptIn() != null) {
            seller.setMonthlyReportOptIn(req.getMonthlyReportOptIn());
        }
        return sellerRepository.save(seller);
    }

    @Transactional
    public Seller updatePayment(Long sellerId, PaymentSettingsRequest req) {
        Seller seller = mustFind(sellerId);
        if (req.getPaymentType() != null && !req.getPaymentType().isBlank()) {
            seller.setPaymentType(req.getPaymentType());
        }
        if (req.getPaymentQRCode() != null) {
            seller.setPaymentQRCode(req.getPaymentQRCode().trim());
        }
        if (req.getBankAccountNumber() != null && !req.getBankAccountNumber().isBlank()) {
            seller.setBankAccountNumber(req.getBankAccountNumber().trim());
        }
        if (req.getBankAccountIfsc() != null && !req.getBankAccountIfsc().isBlank()) {
            seller.setBankAccountIfsc(req.getBankAccountIfsc().trim().toUpperCase());
        }
        return sellerRepository.save(seller);
    }

    @Transactional
    public String saveQrCode(Long sellerId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("QR image is required");
        }
        Seller seller = mustFind(sellerId);

        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "qr";
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0 && dot < original.length() - 1) ext = original.substring(dot).toLowerCase();
        if (ext.isEmpty() || ext.length() > 6) {
            String ct = file.getContentType();
            if (ct != null && ct.toLowerCase().contains("png"))       ext = ".png";
            else if (ct != null && ct.toLowerCase().contains("jpeg")) ext = ".jpg";
            else if (ct != null && ct.toLowerCase().contains("jpg"))  ext = ".jpg";
            else                                                       ext = ".png";
        }

        String filename = "qrcodes/" + seller.getId() + "_" + System.currentTimeMillis() + ext;
        Path target = Paths.get(productConfig.getUploadDir(), filename);
        Files.createDirectories(target.getParent());
        Files.write(target, file.getBytes());

        seller.setPaymentQRCode(filename);
        if (seller.getPaymentType() == null || seller.getPaymentType().isBlank()) {
            seller.setPaymentType("UPI_QR");
        }
        sellerRepository.save(seller);
        return filename;
    }

    public Map.Entry<String, InputStreamResource> getMyQrCode(Long sellerId) throws IOException {
        Seller seller = mustFind(sellerId);
        if (seller.getPaymentQRCode() == null || seller.getPaymentQRCode().isBlank()) {
            throw new RuntimeException("No QR code uploaded");
        }
        Path path = Paths.get(productConfig.getUploadDir(), seller.getPaymentQRCode());
        if (!Files.exists(path)) {
            throw new RuntimeException("QR file missing on disk");
        }
        String contentType = Files.probeContentType(path);
        if (contentType == null || contentType.isBlank()) contentType = "image/png";
        return new AbstractMap.SimpleEntry<>(contentType,
                new InputStreamResource(Files.newInputStream(path)));
    }

    @Transactional
    public void deactivate(Long sellerId) {
        Seller seller = mustFind(sellerId);
        seller.setStatus(Seller.Status.INACTIVE);
        sellerRepository.save(seller);
    }

    private Seller mustFind(Long sellerId) {
        return sellerRepository.findById(sellerId)
                .orElseThrow(() -> new EntityNotFoundException("Seller not found: " + sellerId));
    }

    /** "Aanya Bakes" → "Aanya_Bakes". Trims trailing/leading underscores. */
    private static String slugifyStoreName(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", "_").replaceAll("^_+|_+$", "");
    }

    /**
     * Guarantee the slug doesn't collide with another seller's publicToken.
     * "Aanya_Bakes" taken → try "Aanya_Bakes_2", "Aanya_Bakes_3"…
     * {@code selfId} excludes the current seller from the collision check.
     */
    private String ensureUniquePublicToken(String base, Long selfId) {
        String candidate = base;
        int suffix = 2;
        while (true) {
            Optional<Seller> clash = sellerRepository.findByPublicToken(candidate);
            if (clash.isEmpty() || (selfId != null && clash.get().getId().equals(selfId))) {
                return candidate;
            }
            candidate = base + "_" + suffix++;
            if (suffix > 999) {
                // Vanishingly unlikely — fall back to UUID rather than loop forever.
                return UUID.randomUUID().toString().replace("-", "");
            }
        }
    }
}
