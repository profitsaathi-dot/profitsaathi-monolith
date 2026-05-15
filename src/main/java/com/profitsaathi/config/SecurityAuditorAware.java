package com.profitsaathi.config;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("securityAuditorAware")
public class SecurityAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return Optional.of("system");
        Object principal = a.getPrincipal();
        if (principal instanceof AuthenticatedPrincipal p) {
            return Optional.of(p.email() != null ? p.email() : String.valueOf(p.credentialsId()));
        }
        return Optional.of(a.getName());
    }
}
