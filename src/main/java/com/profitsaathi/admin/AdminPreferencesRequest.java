package com.profitsaathi.admin;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminPreferencesRequest {

    @Pattern(regexp = "light|dark|system",
             message = "theme must be one of: light, dark, system")
    private String theme;

    @Pattern(regexp = "emerald|sky|violet|rose|amber",
             message = "accent must be one of: emerald, sky, violet, rose, amber")
    private String accent;

    @Pattern(regexp = "india|apac|emea",
             message = "region must be one of: india, apac, emea")
    private String region;

    private Boolean notifyEmail;
    private Boolean notifyWhatsapp;
}
