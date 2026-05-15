package com.profitsaathi.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasskeysRepository extends JpaRepository<Passkeys, Long> {
    Optional<Passkeys> findByCredentialId(String credentialId);
    List<Passkeys> findAllByUserId(Long userId);
    boolean existsByUserId(Long userId);
}
