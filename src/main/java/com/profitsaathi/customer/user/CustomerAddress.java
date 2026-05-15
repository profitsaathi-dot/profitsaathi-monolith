package com.profitsaathi.customer.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "customer_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String street;
    private String city;
    private String state;
    private String zip;
    private Long contactNumber;
    private Long alternativeContactNumber;

    @Builder.Default
    private Boolean isDefault = Boolean.FALSE;

    // EAGER: getCustomerInfo() reads customer.id/name on every JSON write,
    // and updateAddress/deleteAddress also touch existing.getCustomer().getId()
    // outside any explicit transaction. open-in-view=false makes both paths
    // hard-fail with LazyInitializationException unless customer is loaded
    // up-front.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    @JsonIgnore
    private Customer customer;

    @Transient
    @JsonProperty("customer")
    public Map<String, Object> getCustomerInfo() {
        if (customer == null) return null;
        Map<String, Object> map = new HashMap<>();
        map.put("id", customer.getId());
        map.put("name", customer.getName());
        return map;
    }
}
