package com.profitsaathi.customer.user;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Replaces the old {@code UserController} from the buyer app. JWT principal
 * supplies identity — no Keycloak round-trip.
 */
@RestController
@RequestMapping("/api/v1/customer")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;

    @Transactional(readOnly = true)
    @GetMapping("/me")
    public ResponseEntity<Customer> me(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        return ResponseEntity.of(customerRepository.findById(me.subjectId()));
    }

    /**
     * Bulk update — replaces top-level fields and (if {@code addresses} is
     * non-null) the entire address list. Mirrors the old buyer-app's
     * {@code PUT /api/v1/user/update} endpoint.
     */
    @Transactional
    @PutMapping("/update")
    public Customer bulkUpdate(@AuthenticationPrincipal AuthenticatedPrincipal me,
                               @Valid @RequestBody CustomerUpdateRequest req) {
        Customer c = customerRepository.findById(me.subjectId())
                .orElseThrow(() -> new IllegalStateException("Customer not found"));

        if (req.getName() != null)          c.setName(req.getName());
        if (req.getEmail() != null)         c.setEmail(req.getEmail());
        if (req.getNotifications() != null) c.setNotifications(req.getNotifications());
        if (req.getLanguage() != null)      c.setLanguage(req.getLanguage());

        if (req.getAddresses() != null) {
            if (c.getAddresses() == null) c.setAddresses(new ArrayList<>());
            // orphanRemoval=true on the @OneToMany deletes the cleared rows.
            c.getAddresses().clear();

            List<CustomerAddress> rebuilt = req.getAddresses().stream().map(dto -> {
                CustomerAddress a = new CustomerAddress();
                a.setStreet(dto.getStreet());
                a.setCity(dto.getCity());
                a.setState(dto.getState());
                a.setZip(dto.getZip());
                a.setIsDefault(dto.getIsDefault());
                a.setCustomer(c);
                return a;
            }).collect(Collectors.toList());

            c.getAddresses().addAll(rebuilt);
        }
        return customerRepository.save(c);
    }

    @Transactional(readOnly = true)
    @GetMapping("/numbers")
    public ResponseEntity<List<Long>> getNumbers(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        return ResponseEntity.ok(addressRepository.findPhoneNumbersByCustomerId(me.subjectId()));
    }

    @Transactional(readOnly = true)
    @GetMapping("/addresses")
    public List<CustomerAddress> addresses(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        return addressRepository.findByCustomerId(me.subjectId());
    }

    @Transactional
    @PostMapping("/addresses")
    public CustomerAddress addAddress(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                      @Valid @RequestBody CustomerAddress address) {
        Customer c = customerRepository.findById(me.subjectId())
                .orElseThrow(() -> new IllegalStateException("Customer not found"));
        address.setCustomer(c);
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            resetDefault(c.getId());
        }
        return addressRepository.save(address);
    }

    @Transactional
    @PutMapping("/addresses/{id}")
    public CustomerAddress updateAddress(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                         @PathVariable Long id,
                                         @Valid @RequestBody CustomerAddress patch) {
        CustomerAddress existing = addressRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Address not found"));
        if (existing.getCustomer() == null
                || !existing.getCustomer().getId().equals(me.subjectId())) {
            throw new IllegalStateException("Not your address");
        }
        existing.setStreet(patch.getStreet());
        existing.setCity(patch.getCity());
        existing.setState(patch.getState());
        existing.setZip(patch.getZip());
        existing.setContactNumber(patch.getContactNumber());
        existing.setAlternativeContactNumber(patch.getAlternativeContactNumber());
        if (Boolean.TRUE.equals(patch.getIsDefault())) {
            resetDefault(me.subjectId());
            existing.setIsDefault(true);
        } else {
            existing.setIsDefault(patch.getIsDefault());
        }
        return addressRepository.save(existing);
    }

    @Transactional
    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                              @PathVariable Long id) {
        CustomerAddress existing = addressRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Address not found"));
        if (existing.getCustomer() == null
                || !existing.getCustomer().getId().equals(me.subjectId())) {
            throw new IllegalStateException("Not your address");
        }
        addressRepository.delete(existing);
        return ResponseEntity.noContent().build();
    }

    private void resetDefault(Long customerId) {
        List<CustomerAddress> all = addressRepository.findByCustomerId(customerId);
        for (CustomerAddress a : all) {
            if (Boolean.TRUE.equals(a.getIsDefault())) {
                a.setIsDefault(false);
            }
        }
        addressRepository.saveAll(all);
    }
}
