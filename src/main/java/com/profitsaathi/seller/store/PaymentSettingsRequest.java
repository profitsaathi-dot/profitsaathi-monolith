package com.profitsaathi.seller.store;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaymentSettingsRequest {

    @Pattern(regexp = "ONLINE|UPI_QR|BANK_ACCOUNT|^$",
             message = "paymentType must be ONLINE, UPI_QR, or BANK_ACCOUNT")
    private String paymentType;

    @Size(max = 1024)
    private String paymentQRCode;

    @Pattern(regexp = "^\\d{9,18}$|^$",
             message = "bankAccountNumber must be 9-18 digits")
    private String bankAccountNumber;

    @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$|^$",
             message = "bankAccountIfsc must be a valid 11-character IFSC code")
    private String bankAccountIfsc;
}
