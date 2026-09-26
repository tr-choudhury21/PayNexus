package com.project.main.payment_platform.exception;

public class WalletNotActiveException extends RuntimeException{

    public WalletNotActiveException() {
        super("Wallet is not active");
    }
}
