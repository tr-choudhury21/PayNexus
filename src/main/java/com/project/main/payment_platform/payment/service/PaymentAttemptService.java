package com.project.main.payment_platform.payment.service;

import com.project.main.payment_platform.payment.dto.PaymentAttemptResponse;

import java.util.UUID;

public interface PaymentAttemptService {

    PaymentAttemptResponse createPaymentAttempt(UUID paymentId);
}
