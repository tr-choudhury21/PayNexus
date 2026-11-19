package com.project.main.payment_platform.payment.repository;

import com.project.main.payment_platform.payment.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    List<PaymentAttempt> findAllByPayment_IdOrderByAttemptNumberAsc(
            UUID paymentId
    );

    Optional<PaymentAttempt> findByPayment_IdAndAttemptNumber(
            UUID paymentId,
            Integer attemptNumber
    );

    boolean existsByPayment_IdAndAttemptNumber(
            UUID paymentId,
            Integer attemptNumber
    );

    Optional<PaymentAttempt> findByGatewayNameAndGatewayReference(
            String gatewayName,
            String gatewayReference
    );

    long countByPayment_Id(UUID paymentId);
}
