package com.profitsaathi.otp;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class OtpDtos {

    private OtpDtos() {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiResponseOTP {
        private boolean success;
        private String message;
    }

    @Data
    public static class EmailOtpRequest {
        @NotEmpty(message = "Email list cannot be empty")
        private List<@Email(message = "Invalid email format") String> emails;
        private List<String> cc;
        @NotBlank(message = "Name is required")
        private String name;
    }

    @Data
    public static class WhatsAppOtpRequest {
        @NotBlank(message = "Chat ID / Mobile Number is required")
        private String chatId;
        private String text;
        private String session;
    }

    @Data
    public static class VerifyOtpRequest {
        @NotBlank(message = "Identifier (Email/Phone) is required")
        private String identifier;
        @NotBlank(message = "OTP cannot be blank")
        private String otp;
    }
}
