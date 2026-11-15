package com.project.main.payment_platform.payment;

import com.project.main.payment_platform.exception.InactiveMerchantException;
import com.project.main.payment_platform.exception.InvalidCustomerException;
import com.project.main.payment_platform.exception.InvalidMerchantException;
import com.project.main.payment_platform.exception.MerchantNotFoundException;
import com.project.main.payment_platform.payment.dto.CreatePaymentRequest;
import com.project.main.payment_platform.payment.dto.PaymentResponse;
import com.project.main.payment_platform.payment.entity.Payment;
import com.project.main.payment_platform.payment.enums.PaymentMethod;
import com.project.main.payment_platform.payment.enums.PaymentStatus;
import com.project.main.payment_platform.payment.repository.PaymentRepository;
import com.project.main.payment_platform.payment.service.PaymentService;
import com.project.main.payment_platform.security.CustomUserDetails;
import com.project.main.payment_platform.user.entity.Role;
import com.project.main.payment_platform.user.entity.User;
import com.project.main.payment_platform.user.entity.UserStatus;
import com.project.main.payment_platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class PaymentServiceIntegrationTest {

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
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

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

        authenticateUser(customer);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateUser(User user) {

        CustomUserDetails userDetails = new CustomUserDetails(user);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }


    @Test
    void shouldCreatePaymentSuccessfully() {

        CreatePaymentRequest request = new CreatePaymentRequest();

        request.setMerchantId(merchant.getId());
        request.setAmount(new BigDecimal("2500.00"));
        request.setCurrency("INR");
        request.setPaymentMethod(PaymentMethod.UPI);
        request.setDescription("Integration test payment");

        PaymentResponse response = paymentService.createPayment(request);

        assertNotNull(response.getId());
        assertNotNull(response.getPaymentReference());

        assertEquals(customer.getId(), response.getCustomerId());
        assertEquals(merchant.getId(), response.getMerchantId());

        assertEquals(
                0,
                new BigDecimal("2500.00").compareTo(response.getAmount())
        );

        assertEquals("INR", response.getCurrency());
        assertEquals(PaymentStatus.CREATED, response.getStatus());
        assertEquals(PaymentMethod.UPI, response.getPaymentMethod());

        assertNotNull(response.getCreatedAt());

        Payment savedPayment = paymentRepository.findById(response.getId())
                .orElseThrow();

        assertEquals(response.getPaymentReference(), savedPayment.getPaymentReference());
        assertEquals(customer.getId(), savedPayment.getCustomer().getId());
        assertEquals(merchant.getId(), savedPayment.getMerchant().getId());
    }


    @Test
    void shouldThrowExceptionWhenMerchantDoesNotExist() {

        long paymentsBefore = paymentRepository.count();

        CreatePaymentRequest request = new CreatePaymentRequest();

        request.setMerchantId(UUID.randomUUID());
        request.setAmount(new BigDecimal("2500.00"));
        request.setCurrency("INR");
        request.setPaymentMethod(PaymentMethod.UPI);
        request.setDescription("Integration test payment");

        assertThrows(
                MerchantNotFoundException.class,
                () -> paymentService.createPayment(request)
        );

        assertEquals(paymentsBefore, paymentRepository.count());
    }

    @Test
    void shouldThrowExceptionWhenSelectedUserIsNotMerchant() {

        User anotherCustomer = userRepository.save(
                User.builder()
                        .email("customer-" + UUID.randomUUID() + "@test.com")
                        .passwordHash("hashed-password")
                        .firstName("Another")
                        .lastName("Customer")
                        .role(Role.CUSTOMER)
                        .status(UserStatus.ACTIVE)
                        .build()
        );

        long paymentsBefore = paymentRepository.count();

        CreatePaymentRequest request = new CreatePaymentRequest();

        request.setMerchantId(anotherCustomer.getId());
        request.setAmount(new BigDecimal("2500.00"));
        request.setCurrency("INR");
        request.setPaymentMethod(PaymentMethod.UPI);
        request.setDescription("Integration test payment");

        assertThrows(
                InvalidMerchantException.class,
                () -> paymentService.createPayment(request)
        );

        assertEquals(paymentsBefore, paymentRepository.count());
    }

    @Test
    void shouldThrowExceptionWhenMerchantIsInactive() {

        System.out.println("Merchant role: " + merchant.getRole());
        System.out.println("Merchant status: " + merchant.getStatus());

        merchant.setStatus(UserStatus.BLOCKED);
        userRepository.saveAndFlush(merchant);

        System.out.println("Merchant role: " + merchant.getRole());
        System.out.println("Merchant status: " + merchant.getStatus());

        long paymentsBefore = paymentRepository.count();

        CreatePaymentRequest request = new CreatePaymentRequest();

        request.setMerchantId(merchant.getId());
        request.setAmount(new BigDecimal("2500.00"));
        request.setCurrency("INR");
        request.setPaymentMethod(PaymentMethod.UPI);
        request.setDescription("Integration test payment");

        assertThrows(
                InactiveMerchantException.class,
                () -> paymentService.createPayment(request)
        );

        assertEquals(paymentsBefore, paymentRepository.count());
    }

    @Test
    void shouldThrowExceptionWhenCustomerIsInactive() {

        customer.setStatus(UserStatus.BLOCKED);
        userRepository.saveAndFlush(customer);

        authenticateUser(customer);

        long paymentsBefore = paymentRepository.count();

        CreatePaymentRequest request = new CreatePaymentRequest();

        request.setMerchantId(merchant.getId());
        request.setAmount(new BigDecimal("2500.00"));
        request.setCurrency("INR");
        request.setPaymentMethod(PaymentMethod.UPI);
        request.setDescription("Integration test payment");

        assertThrows(
                InvalidCustomerException.class,
                () -> paymentService.createPayment(request)
        );

        assertEquals(paymentsBefore, paymentRepository.count());
    }

    @Test
    void shouldThrowExceptionWhenAuthenticatedUserIsNotCustomer() {

        // Authenticate as an active merchant
        authenticateUser(merchant);

        long paymentsBefore = paymentRepository.count();

        CreatePaymentRequest request = new CreatePaymentRequest();

        request.setMerchantId(merchant.getId());
        request.setAmount(new BigDecimal("2500.00"));
        request.setCurrency("INR");
        request.setPaymentMethod(PaymentMethod.UPI);
        request.setDescription("Integration test payment");

        assertThrows(
                InvalidCustomerException.class,
                () -> paymentService.createPayment(request)
        );

        assertEquals(paymentsBefore, paymentRepository.count());
    }

}
