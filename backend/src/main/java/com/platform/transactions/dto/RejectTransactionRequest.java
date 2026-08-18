package com.platform.transactions.dto;

import jakarta.validation.constraints.Size;

public record RejectTransactionRequest(
        @Size(max = 255) String reason
) {
}
