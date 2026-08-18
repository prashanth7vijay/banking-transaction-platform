package com.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank String token,

        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "Password must be at least 8 characters and include a letter and a digit"
        )
        String newPassword
) {
}
