package com.project.main.payment_platform.payment.service;

import com.project.main.payment_platform.payment.dto.CreatePaymentRequest;
import com.project.main.payment_platform.payment.dto.PaymentResponse;

public interface PaymentService {

    PaymentResponse createPayment(CreatePaymentRequest request);
}
