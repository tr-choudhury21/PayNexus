package com.project.main.payment_platform.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException{

    public PaymentNotFoundException(UUID paymentId) {
        super("Payment not found with ID: " + paymentId);
    }
}
