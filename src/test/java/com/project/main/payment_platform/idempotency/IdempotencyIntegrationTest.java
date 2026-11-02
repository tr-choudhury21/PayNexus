package com.project.main.payment_platform.idempotency;

import com.project.main.payment_platform.idempotency.dto.IdempotencyResult;
import com.project.main.payment_platform.idempotency.entity.IdempotencyOperation;
import com.project.main.payment_platform.idempotency.entity.IdempotencyRecord;
import com.project.main.payment_platform.idempotency.service.IdempotencyService;
import com.project.main.payment_platform.idempotency.repository.IdempotencyRecordRepository;
import com.project.main.payment_platform.user.entity.Role;
import com.project.main.payment_platform.user.entity.User;
import com.project.main.payment_platform.user.entity.UserStatus;
import com.project.main.payment_platform.user.repository.UserRepository;
import com.project.main.payment_platform.wallet.dto.WalletResponse;
import com.project.main.payment_platform.wallet.dto.WalletTransactionRequest;
import com.project.main.payment_platform.wallet.entity.LedgerEntry;
import com.project.main.payment_platform.wallet.entity.Wallet;
import com.project.main.payment_platform.wallet.repository.LedgerEntryRepository;
import com.project.main.payment_platform.wallet.repository.WalletRepository;
import com.project.main.payment_platform.wallet.service.WalletService;
import com.project.main.payment_platform.exception.IdempotencyKeyReuseException;
import com.project.main.payment_platform.exception.InsufficientBalanceException;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class IdempotencyIntegrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Test
    void repeatingSameCreditRequestShouldNotCreditWalletTwice() {

        // 1. Create a unique test user.
        User user = userRepository.save(
                User.builder()
                        .email("idempotency-" + UUID.randomUUID() + "@test.com")
                        .passwordHash("hashed-password")
                        .firstName("Idempotency")
                        .lastName("Test")
                        .role(Role.CUSTOMER)
                        .status(UserStatus.ACTIVE)
                        .build()
        );

        UUID userId = user.getId();

        // 2. Create a wallet and establish the initial balance.
        Wallet wallet = walletService.createWallet(userId, "INR");

        walletService.creditWallet(
                userId,
                "INR",
                new BigDecimal("1000"),
                "Initial balance",
                "INITIAL-" + UUID.randomUUID()
        );

        UUID walletId = wallet.getId();

        int ledgerCountBefore =
                ledgerEntryRepository
                        .findByWalletIdOrderByCreatedAtDesc(walletId)
                        .size();

        // 3. Prepare one logical credit request.
        WalletTransactionRequest request = new WalletTransactionRequest();
        request.setAmount(new BigDecimal("500"));
        request.setDescription("Integration test credit");
        request.setReferenceId("CREDIT-" + UUID.randomUUID());

        String idempotencyKey = UUID.randomUUID().toString();

        // 4. Execute the original request.
        IdempotencyResult firstResult =
                idempotencyService.executeWalletTransaction(
                        userId,
                        idempotencyKey,
                        "INR",
                        IdempotencyOperation.CREDIT,
                        request
                );

        // 5. Retry the exact same request with the same key.
        IdempotencyResult secondResult =
                idempotencyService.executeWalletTransaction(
                        userId,
                        idempotencyKey,
                        "INR",
                        IdempotencyOperation.CREDIT,
                        request
                );

        // 6. Verify that both calls return the same successful result.
        assertEquals(200, firstResult.status());
        assertEquals(200, secondResult.status());

        WalletResponse firstResponse = firstResult.body();
        WalletResponse secondResponse = secondResult.body();

        assertEquals(firstResponse.getId(), secondResponse.getId());
        assertEquals(
                0,
                firstResponse.getBalance()
                        .compareTo(secondResponse.getBalance())
        );

        // 7. Verify the persisted wallet balance.
        Wallet finalWallet = walletRepository.findById(walletId)
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("1500")
                        .compareTo(finalWallet.getBalance()),
                "The wallet should be credited only once"
        );

        // 8. Verify that exactly one new ledger entry was created.
        List<LedgerEntry> ledgerEntries =
                ledgerEntryRepository
                        .findByWalletIdOrderByCreatedAtDesc(walletId);

        assertEquals(
                ledgerCountBefore + 1,
                ledgerEntries.size(),
                "The retry must not create another ledger entry"
        );

        long matchingEntries = ledgerEntries.stream()
                .filter(entry ->
                        request.getReferenceId().equals(entry.getReferenceId()))
                .count();

        assertEquals(
                1,
                matchingEntries,
                "Exactly one ledger entry should exist for this credit"
        );
    }


    @Test
    void reusingSameKeyWithDifferentPayloadShouldThrowConflict() {

        // 1. Create a unique test user.
        User user = userRepository.save(
                User.builder()
                        .email("idempotency-mismatch-" + UUID.randomUUID() + "@test.com")
                        .passwordHash("hashed-password")
                        .firstName("Idempotency")
                        .lastName("Mismatch")
                        .role(Role.CUSTOMER)
                        .status(UserStatus.ACTIVE)
                        .build()
        );

        UUID userId = user.getId();

        // 2. Create wallet and initialize balance.
        Wallet wallet = walletService.createWallet(userId, "INR");

        walletService.creditWallet(
                userId,
                "INR",
                new BigDecimal("1000"),
                "Initial balance",
                "INITIAL-" + UUID.randomUUID()
        );

        UUID walletId = wallet.getId();

        int ledgerCountBefore =
                ledgerEntryRepository
                        .findByWalletIdOrderByCreatedAtDesc(walletId)
                        .size();

        // 3. First request: credit ₹500.
        String idempotencyKey = UUID.randomUUID().toString();

        WalletTransactionRequest firstRequest =
                new WalletTransactionRequest();

        firstRequest.setAmount(new BigDecimal("500"));
        firstRequest.setDescription("First credit");
        firstRequest.setReferenceId("REF-" + UUID.randomUUID());

        IdempotencyResult firstResult =
                idempotencyService.executeWalletTransaction(
                        userId,
                        idempotencyKey,
                        "INR",
                        IdempotencyOperation.CREDIT,
                        firstRequest
                );

        assertEquals(200, firstResult.status());

        // 4. Second request: same key, but different amount.
        WalletTransactionRequest secondRequest =
                new WalletTransactionRequest();

        secondRequest.setAmount(new BigDecimal("800"));
        secondRequest.setDescription("Different credit");
        secondRequest.setReferenceId("REF-" + UUID.randomUUID());

        // 5. The second request must throw a conflict exception.
        assertThrows(
                IdempotencyKeyReuseException.class,
                () -> idempotencyService.executeWalletTransaction(
                        userId,
                        idempotencyKey,
                        "INR",
                        IdempotencyOperation.CREDIT,
                        secondRequest
                )
        );

        // 6. Verify wallet balance remains ₹1500.
        Wallet finalWallet = walletRepository.findById(walletId)
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("1500").compareTo(finalWallet.getBalance())
        );

        // 7. Verify only one new ledger entry exists.
        int ledgerCountAfter =
                ledgerEntryRepository
                        .findByWalletIdOrderByCreatedAtDesc(walletId)
                        .size();

        assertEquals(ledgerCountBefore + 1, ledgerCountAfter);
    }



    @Test
    void failedDebitShouldRollbackIdempotencyRecordAndLedgerEntry() {

        // 1. Create a unique test user.
        User user = userRepository.save(
                User.builder()
                        .email("idempotency-failure-" + UUID.randomUUID() + "@test.com")
                        .passwordHash("hashed-password")
                        .firstName("Idempotency")
                        .lastName("Failure")
                        .role(Role.CUSTOMER)
                        .status(UserStatus.ACTIVE)
                        .build()
        );

        UUID userId = user.getId();

        // 2. Create wallet with ₹1000.
        Wallet wallet = walletService.createWallet(userId, "INR");

        walletService.creditWallet(
                userId,
                "INR",
                new BigDecimal("1000"),
                "Initial balance",
                "INITIAL-" + UUID.randomUUID()
        );

        UUID walletId = wallet.getId();

        int ledgerCountBefore =
                ledgerEntryRepository
                        .findByWalletIdOrderByCreatedAtDesc(walletId)
                        .size();

        // 3. Prepare a debit request exceeding the balance.
        String idempotencyKey = UUID.randomUUID().toString();

        WalletTransactionRequest request =
                new WalletTransactionRequest();

        request.setAmount(new BigDecimal("1500"));
        request.setDescription("Insufficient balance debit");
        request.setReferenceId("DEBIT-" + UUID.randomUUID());

        // 4. Execute and expect insufficient balance exception.
        assertThrows(
                InsufficientBalanceException.class,
                () -> idempotencyService.executeWalletTransaction(
                        userId,
                        idempotencyKey,
                        "INR",
                        IdempotencyOperation.DEBIT,
                        request
                )
        );

        // 5. Verify wallet balance is unchanged.
        Wallet finalWallet = walletRepository.findById(walletId)
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("1000").compareTo(finalWallet.getBalance()),
                "Failed debit must not change wallet balance"
        );

        // 6. Verify no ledger entry was created.
        int ledgerCountAfter =
                ledgerEntryRepository
                        .findByWalletIdOrderByCreatedAtDesc(walletId)
                        .size();

        assertEquals(
                ledgerCountBefore,
                ledgerCountAfter,
                "Failed debit must not create a ledger entry"
        );

        // 7. Verify the idempotency record was rolled back.
        assertTrue(
                idempotencyRecordRepository
                        .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                        .isEmpty(),
                "Failed transaction must not leave an idempotency record"
        );
    }


    @Test
    void concurrentRequestsWithSameKeyShouldExecuteOnlyOnce() throws Exception {

        // 1. Create a unique test user.
        User user = userRepository.save(
                User.builder()
                        .email("idempotency-concurrent-" + UUID.randomUUID() + "@test.com")
                        .passwordHash("hashed-password")
                        .firstName("Idempotency")
                        .lastName("Concurrent")
                        .role(Role.CUSTOMER)
                        .status(UserStatus.ACTIVE)
                        .build()
        );

        UUID userId = user.getId();

        // 2. Create wallet and initialize balance.
        Wallet wallet = walletService.createWallet(userId, "INR");

        walletService.creditWallet(
                userId,
                "INR",
                new BigDecimal("1000"),
                "Initial balance",
                "INITIAL-" + UUID.randomUUID()
        );

        UUID walletId = wallet.getId();

        int ledgerCountBefore =
                ledgerEntryRepository
                        .findByWalletIdOrderByCreatedAtDesc(walletId)
                        .size();

        // 3. Prepare the same request and key for both threads.
        String idempotencyKey = UUID.randomUUID().toString();

        WalletTransactionRequest request = new WalletTransactionRequest();
        request.setAmount(new BigDecimal("500"));
        request.setDescription("Concurrent credit");
        request.setReferenceId("CONCURRENT-" + UUID.randomUUID());

        // 4. Synchronize both threads so they start together.
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            // Request A
            Future<IdempotencyResult> requestA = executor.submit(() -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start signal timed out");
                }

                return idempotencyService.executeWalletTransaction(
                        userId,
                        idempotencyKey,
                        "INR",
                        IdempotencyOperation.CREDIT,
                        request
                );
            });

            // Request B
            Future<IdempotencyResult> requestB = executor.submit(() -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start signal timed out");
                }

                return idempotencyService.executeWalletTransaction(
                        userId,
                        idempotencyKey,
                        "INR",
                        IdempotencyOperation.CREDIT,
                        request
                );
            });

            // Wait until both threads are ready.
            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both requests should become ready"
            );

            // Release both threads.
            start.countDown();

            // Wait for both requests to complete.
            IdempotencyResult resultA =
                    requestA.get(30, TimeUnit.SECONDS);

            IdempotencyResult resultB =
                    requestB.get(30, TimeUnit.SECONDS);

            // 5. Both requests should succeed.
            assertEquals(200, resultA.status());
            assertEquals(200, resultB.status());

            // Both should receive the same wallet response.
            assertEquals(resultA.body().getId(), resultB.body().getId());

            assertEquals(
                    0,
                    resultA.body().getBalance()
                            .compareTo(resultB.body().getBalance())
            );

        } finally {
            executor.shutdownNow();
        }

        // 6. Verify final wallet balance.
        Wallet finalWallet = walletRepository.findById(walletId)
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("1500")
                        .compareTo(finalWallet.getBalance()),
                "Concurrent duplicate requests must credit only once"
        );

        // 7. Verify exactly one new ledger entry.
        int ledgerCountAfter =
                ledgerEntryRepository
                        .findByWalletIdOrderByCreatedAtDesc(walletId)
                        .size();

        assertEquals(
                ledgerCountBefore + 1,
                ledgerCountAfter,
                "Only one ledger entry should be created"
        );

        // 8. Verify exactly one idempotency record exists.
        assertTrue(
                idempotencyRecordRepository
                        .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                        .isPresent()
        );
    }



}
