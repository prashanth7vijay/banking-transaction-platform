package com.platform.accounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBeneficiaryRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9]{10}$", message = "Account number must be exactly 10 digits")
        String beneficiaryAccountNumber,

        @NotBlank @Size(max = 100) String nickname
) {
}
