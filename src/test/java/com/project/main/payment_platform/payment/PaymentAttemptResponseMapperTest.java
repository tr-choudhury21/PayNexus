package com.project.main.payment_platform.payment;


import com.project.main.payment_platform.payment.dto.PaymentAttemptResponse;
import com.project.main.payment_platform.payment.entity.Payment;
import com.project.main.payment_platform.payment.entity.PaymentAttempt;
import com.project.main.payment_platform.payment.enums.PaymentAttemptStatus;
import com.project.main.payment_platform.payment.mapper.PaymentAttemptResponseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PaymentAttemptResponseMapperTest {

    private final PaymentAttemptResponseMapper mapper =
            new PaymentAttemptResponseMapper();

    @Test
    void shouldMapPaymentAttemptToResponse() {

        UUID paymentId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();

        Payment payment = mock(Payment.class);
        when(payment.getId()).thenReturn(paymentId);

        PaymentAttempt attempt = mock(PaymentAttempt.class);

        when(attempt.getId()).thenReturn(attemptId);
        when(attempt.getPayment()).thenReturn(payment);
        when(attempt.getAttemptNumber()).thenReturn(1);
        when(attempt.getGatewayName()).thenReturn("MOCK");
        when(attempt.getGatewayReference()).thenReturn("MOCK-REF-123");
        when(attempt.getStatus()).thenReturn(PaymentAttemptStatus.INITIATED);

        PaymentAttemptResponse response = mapper.toResponse(attempt);

        assertEquals(attemptId, response.getId());
        assertEquals(paymentId, response.getPaymentId());
        assertEquals(1, response.getAttemptNumber());
        assertEquals("MOCK", response.getGatewayName());
        assertEquals("MOCK-REF-123", response.getGatewayReference());
        assertEquals(PaymentAttemptStatus.INITIATED, response.getStatus());
    }
}
