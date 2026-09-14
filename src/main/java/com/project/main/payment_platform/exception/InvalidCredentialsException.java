package com.project.main.payment_platform.exception;

public class InvalidCredentialsException extends RuntimeException{

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
