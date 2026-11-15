package com.project.main.payment_platform.payment.dto;

import com.project.main.payment_platform.payment.enums.PaymentMethod;
import com.project.main.payment_platform.payment.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private UUID id;

    private String paymentReference;

    private UUID customerId;

    private UUID merchantId;

    private BigDecimal amount;

    private String currency;

    private PaymentStatus status;

    private PaymentMethod paymentMethod;

    private String description;

    private OffsetDateTime expiresAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
