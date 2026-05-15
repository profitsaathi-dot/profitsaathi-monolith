package com.profitsaathi.seller.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Payload for {@code PATCH /api/v1/seller/onboard}. Identity comes from JWT. */
@Data
public class OnboardRequest {

    @NotBlank(message = "storeName is required")
    @Size(min = 2, max = 255, message = "storeName must be 2-255 characters")
    private String storeName;

    @NotBlank(message = "sellerType is required")
    @Pattern(regexp = "individual|social", message = "sellerType must be 'individual' or 'social'")
    private String sellerType;

    @NotBlank(message = "mobile is required")
    @Pattern(regexp = "^[6-9]\\d{9}$",
             message = "mobile must be a 10-digit Indian mobile number")
    private String mobile;

    @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$|^$",
             message = "username must be 3-20 letters, digits, or underscore")
    private String username;
}
