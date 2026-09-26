package com.lms.dto.auth;

import com.lms.entity.enums.RoleName;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-registration. {@code role} defaults to STUDENT; ADMIN accounts can only be created by the seeder
 * or an existing admin.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        RoleName role) {

    @AssertTrue(message = "Self-registration as ADMIN is not allowed")
    public boolean isRoleAllowed() {
        return role != RoleName.ADMIN;
    }

    public RoleName effectiveRole() {
        return role == null ? RoleName.STUDENT : role;
    }
}
