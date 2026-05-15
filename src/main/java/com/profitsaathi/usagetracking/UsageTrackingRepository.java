package com.profitsaathi.usagetracking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsageTrackingRepository extends JpaRepository<UsageTracking, Long> {
    Optional<UsageTracking> findByCredentialsIdAndFeatureName(Long credentialsId, String featureName);
}
