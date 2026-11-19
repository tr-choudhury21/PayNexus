package com.project.main.payment_platform.payment.service;

import com.project.main.payment_platform.exception.InvalidPaymentStatusException;
import com.project.main.payment_platform.exception.PaymentNotFoundException;
import com.project.main.payment_platform.exception.PaymentOwnershipException;
import com.project.main.payment_platform.payment.dto.PaymentAttemptResponse;
import com.project.main.payment_platform.payment.entity.Payment;
import com.project.main.payment_platform.payment.entity.PaymentAttempt;
import com.project.main.payment_platform.payment.enums.PaymentAttemptStatus;
import com.project.main.payment_platform.payment.enums.PaymentStatus;
import com.project.main.payment_platform.payment.mapper.PaymentAttemptResponseMapper;
import com.project.main.payment_platform.payment.repository.PaymentAttemptRepository;
import com.project.main.payment_platform.payment.repository.PaymentRepository;
import com.project.main.payment_platform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentAttemptServiceImpl implements PaymentAttemptService{

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentAttemptResponseMapper paymentAttemptMapper;

    @Override
    @Transactional
    public PaymentAttemptResponse createPaymentAttempt(UUID paymentId) {
        // Get authenticated customer
        UUID customerId = SecurityUtils.getCurrentUserId();

        // Retrieve payment
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        // Validate ownership
        if (!payment.getCustomer().getId().equals(customerId)) {
            throw new PaymentOwnershipException();
        }

        // Validate payment status
        PaymentStatus status = payment.getStatus();

        if (status != PaymentStatus.CREATED &&
                status != PaymentStatus.FAILED) {

            throw new InvalidPaymentStatusException(status);
        }

        // Calculate next attempt number
        int nextAttemptNumber = Math.toIntExact(
                paymentAttemptRepository.countByPayment_Id(paymentId) + 1
        );

        // Create payment attempt
        PaymentAttempt attempt = new PaymentAttempt();

        attempt.setPayment(payment);
        attempt.setAttemptNumber(nextAttemptNumber);
        attempt.setGatewayName("MOCK");
        attempt.setStatus(PaymentAttemptStatus.INITIATED);

        // Save attempt
        PaymentAttempt savedAttempt =
                paymentAttemptRepository.save(attempt);

        // Update parent payment status
        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);

        // Return response
        return paymentAttemptMapper.toResponse(savedAttempt);
    }
}
