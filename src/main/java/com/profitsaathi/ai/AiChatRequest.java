package com.profitsaathi.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Text chat request. Image flows go through the dedicated /verify-payment endpoint. */
public record AiChatRequest(
        @NotBlank @Size(max = 8000) String message,
        String model
) {}
