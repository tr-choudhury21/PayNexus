package com.project.main.payment_platform.payment.controller;


import com.project.main.payment_platform.payment.dto.PaymentAttemptResponse;
import com.project.main.payment_platform.payment.service.PaymentAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/{paymentId}/attempts")
@RequiredArgsConstructor
public class PaymentAttemptController {

    private final PaymentAttemptService paymentAttemptService;

    @PostMapping
    public ResponseEntity<PaymentAttemptResponse> createPaymentAttempt(
            @PathVariable UUID paymentId) {

        PaymentAttemptResponse response =
                paymentAttemptService.createPaymentAttempt(paymentId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
