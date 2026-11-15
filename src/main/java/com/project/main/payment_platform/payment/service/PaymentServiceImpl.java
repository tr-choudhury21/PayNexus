package com.project.main.payment_platform.payment.service;


import com.project.main.payment_platform.exception.*;
import com.project.main.payment_platform.payment.dto.CreatePaymentRequest;
import com.project.main.payment_platform.payment.dto.PaymentResponse;
import com.project.main.payment_platform.payment.entity.Payment;
import com.project.main.payment_platform.payment.enums.PaymentStatus;
import com.project.main.payment_platform.payment.mapper.PaymentMapper;
import com.project.main.payment_platform.payment.repository.PaymentRepository;
import com.project.main.payment_platform.user.entity.Role;
import com.project.main.payment_platform.user.entity.User;
import com.project.main.payment_platform.user.entity.UserStatus;
import com.project.main.payment_platform.user.repository.UserRepository;
import com.project.main.payment_platform.security.SecurityUtils;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PaymentMapper paymentMapper;


    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {

        // 1. Get authenticated customer
        UUID customerId = SecurityUtils.getCurrentUserId();

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new UserNotFoundException(customerId));

        // 2. Validate customer account
        if (customer.getRole() != Role.CUSTOMER ||
                customer.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCustomerException();
        }

        // 3. Retrieve merchant
        User merchant = userRepository.findById(request.getMerchantId())
                .orElseThrow(() ->
                        new MerchantNotFoundException(request.getMerchantId()));

        // 4. Validate merchant
        if (merchant.getRole() != Role.MERCHANT) {
            throw new InvalidMerchantException();
        }

        if (merchant.getStatus() != UserStatus.ACTIVE) {
            throw new InactiveMerchantException();
        }

        // 5. Create payment entity
        Payment payment = new Payment();

        payment.setPaymentReference(generatePaymentReference());
        payment.setCustomer(customer);
        payment.setMerchant(merchant);
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setDescription(request.getDescription());
        payment.setStatus(PaymentStatus.CREATED);

        // 6. Persist payment
        Payment savedPayment = paymentRepository.save(payment);

        // 7. Convert entity to response DTO
        return paymentMapper.toResponse(savedPayment);
    }

    private String generatePaymentReference() {
        return "PAY-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase(Locale.ROOT);
    }

}
