package com.rockettrading.rocket_trading.dto.auth;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RegisterClientRequest(
        @NotBlank(message = "name is required")
        String name,
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 128, message = "password must be 8 to 128 characters")
        String password,
        LocalDate dateOfBirth,
        @Pattern(regexp = "Cautious|Balanced|Adventurous", message = "riskProfile must be Cautious, Balanced or Adventurous")
        String riskProfile,
        @DecimalMin(value = "0.01", message = "initialCash must be positive")
        BigDecimal initialCash
) {
    @Override
    public String toString() {
        // Never let the password reach a log line or audit record
        return "RegisterClientRequest[name=" + name + ", email=" + email + "]";
    }
}
