package com.project.main.payment_platform.exception;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;


@Builder
@Getter
public class ErrorResponse {

    private int status;
    private String message;
    private OffsetDateTime timestamp;
}
