package com.project.main.payment_platform.exception;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Map;

@Builder
@Getter
public class ValidationErrorResponse {
    private int status;
    private String message;
    private Map<String, String> errors;
    private OffsetDateTime timestamp;
}
