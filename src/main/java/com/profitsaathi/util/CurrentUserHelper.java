package com.profitsaathi.util;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import com.profitsaathi.customer.user.Customer;
import com.profitsaathi.customer.user.CustomerRepository;
import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Equivalent of the old {@code Helper.getCurrentUser()} — resolves the
 * authenticated principal back to a Seller or Customer entity. Inject and
 * call from anywhere a service needs the persistent identity (most
 * controllers can use {@code @AuthenticationPrincipal AuthenticatedPrincipal}
 * directly and avoid this).
 */
@Service
@RequiredArgsConstructor
public class CurrentUserHelper {

    private final SellerRepository sellerRepository;
    private final CustomerRepository customerRepository;

    public AuthenticatedPrincipal currentPrincipal() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) {
            throw new IllegalStateException("No authenticated principal in security context");
        }
        Object p = a.getPrincipal();
        if (p instanceof AuthenticatedPrincipal ap) return ap;
        throw new IllegalStateException("Principal is not an AuthenticatedPrincipal: " + p);
    }

    public Seller currentSeller() {
        AuthenticatedPrincipal me = currentPrincipal();
        if (!me.isSeller()) {
            throw new IllegalStateException("Caller is not a seller (role=" + me.role() + ")");
        }
        return sellerRepository.findById(me.subjectId())
                .orElseThrow(() -> new EntityNotFoundException("Seller not found: " + me.subjectId()));
    }

    public Customer currentCustomer() {
        AuthenticatedPrincipal me = currentPrincipal();
        if (!me.isCustomer()) {
            throw new IllegalStateException("Caller is not a customer (role=" + me.role() + ")");
        }
        return customerRepository.findById(me.subjectId())
                .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + me.subjectId()));
    }
}
