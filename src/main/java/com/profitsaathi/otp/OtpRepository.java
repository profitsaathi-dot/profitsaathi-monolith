package com.profitsaathi.otp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpRepository extends JpaRepository<OtpEntity, Long> {
    Optional<OtpEntity> findTopByEmailOrderByIdDesc(String email);
    Optional<OtpEntity> findTopByMobilenumberOrderByIdDesc(String mobile);
}
