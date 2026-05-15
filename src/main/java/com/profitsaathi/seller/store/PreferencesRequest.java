package com.profitsaathi.seller.store;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PreferencesRequest {

    @Pattern(regexp = "en|hi|mr|ta|te|bn|kn|gu|ml",
             message = "language must be one of: en, hi, mr, ta, te, bn, kn, gu, ml")
    private String language;

    @Pattern(regexp = "light|dark|system",
             message = "theme must be one of: light, dark, system")
    private String theme;

    @Pattern(regexp = "emerald|sky|violet|rose|amber",
             message = "accent must be one of: emerald, sky, violet, rose, amber")
    private String accent;

    /** Boolean flags use {@code Boolean} (nullable) so the PATCH endpoint can
     *  distinguish "field omitted" from "field set to false". */
    private Boolean weeklyReportOptIn;

    private Boolean monthlyReportOptIn;
}
