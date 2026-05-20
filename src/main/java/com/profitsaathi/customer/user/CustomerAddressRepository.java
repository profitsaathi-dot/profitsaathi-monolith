package com.profitsaathi.customer.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    /**
     * Find addresses by customer ID with customer data eagerly loaded.
     * Uses JOIN FETCH to avoid N+1 queries.
     */
    @Query("SELECT a FROM CustomerAddress a JOIN FETCH a.customer WHERE a.customer.id = :customerId")
    List<CustomerAddress> findByCustomerId(@Param("customerId") Long customerId);
    
    /**
     * Find address by ID with customer data eagerly loaded.
     * Uses JOIN FETCH to avoid lazy loading issues.
     */
    @Query("SELECT a FROM CustomerAddress a JOIN FETCH a.customer WHERE a.id = :id")
    CustomerAddress findByIdWithCustomer(@Param("id") Long id);

    /** Distinct contact numbers across the customer's saved addresses — used by /api/v1/customer/numbers. */
    @Query("SELECT DISTINCT a.contactNumber FROM CustomerAddress a WHERE a.customer.id = :customerId")
    List<Long> findPhoneNumbersByCustomerId(@Param("customerId") Long customerId);
}
