package com.project.main.payment_platform.exception;

public class PaymentOwnershipException extends RuntimeException{

    public PaymentOwnershipException(){
        super("You are not authorized to access this payment");
    }
}
