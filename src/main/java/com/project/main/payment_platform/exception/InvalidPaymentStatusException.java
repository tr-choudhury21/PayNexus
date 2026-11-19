package com.project.main.payment_platform.exception;

import com.project.main.payment_platform.payment.enums.PaymentStatus;

public class InvalidPaymentStatusException extends RuntimeException{

    public InvalidPaymentStatusException(PaymentStatus status){
        super("Cannot initiate a payment attempt when payment status is: " + status);
    }
}
