package com.project.main.payment_platform.payment.mapper;

import com.project.main.payment_platform.payment.dto.PaymentAttemptResponse;
import com.project.main.payment_platform.payment.entity.PaymentAttempt;
import org.springframework.stereotype.Component;

@Component
public class PaymentAttemptResponseMapper {

    public PaymentAttemptResponse toResponse(PaymentAttempt attempt) {

        return PaymentAttemptResponse.builder()
                .id(attempt.getId())
                .paymentId(attempt.getPayment().getId())
                .attemptNumber(attempt.getAttemptNumber())
                .gatewayName(attempt.getGatewayName())
                .gatewayReference(attempt.getGatewayReference())
                .status(attempt.getStatus())
                .errorCode(attempt.getErrorCode())
                .errorMessage(attempt.getErrorMessage())
                .startedAt(attempt.getStartedAt())
                .completedAt(attempt.getCompletedAt())
                .createdAt(attempt.getCreatedAt())
                .build();
    }
}
