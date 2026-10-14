package com.rockettrading.rocket_trading.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank(message = "name is required")
        String name,
        @NotNull(message = "dateOfBirth is required")
        LocalDate dateOfBirth,
        @Pattern(regexp = "Cautious|Balanced|Adventurous", message = "riskProfile must be Cautious, Balanced or Adventurous")
        String riskProfile
) {
}
