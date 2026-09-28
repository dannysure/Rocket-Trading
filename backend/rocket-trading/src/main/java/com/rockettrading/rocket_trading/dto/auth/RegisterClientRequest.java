package com.rockettrading.rocket_trading.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RegisterClientRequest(
        @NotBlank(message = "name is required")
        String name,
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,
        LocalDate dateOfBirth,
        String riskProfile,
        @DecimalMin(value = "0.01", message = "initialCash must be positive")
        BigDecimal initialCash
) {
}
