package com.kodilla.portfolio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserRequest(
            @NotBlank(message = "username is required")
            @Size(min = 3, max = 60, message = "username must be 3-60 characters")
            String username,

            @NotBlank(message = "email is required")
            @Email(message = "email must be a valid address")
            @Size(max = 160)
            String email,

            @Size(max = 120)
            String displayName) {
    }

    public record UserResponse(
            Long id,
            String username,
            String email,
            String displayName,
            LocalDateTime createdAt,
            int portfolioCount) {
    }
}
