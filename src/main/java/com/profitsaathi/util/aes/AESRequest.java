package com.profitsaathi.util.aes;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AESRequest {
    @NotEmpty(message = "request cannot be empty")
    private String request;
}
