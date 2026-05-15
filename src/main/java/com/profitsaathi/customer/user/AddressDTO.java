package com.profitsaathi.customer.user;

import lombok.Data;

@Data
public class AddressDTO {
    private Long id;
    private String street;
    private String city;
    private String state;
    private String zip;
    private Boolean isDefault;
}
