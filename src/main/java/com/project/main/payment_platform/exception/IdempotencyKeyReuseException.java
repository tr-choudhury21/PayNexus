package com.project.main.payment_platform.exception;

public class IdempotencyKeyReuseException extends RuntimeException{

    public IdempotencyKeyReuseException() {
        super("Idempotency key has already been used with a different request" );
    }
}
