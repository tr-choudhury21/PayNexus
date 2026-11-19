package com.project.main.payment_platform.payment;


import com.project.main.payment_platform.exception.InvalidPaymentStatusException;
import com.project.main.payment_platform.exception.PaymentNotFoundException;
import com.project.main.payment_platform.exception.PaymentOwnershipException;
import com.project.main.payment_platform.payment.dto.PaymentAttemptResponse;
import com.project.main.payment_platform.payment.entity.Payment;
import com.project.main.payment_platform.payment.entity.PaymentAttempt;
import com.project.main.payment_platform.payment.enums.PaymentAttemptStatus;
import com.project.main.payment_platform.payment.enums.PaymentMethod;
import com.project.main.payment_platform.payment.enums.PaymentStatus;
import com.project.main.payment_platform.payment.repository.PaymentAttemptRepository;
import com.project.main.payment_platform.payment.repository.PaymentRepository;
import com.project.main.payment_platform.payment.service.PaymentAttemptService;
import com.project.main.payment_platform.security.CustomUserDetails;
import com.project.main.payment_platform.user.entity.Role;
import com.project.main.payment_platform.user.entity.User;
import com.project.main.payment_platform.user.entity.UserStatus;
import com.project.main.payment_platform.user.repository.UserRepository;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class PaymentAttemptServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("payment_platform")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .withEnv("TZ", "Asia/Kolkata")
                    .withEnv("PGTZ", "Asia/Kolkata");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> postgres.getJdbcUrl()
                        + "?options=-c%20TimeZone=Asia%2FKolkata");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PaymentAttemptService paymentAttemptService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private UserRepository userRepository;

    private User customer;
    private User merchant;
    private Payment payment;

    private User createCustomer() {

        User customer = new User();

        customer.setEmail("customer-" + UUID.randomUUID() + "@test.com");
        customer.setPasswordHash("encoded-password");
        customer.setFirstName("Test");
        customer.setLastName("Customer");
        customer.setRole(Role.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);

        return userRepository.saveAndFlush(customer);
    }

    private User createMerchant() {

        User merchant = new User();

        merchant.setEmail("merchant-" + UUID.randomUUID() + "@test.com");
        merchant.setPasswordHash("encoded-password");
        merchant.setFirstName("Test");
        merchant.setLastName("Merchant");
        merchant.setRole(Role.MERCHANT);
        merchant.setStatus(UserStatus.ACTIVE);

        return userRepository.saveAndFlush(merchant);
    }

    private void authenticateUser(User user) {

        CustomUserDetails userDetails = new CustomUserDetails(user);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @BeforeEach
    void setUp() {

        customer = createCustomer();
        merchant = createMerchant();

        payment = new Payment();
        payment.setPaymentReference("PAY-" + UUID.randomUUID());
        payment.setCustomer(customer);
        payment.setMerchant(merchant);
        payment.setAmount(new BigDecimal("2500.00"));
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.CREATED);
        payment.setPaymentMethod(PaymentMethod.UPI);
        payment.setDescription("Integration test payment");

        payment = paymentRepository.saveAndFlush(payment);

        authenticateUser(customer);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreatePaymentAttemptSuccessfully() {

        PaymentAttemptResponse response =
                paymentAttemptService.createPaymentAttempt(payment.getId());

        // Verify response
        assertNotNull(response.getId());
        assertEquals(payment.getId(), response.getPaymentId());
        assertEquals(1, response.getAttemptNumber());
        assertEquals("MOCK", response.getGatewayName());
        assertEquals(PaymentAttemptStatus.INITIATED, response.getStatus());

        // Verify attempt persisted
        PaymentAttempt savedAttempt =
                paymentAttemptRepository.findById(response.getId())
                        .orElseThrow();

        assertEquals(payment.getId(), savedAttempt.getPayment().getId());
        assertEquals(1, savedAttempt.getAttemptNumber());

        // Verify parent payment updated
        Payment updatedPayment =
                paymentRepository.findById(payment.getId())
                        .orElseThrow();

        assertEquals(PaymentStatus.PROCESSING, updatedPayment.getStatus());
    }

    @Test
    void shouldThrowExceptionWhenPaymentDoesNotExist() {

        UUID nonExistingPaymentId = UUID.randomUUID();

        long attemptsBefore = paymentAttemptRepository.count();

        assertThrows(
                PaymentNotFoundException.class,
                () -> paymentAttemptService.createPaymentAttempt(UUID.randomUUID())
        );

        assertEquals(attemptsBefore, paymentAttemptRepository.count());

    }

    @Test
    void shouldThrowExceptionWhenCustomerDoesNotOwnPayment() {

        User anotherCustomer = createCustomer();
        authenticateUser(anotherCustomer);

        long attemptsBefore = paymentAttemptRepository.count();

        assertThrows(
                PaymentOwnershipException.class,
                () -> paymentAttemptService.createPaymentAttempt(payment.getId())
        );

        assertEquals(attemptsBefore, paymentAttemptRepository.count());

        Payment unchangedPayment =
                paymentRepository.findById(payment.getId()).orElseThrow();

        assertEquals(PaymentStatus.CREATED, unchangedPayment.getStatus());
    }

    @Test
    void shouldThrowExceptionWhenPaymentIsAlreadySucceeded() {

        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.saveAndFlush(payment);

        long attemptsBefore = paymentAttemptRepository.count();

        assertThrows(
                InvalidPaymentStatusException.class,
                () -> paymentAttemptService.createPaymentAttempt(payment.getId())
        );

        assertEquals(attemptsBefore, paymentAttemptRepository.count());
    }

    @Test
    void shouldCreateNextAttemptWhenPaymentIsRetried() {

        // Simulate the first failed attempt
        PaymentAttempt firstAttempt = new PaymentAttempt();

        firstAttempt.setPayment(payment);
        firstAttempt.setAttemptNumber(1);
        firstAttempt.setGatewayName("MOCK");
        firstAttempt.setStatus(PaymentAttemptStatus.FAILED);

        paymentAttemptRepository.saveAndFlush(firstAttempt);

        // Mark the overall payment as failed
        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.saveAndFlush(payment);

        // Initiate retry
        PaymentAttemptResponse response =
                paymentAttemptService.createPaymentAttempt(payment.getId());

        // Verify second attempt
        assertEquals(2, response.getAttemptNumber());
        assertEquals(PaymentAttemptStatus.INITIATED, response.getStatus());
        assertEquals("MOCK", response.getGatewayName());

        // Verify both attempts exist
        List<PaymentAttempt> attempts =
                paymentAttemptRepository
                        .findAllByPayment_IdOrderByAttemptNumberAsc(payment.getId());

        assertEquals(2, attempts.size());
        assertEquals(1, attempts.get(0).getAttemptNumber());
        assertEquals(2, attempts.get(1).getAttemptNumber());

        // Verify parent payment is processing again
        Payment updatedPayment =
                paymentRepository.findById(payment.getId()).orElseThrow();

        assertEquals(PaymentStatus.PROCESSING, updatedPayment.getStatus());
    }


    @Test
    void shouldAllowOnlyOneConcurrentPaymentAttempt() throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            // Both threads will execute this task.
            Callable<Boolean> task = () -> {

                // SecurityContext is thread-local, so authenticate
                // independently inside each worker thread.
                authenticateUser(customer);

                try {
                    ready.countDown();

                    // Wait until both threads are ready.
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting to start concurrent requests"
                        );
                    }

                    paymentAttemptService.createPaymentAttempt(payment.getId());

                    // Request successfully created an attempt.
                    return true;

                } catch (InvalidPaymentStatusException ex) {

                    // Another request already initiated the payment.
                    return false;

                } finally {
                    SecurityContextHolder.clearContext();
                }
            };

            // Submit both requests.
            Future<Boolean> requestA = executor.submit(task);
            Future<Boolean> requestB = executor.submit(task);

            // Ensure both threads are ready before releasing them.
            assertTrue(ready.await(10, TimeUnit.SECONDS));

            // Start both requests.
            start.countDown();

            boolean resultA = requestA.get(20, TimeUnit.SECONDS);
            boolean resultB = requestB.get(20, TimeUnit.SECONDS);

            // Exactly one request should succeed.
            assertTrue(resultA ^ resultB);

            // Verify only one attempt was created.
            List<PaymentAttempt> attempts =
                    paymentAttemptRepository
                            .findAllByPayment_IdOrderByAttemptNumberAsc(
                                    payment.getId()
                            );

            assertEquals(1, attempts.size());
            assertEquals(1, attempts.get(0).getAttemptNumber());
            assertEquals(
                    PaymentAttemptStatus.INITIATED,
                    attempts.get(0).getStatus()
            );

            // Verify parent payment status.
            Payment updatedPayment =
                    paymentRepository.findById(payment.getId())
                            .orElseThrow();

            assertEquals(
                    PaymentStatus.PROCESSING,
                    updatedPayment.getStatus()
            );

        } finally {
            executor.shutdownNow();
            SecurityContextHolder.clearContext();
        }
    }

}
