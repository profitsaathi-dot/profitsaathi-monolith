package com.profitsaathi.admin;

import com.profitsaathi.customer.user.Customer;
import com.profitsaathi.customer.user.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin-facing customer operations. Mirrors {@link AdminSellerService} —
 * profile fields only; auth credentials, addresses, and orders stay with
 * their respective owning surfaces.
 */
@Service
@RequiredArgsConstructor
public class AdminCustomerService {

    private final CustomerRepository customerRepository;

    public List<Customer> list(Customer.Status status) {
        return status == null ? customerRepository.findAll() : customerRepository.findByStatus(status);
    }

    public Customer get(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + id));
    }

    @Transactional
    public Customer update(Long id, AdminCustomerUpdateRequest req) {
        Customer customer = get(id);
        if (req.getName() != null)          customer.setName(req.getName().trim());
        if (req.getLanguage() != null)      customer.setLanguage(req.getLanguage());
        if (req.getNotifications() != null) customer.setNotifications(req.getNotifications());
        if (req.getStatus() != null)        customer.setStatus(req.getStatus());
        return customerRepository.save(customer);
    }
}
