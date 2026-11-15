package com.project.main.payment_platform.exception;

import java.util.UUID;

public class MerchantNotFoundException extends RuntimeException{

    public MerchantNotFoundException(UUID merchantId) {
        super("Merchant not found with ID: " + merchantId);
    }
}
