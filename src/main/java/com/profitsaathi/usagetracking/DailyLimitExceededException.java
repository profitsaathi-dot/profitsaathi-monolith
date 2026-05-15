package com.profitsaathi.usagetracking;

import lombok.Getter;

@Getter
public class DailyLimitExceededException extends RuntimeException {

    private final String featureName;
    private final int dailyCap;
    private final int currentCount;

    public DailyLimitExceededException(String featureName, int dailyCap, int currentCount) {
        super("Daily limit reached for " + featureName + " (" + currentCount + "/" + dailyCap + "). Resets at midnight IST.");
        this.featureName = featureName;
        this.dailyCap = dailyCap;
        this.currentCount = currentCount;
    }
}
