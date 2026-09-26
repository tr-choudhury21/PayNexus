package com.project.main.payment_platform.exception;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException{

    public InsufficientBalanceException(BigDecimal amount) {
        super("Insufficient wallet balance for transaction amount: " + amount);
    }
}
