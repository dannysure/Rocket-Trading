package com.rockettrading.rocket_trading.dto.auth;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank(message = "name is required")
        String name,
        LocalDate dateOfBirth,
        String riskProfile
) {
}
