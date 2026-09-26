package com.lms.dto.auth;

import java.time.Instant;

import com.lms.entity.User;
import com.lms.entity.enums.RoleName;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        RoleName role,
        boolean enabled,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRole().getName(), user.isEnabled(), user.getCreatedAt());
    }
}
