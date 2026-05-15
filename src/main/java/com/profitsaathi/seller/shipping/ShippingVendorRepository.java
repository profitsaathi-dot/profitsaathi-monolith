package com.profitsaathi.seller.shipping;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShippingVendorRepository extends JpaRepository<ShippingVendor, Long> {
    Optional<ShippingVendor> findByCode(String code);
    List<ShippingVendor> findAllByOrderByNameAsc();
}
