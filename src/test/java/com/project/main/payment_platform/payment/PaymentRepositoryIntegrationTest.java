package com.project.main.payment_platform.payment;

import com.project.main.payment_platform.payment.entity.Payment;
import com.project.main.payment_platform.payment.entity.PaymentAttempt;
import com.project.main.payment_platform.payment.enums.PaymentAttemptStatus;
import com.project.main.payment_platform.payment.enums.PaymentMethod;
import com.project.main.payment_platform.payment.enums.PaymentStatus;
import com.project.main.payment_platform.payment.repository.PaymentAttemptRepository;
import com.project.main.payment_platform.payment.repository.PaymentRepository;
import com.project.main.payment_platform.user.entity.User;
import com.project.main.payment_platform.user.entity.Role;
import com.project.main.payment_platform.user.entity.UserStatus;
import com.project.main.payment_platform.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Transactional
class PaymentRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("payment_platform")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .withEnv("TZ", "Asia/Kolkata")
                    .withEnv("PGTZ", "Asia/Kolkata");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {

        registry.add(
                "spring.datasource.url",
                () -> postgres.getJdbcUrl()
                        + "?options=-c%20TimeZone=Asia%2FKolkata"
        );

        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );

        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private UserRepository userRepository;

    private User customer;
    private User merchant;

    @BeforeEach
    void setUp() {

        customer = userRepository.save(
                User.builder()
                        .email("customer-" + UUID.randomUUID() + "@test.com")
                        .passwordHash("hashed-password")
                        .firstName("Test")
                        .lastName("Customer")
                        .role(Role.CUSTOMER)
                        .status(UserStatus.ACTIVE)
                        .build()
        );

        merchant = userRepository.save(
                User.builder()
                        .email("merchant-" + UUID.randomUUID() + "@test.com")
                        .passwordHash("hashed-password")
                        .firstName("Test")
                        .lastName("Merchant")
                        .role(Role.MERCHANT)
                        .status(UserStatus.ACTIVE)
                        .build()
        );
    }

    // =========================================
    // PAYMENT REPOSITORY TESTS
    // =========================================

    @Test
    void shouldSaveAndRetrievePayment() {

        Payment payment = createPayment(
                customer,
                merchant,
                generateReference()
        );

        Payment savedPayment = paymentRepository.saveAndFlush(payment);

        Payment retrievedPayment = paymentRepository
                .findByPaymentReference(savedPayment.getPaymentReference())
                .orElseThrow();

        assertEquals(savedPayment.getId(), retrievedPayment.getId());

        assertEquals(
                savedPayment.getPaymentReference(),
                retrievedPayment.getPaymentReference()
        );

        assertEquals(
                0,
                new BigDecimal("2500.00")
                        .compareTo(retrievedPayment.getAmount())
        );

        assertEquals(PaymentStatus.CREATED, retrievedPayment.getStatus());
        assertEquals(customer.getId(), retrievedPayment.getCustomer().getId());
        assertEquals(merchant.getId(), retrievedPayment.getMerchant().getId());

        assertTrue(
                paymentRepository.existsByPaymentReference(
                        savedPayment.getPaymentReference()
                )
        );
    }

    @Test
    void shouldRetrievePaymentsByCustomerAndMerchant() {

        Payment payment = createPayment(
                customer,
                merchant,
                generateReference()
        );

        paymentRepository.saveAndFlush(payment);

        Page<Payment> customerPayments =
                paymentRepository.findAllByCustomer_IdOrderByCreatedAtDesc(
                        customer.getId(),
                        PageRequest.of(0, 10)
                );

        Page<Payment> merchantPayments =
                paymentRepository.findAllByMerchant_IdOrderByCreatedAtDesc(
                        merchant.getId(),
                        PageRequest.of(0, 10)
                );

        assertEquals(1, customerPayments.getTotalElements());
        assertEquals(1, merchantPayments.getTotalElements());

        assertEquals(
                payment.getId(),
                customerPayments.getContent().get(0).getId()
        );

        assertEquals(
                payment.getId(),
                merchantPayments.getContent().get(0).getId()
        );
    }

    @Test
    void shouldRejectDuplicatePaymentReference() {

        String reference = generateReference();

        Payment firstPayment = createPayment(
                customer,
                merchant,
                reference
        );

        paymentRepository.saveAndFlush(firstPayment);

        Payment duplicatePayment = createPayment(
                customer,
                merchant,
                reference
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> paymentRepository.saveAndFlush(duplicatePayment)
        );
    }

    // =========================================
    // PAYMENT ATTEMPT REPOSITORY TESTS
    // =========================================

    @Test
    void shouldSaveAndRetrieveMultiplePaymentAttemptsInOrder() {

        Payment payment = paymentRepository.saveAndFlush(
                createPayment(customer, merchant, generateReference())
        );

        PaymentAttempt secondAttempt = createAttempt(
                payment,
                2,
                "STRIPE",
                "stripe-txn-" + UUID.randomUUID(),
                PaymentAttemptStatus.SUCCEEDED
        );

        PaymentAttempt firstAttempt = createAttempt(
                payment,
                1,
                "RAZORPAY",
                "razorpay-txn-" + UUID.randomUUID(),
                PaymentAttemptStatus.FAILED
        );

        paymentAttemptRepository.saveAndFlush(secondAttempt);
        paymentAttemptRepository.saveAndFlush(firstAttempt);

        List<PaymentAttempt> attempts =
                paymentAttemptRepository
                        .findAllByPayment_IdOrderByAttemptNumberAsc(
                                payment.getId()
                        );

        assertEquals(2, attempts.size());

        assertEquals(1, attempts.get(0).getAttemptNumber());
        assertEquals(2, attempts.get(1).getAttemptNumber());

        assertEquals("RAZORPAY", attempts.get(0).getGatewayName());
        assertEquals("STRIPE", attempts.get(1).getGatewayName());

        assertTrue(
                paymentAttemptRepository.existsByPayment_IdAndAttemptNumber(
                        payment.getId(),
                        1
                )
        );
    }

    @Test
    void shouldFindPaymentAttemptByGatewayReference() {

        Payment payment = paymentRepository.saveAndFlush(
                createPayment(customer, merchant, generateReference())
        );

        String gatewayReference = "mock-txn-" + UUID.randomUUID();

        PaymentAttempt attempt = createAttempt(
                payment,
                1,
                "MOCK",
                gatewayReference,
                PaymentAttemptStatus.SUCCEEDED
        );

        paymentAttemptRepository.saveAndFlush(attempt);

        PaymentAttempt retrievedAttempt =
                paymentAttemptRepository
                        .findByGatewayNameAndGatewayReference(
                                "MOCK",
                                gatewayReference
                        )
                        .orElseThrow();

        assertEquals(attempt.getId(), retrievedAttempt.getId());
        assertEquals(payment.getId(), retrievedAttempt.getPayment().getId());
        assertEquals(PaymentAttemptStatus.SUCCEEDED, retrievedAttempt.getStatus());
    }

    @Test
    void shouldRejectDuplicateAttemptNumberForSamePayment() {

        Payment payment = paymentRepository.saveAndFlush(
                createPayment(customer, merchant, generateReference())
        );

        PaymentAttempt firstAttempt = createAttempt(
                payment,
                1,
                "RAZORPAY",
                "razorpay-" + UUID.randomUUID(),
                PaymentAttemptStatus.FAILED
        );

        paymentAttemptRepository.saveAndFlush(firstAttempt);

        PaymentAttempt duplicateAttempt = createAttempt(
                payment,
                1,
                "STRIPE",
                "stripe-" + UUID.randomUUID(),
                PaymentAttemptStatus.SUCCEEDED
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> paymentAttemptRepository.saveAndFlush(duplicateAttempt)
        );
    }

    // =========================================
    // HELPER METHODS
    // =========================================

    private Payment createPayment(
            User customer,
            User merchant,
            String paymentReference
    ) {

        return new PaymentBuilder()
                .customer(customer)
                .merchant(merchant)
                .paymentReference(paymentReference)
                .build();
    }

    private PaymentAttempt createAttempt(
            Payment payment,
            int attemptNumber,
            String gatewayName,
            String gatewayReference,
            PaymentAttemptStatus status
    ) {

        PaymentAttempt attempt = new PaymentAttempt();

        attempt.setPayment(payment);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setGatewayName(gatewayName);
        attempt.setGatewayReference(gatewayReference);
        attempt.setStatus(status);
        attempt.setStartedAt(OffsetDateTime.now());

        if (status == PaymentAttemptStatus.SUCCEEDED
                || status == PaymentAttemptStatus.FAILED) {
            attempt.setCompletedAt(OffsetDateTime.now());
        }

        return attempt;
    }

    private String generateReference() {
        return "PAY-" + UUID.randomUUID();
    }

    // Helper for creating Payment without adding an all-args constructor
    private static class PaymentBuilder {

        private final Payment payment = new Payment();

        PaymentBuilder customer(User customer) {
            payment.setCustomer(customer);
            return this;
        }

        PaymentBuilder merchant(User merchant) {
            payment.setMerchant(merchant);
            return this;
        }

        PaymentBuilder paymentReference(String reference) {
            payment.setPaymentReference(reference);
            return this;
        }

        Payment build() {
            payment.setAmount(new BigDecimal("2500.00"));
            payment.setCurrency("INR");
            payment.setStatus(PaymentStatus.CREATED);
            payment.setPaymentMethod(PaymentMethod.UPI);
            payment.setDescription("Integration test payment");
            return payment;
        }
    }
}
