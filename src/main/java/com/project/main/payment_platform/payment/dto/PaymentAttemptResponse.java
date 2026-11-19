package com.project.main.payment_platform.payment.dto;

import com.project.main.payment_platform.payment.enums.PaymentAttemptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAttemptResponse {

    private UUID id;

    private UUID paymentId;

    private Integer attemptNumber;

    private String gatewayName;

    private String gatewayReference;

    private PaymentAttemptStatus status;

    private String errorCode;

    private String errorMessage;

    private OffsetDateTime startedAt;

    private OffsetDateTime completedAt;

    private OffsetDateTime createdAt;
}
