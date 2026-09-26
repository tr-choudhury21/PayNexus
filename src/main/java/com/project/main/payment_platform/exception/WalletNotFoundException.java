package com.project.main.payment_platform.exception;

public class WalletNotFoundException extends RuntimeException{

    public WalletNotFoundException(String currency) {
        super("Wallet not found for currency: " + currency);
    }
}
