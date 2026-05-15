package com.profitsaathi.usagetracking;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@AllArgsConstructor
public class UsageTrackingService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final UsageTrackingRepository repository;

    public UsageTracking incrementUsage(AuthenticatedPrincipal me, String featureName) {
        UsageTracking usage = repository
                .findByCredentialsIdAndFeatureName(me.credentialsId(), featureName)
                .orElseGet(() -> {
                    UsageTracking u = new UsageTracking();
                    u.setCredentialsId(me.credentialsId());
                    u.setSubjectRole(me.role());
                    u.setFeatureName(featureName);
                    u.setUsageCount(0);
                    u.setLastResetDate(LocalDate.now(IST));
                    return u;
                });

        usage.setUsageCount(usage.getUsageCount() == null ? 1 : usage.getUsageCount() + 1);
        return repository.save(usage);
    }

    /**
     * Reserves one unit of the daily cap atomically: resets the counter to 0 if
     * we've crossed midnight in IST since the last reset, then either increments
     * and returns the new state, or throws {@link DailyLimitExceededException}
     * if the cap is already hit. Use this — not {@link #incrementUsage} —
     * for any feature that has a per-day quota.
     */
    public UsageTracking checkAndIncrement(AuthenticatedPrincipal me, String featureName, int dailyCap) {
        LocalDate today = LocalDate.now(IST);
        UsageTracking usage = repository
                .findByCredentialsIdAndFeatureName(me.credentialsId(), featureName)
                .orElseGet(() -> {
                    UsageTracking u = new UsageTracking();
                    u.setCredentialsId(me.credentialsId());
                    u.setSubjectRole(me.role());
                    u.setFeatureName(featureName);
                    u.setUsageCount(0);
                    u.setLastResetDate(today);
                    return u;
                });

        if (usage.getLastResetDate() == null || usage.getLastResetDate().isBefore(today)) {
            usage.setUsageCount(0);
            usage.setLastResetDate(today);
        }

        int current = usage.getUsageCount() == null ? 0 : usage.getUsageCount();
        if (current >= dailyCap) {
            throw new DailyLimitExceededException(featureName, dailyCap, current);
        }
        usage.setUsageCount(current + 1);
        return repository.save(usage);
    }

    /** Read-only counter view — used to render "X / cap left today" badges. */
    public int usageToday(AuthenticatedPrincipal me, String featureName) {
        LocalDate today = LocalDate.now(IST);
        return repository
                .findByCredentialsIdAndFeatureName(me.credentialsId(), featureName)
                .filter(u -> u.getLastResetDate() != null && !u.getLastResetDate().isBefore(today))
                .map(UsageTracking::getUsageCount)
                .orElse(0);
    }
}
