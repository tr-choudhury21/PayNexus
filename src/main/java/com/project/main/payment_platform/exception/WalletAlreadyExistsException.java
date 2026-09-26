package com.project.main.payment_platform.exception;

public class WalletAlreadyExistsException extends RuntimeException{

    public WalletAlreadyExistsException(String currency) {
        super("Wallet already exists for currency: " + currency);
    }
}
