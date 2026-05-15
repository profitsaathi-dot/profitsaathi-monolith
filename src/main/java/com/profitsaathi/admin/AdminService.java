package com.profitsaathi.admin;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin account-management service. Mirrors {@code SellerService.updatePreferences}:
 * identity always comes from the JWT principal's {@code subjectId} (which points
 * at the {@code admins.id} row), never from the request body. Patch semantics —
 * null fields on the request leave the existing column untouched.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminRepository adminRepository;

    @Transactional
    public Admin updatePreferences(Long adminId, AdminPreferencesRequest req) {
        Admin admin = mustFind(adminId);
        if (req.getTheme() != null)          admin.setTheme(req.getTheme());
        if (req.getAccent() != null)         admin.setAccent(req.getAccent());
        if (req.getRegion() != null)         admin.setRegion(req.getRegion());
        if (req.getNotifyEmail() != null)    admin.setNotifyEmail(req.getNotifyEmail());
        if (req.getNotifyWhatsapp() != null) admin.setNotifyWhatsapp(req.getNotifyWhatsapp());
        return adminRepository.save(admin);
    }

    private Admin mustFind(Long adminId) {
        return adminRepository.findById(adminId)
                .orElseThrow(() -> new EntityNotFoundException("Admin not found: " + adminId));
    }
}
