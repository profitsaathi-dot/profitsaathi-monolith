package com.profitsaathi.customer.user;

import lombok.Data;

import java.util.List;

/**
 * Bulk customer profile update — equivalent to the old buyer-app's
 * {@code UserUpdateRequest}. Sending a non-null {@code addresses} list
 * REPLACES all of the customer's saved addresses (orphan removal cleans
 * up the old rows).
 *
 * Password change is intentionally not on this DTO — it goes through the
 * dedicated auth flow ({@code POST /api/v1/auth/change-password}, when added).
 */
@Data
public class CustomerUpdateRequest {
    private String name;
    private String email;
    private Boolean notifications;
    private String language;
    private List<AddressDTO> addresses;
}
