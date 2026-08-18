package com.platform.exceptions.dto;

import java.util.List;

public record ExceptionDetailResponse(
        TransactionExceptionResponse exception,
        List<ExceptionNoteResponse> notes,
        LinkedTransactionResponse transaction
) {
}
