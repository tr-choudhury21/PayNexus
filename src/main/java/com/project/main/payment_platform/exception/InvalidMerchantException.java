package com.project.main.payment_platform.exception;

public class InvalidMerchantException extends RuntimeException{

    public InvalidMerchantException() {
        super("Selected user is not a merchant");
    }
}
