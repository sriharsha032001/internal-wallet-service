package com.internal_wallet.internal_wallet.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ErrorResponse {

    private final int status;
    private final String error;
    private final String message;

    /** Field-level validation errors — populated only for 400 DTO violations. */
    private final List<FieldError> fieldErrors;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime timestamp;

    @Getter
    @Builder
    public static class FieldError {
        private final String field;
        private final String message;
    }
}
