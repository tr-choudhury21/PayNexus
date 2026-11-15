package com.project.main.payment_platform.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException{

    public UserNotFoundException(UUID userId) {
        super("User not found with ID: " + userId);
    }
}
