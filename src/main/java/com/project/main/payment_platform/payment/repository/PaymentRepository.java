package com.project.main.payment_platform.payment.repository;

import com.project.main.payment_platform.payment.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;


public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByPaymentReference(String paymentReference);

    boolean existsByPaymentReference(String paymentReference);

    Page<Payment> findAllByCustomer_IdOrderByCreatedAtDesc(
            UUID customerId,
            Pageable pageable
    );

    Page<Payment> findAllByMerchant_IdOrderByCreatedAtDesc(
            UUID merchantId,
            Pageable pageable
    );
}
