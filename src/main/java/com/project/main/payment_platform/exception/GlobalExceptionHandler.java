package com.project.main.payment_platform.exception;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.project.main.payment_platform.exception.InsufficientBalanceException;
import com.project.main.payment_platform.exception.WalletAlreadyExistsException;
import com.project.main.payment_platform.exception.WalletNotActiveException;
import com.project.main.payment_platform.exception.WalletNotFoundException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status, String message) {

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(status.value())
                .message(message)
                .timestamp(OffsetDateTime.now())
                .build();

        return ResponseEntity.status(status).body(errorResponse);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex) {

        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new LinkedHashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(error.getField(), error.getDefaultMessage())
                );

        ValidationErrorResponse response = ValidationErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Validation failed")
                .errors(errors)
                .timestamp(OffsetDateTime.now())
                .build();

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex) {

        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWalletNotFound(
            WalletNotFoundException ex) {

        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(WalletAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleWalletAlreadyExists(
            WalletAlreadyExistsException ex) {

        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(
            InsufficientBalanceException ex) {

        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(WalletNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleWalletNotActive(
            WalletNotActiveException ex) {

        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException ex) {

        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MerchantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMerchantNotFound(
            MerchantNotFoundException ex) {

        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidMerchantException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMerchant(
            InvalidMerchantException ex) {

        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InactiveMerchantException.class)
    public ResponseEntity<ErrorResponse> handleInactiveMerchant(
            InactiveMerchantException ex) {

        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidCustomerException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCustomer(
            InvalidCustomerException ex) {

        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex) {

        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(
            PaymentNotFoundException ex) {

        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PaymentOwnershipException.class)
    public ResponseEntity<ErrorResponse> handlePaymentOwnership(
            PaymentOwnershipException ex) {

        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InvalidPaymentStatusException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPaymentStatus(
            InvalidPaymentStatusException ex) {

        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }
}
