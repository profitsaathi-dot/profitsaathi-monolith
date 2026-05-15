package com.profitsaathi.admin;

import com.profitsaathi.auth.Credentials;
import com.profitsaathi.auth.CredentialsRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin-managing-admins service. Cross-checks the {@code credentials} table
 * before status changes so deactivating an admin also disables the auth
 * row — otherwise the deactivated admin could still log in.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminRepository adminRepository;
    private final CredentialsRepository credentialsRepository;

    public List<Admin> list(Admin.Status status) {
        return status == null ? adminRepository.findAll() : adminRepository.findByStatus(status);
    }

    public Admin get(Long id) {
        return adminRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Admin not found: " + id));
    }

    @Transactional
    public Admin update(Long id, AdminUserUpdateRequest req) {
        Admin admin = get(id);
        if (req.getName() != null)   admin.setName(req.getName().trim());
        if (req.getStatus() != null) {
            admin.setStatus(req.getStatus());
            // Keep credentials.enabled in sync — a SUSPENDED/INACTIVE/BLOCKED
            // admin should fail at login, not just be hidden in the UI.
            credentialsRepository.findByEmail(admin.getEmail()).ifPresent(c -> {
                boolean enabled = req.getStatus() == Admin.Status.ACTIVE;
                if (c.isEnabled() != enabled) {
                    c.setEnabled(enabled);
                    credentialsRepository.save(c);
                }
            });
        }
        return adminRepository.save(admin);
    }
}
