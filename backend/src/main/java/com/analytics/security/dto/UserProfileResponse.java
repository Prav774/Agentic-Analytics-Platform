package com.analytics.security.dto;

import com.analytics.security.entity.Role;
import com.analytics.security.entity.User;

import java.time.Instant;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        Role role,
        Instant createdAt
) {
    public static UserProfileResponse fromEntity(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
