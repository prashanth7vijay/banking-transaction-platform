package com.platform.exceptions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExceptionNoteRequest(
        @NotBlank @Size(max = 2000) String content
) {
}
