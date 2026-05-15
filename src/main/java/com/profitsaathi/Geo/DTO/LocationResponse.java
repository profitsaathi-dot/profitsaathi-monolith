package com.profitsaathi.Geo.DTO;

import jakarta.validation.constraints.NotBlank;

public record LocationResponse(
    @NotBlank
    String city,
    @NotBlank String state,
    @NotBlank String country,
    @NotBlank String postcode,
    @NotBlank String displayName
){}
