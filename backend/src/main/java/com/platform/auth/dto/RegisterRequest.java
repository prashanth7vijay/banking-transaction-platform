package com.platform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,

        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "Password must be at least 8 characters and include a letter and a digit"
        )
        String password,

        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName
) {
}
