package com.profitsaathi.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Patch payload for an admin row when one admin manages another admin.
 * Email + auth credentials are not exposed — those live on the
 * {@code credentials} table and changing them would desync auth state.
 */
@Data
public class AdminUserUpdateRequest {

    @Size(max = 255)
    private String name;

    private Admin.Status status;
}
