package com.profitsaathi.customer.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByCustomerId(Long customerId);

    /** Distinct contact numbers across the customer's saved addresses — used by /api/v1/customer/numbers. */
    @Query("SELECT DISTINCT a.contactNumber FROM CustomerAddress a WHERE a.customer.id = :customerId")
    List<Long> findPhoneNumbersByCustomerId(@Param("customerId") Long customerId);
}
