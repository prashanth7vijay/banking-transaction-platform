package com.platform.exceptions.dto;

import jakarta.validation.constraints.Size;

public record OptionalNoteRequest(
        @Size(max = 2000) String content
) {
}
