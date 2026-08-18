package com.platform.transactions.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferRequest(
        @NotNull UUID sourceAccountId,

        @NotBlank
        @Pattern(regexp = "^[0-9]{10}$", message = "Account number must be exactly 10 digits")
        String destinationAccountNumber,

        @NotNull
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotBlank String idempotencyKey,

        @Size(max = 255) String note
) {
}
