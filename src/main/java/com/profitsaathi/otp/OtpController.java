package com.profitsaathi.otp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitsaathi.otp.OtpDtos.*;
import com.profitsaathi.util.aes.AESRequest;
import com.profitsaathi.util.aes.AESService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;
    private final AESService aesService;
    private final ObjectMapper objectMapper;

    @PostMapping("/send/email")
    public ResponseEntity<ApiResponseOTP> sendEmailOtp(@Valid @RequestBody AESRequest request) {
        try {
            String json = aesService.decryptToJson(request);
            EmailOtpRequest req = objectMapper.readValue(json, EmailOtpRequest.class);
            otpService.sendEmailOtp(req.getEmails(), req.getCc(), req.getName());
            return ResponseEntity.ok(new ApiResponseOTP(true, "OTP sent successfully to email"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponseOTP(false, e.getMessage()));
        }
    }

    @PostMapping("/send/whatsapp")
    public ResponseEntity<ApiResponseOTP> sendWhatsAppOtp(@Valid @RequestBody AESRequest request) {
        try {
            String json = aesService.decryptToJson(request);
            WhatsAppOtpRequest req = objectMapper.readValue(json, WhatsAppOtpRequest.class);
            otpService.sendWhatsAppOtp(req.getChatId(), req.getSession());
            return ResponseEntity.ok(new ApiResponseOTP(true, "OTP sent successfully via WhatsApp"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponseOTP(false, e.getMessage()));
        }
    }

    @PostMapping("/verify/email")
    public ResponseEntity<ApiResponseOTP> verifyEmailOtp(@Valid @RequestBody AESRequest request) {
        try {
            String json = aesService.decryptToJson(request);
            VerifyOtpRequest req = objectMapper.readValue(json, VerifyOtpRequest.class);
            otpService.verifyOtp(req.getIdentifier(), req.getOtp(), true);
            return ResponseEntity.ok(new ApiResponseOTP(true, "Email OTP verified successfully"));
        } catch (OtpVerificationException | JsonProcessingException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponseOTP(false, ex.getMessage()));
        }
    }

    @PostMapping("/verify/whatsapp")
    public ResponseEntity<ApiResponseOTP> verifyWhatsAppOtp(@Valid @RequestBody AESRequest request) {
        try {
            String json = aesService.decryptToJson(request);
            VerifyOtpRequest req = objectMapper.readValue(json, VerifyOtpRequest.class);
            otpService.verifyOtp(req.getIdentifier(), req.getOtp(), false);
            return ResponseEntity.ok(new ApiResponseOTP(true, "WhatsApp OTP verified successfully"));
        } catch (OtpVerificationException | JsonProcessingException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponseOTP(false, ex.getMessage()));
        }
    }
}
