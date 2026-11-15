package com.project.main.payment_platform.exception;

public class InactiveMerchantException extends RuntimeException{

    public InactiveMerchantException() {
        super("Merchant account is not active");
    }
}
