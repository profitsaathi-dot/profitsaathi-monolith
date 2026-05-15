package com.profitsaathi.otp;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "otp_verification")
public class OtpEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String mobilenumber;
    private String email;
    private String otp;
    private LocalDateTime expiryTime;
    private int attempts;
    private boolean verified;
}
