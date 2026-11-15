package com.project.main.payment_platform.exception;

public class InvalidCustomerException extends RuntimeException{

    public InvalidCustomerException() {
        super("Customer account is not eligible to make payments");
    }
}
