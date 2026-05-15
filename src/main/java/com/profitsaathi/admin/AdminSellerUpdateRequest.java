package com.profitsaathi.admin;

import com.profitsaathi.seller.user.Seller;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Admin-side patch for a seller profile. Email is intentionally absent —
 * it doubles as the credential identifier and changing it would desync the
 * {@code credentials.email} row. All fields are optional; null leaves the
 * existing column untouched.
 */
@Data
public class AdminSellerUpdateRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String storeName;

    @Size(max = 50)
    private String sellerType;

    @Size(max = 16)
    private String mobile;

    private Seller.Status status;
}
