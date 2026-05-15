package com.profitsaathi.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CredentialsRepository extends JpaRepository<Credentials, Long> {
    Optional<Credentials> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<Credentials> findByRoleAndSubjectId(Credentials.Role role, Long subjectId);
}
