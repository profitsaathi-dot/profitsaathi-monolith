package com.profitsaathi.admin;

import com.profitsaathi.customer.user.Customer;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Admin-side patch for a customer profile. Email + addresses are not
 * exposed — email doubles as the credential identifier and addresses are
 * managed by the customer in their own surface.
 */
@Data
public class AdminCustomerUpdateRequest {

    @Size(max = 255)
    private String name;

    @Pattern(regexp = "en|hi|mr|ta|te|bn|kn|gu|ml",
             message = "language must be one of: en, hi, mr, ta, te, bn, kn, gu, ml")
    private String language;

    private Boolean notifications;

    private Customer.Status status;
}
