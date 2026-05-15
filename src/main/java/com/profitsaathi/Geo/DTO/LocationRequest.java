package com.profitsaathi.Geo.DTO;

import jakarta.validation.constraints.NotNull;

public record LocationRequest(
        @NotNull Double lat,
        @NotNull Double lng
){}
