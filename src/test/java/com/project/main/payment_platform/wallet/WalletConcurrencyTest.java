package com.project.main.payment_platform.wallet;


import com.project.main.payment_platform.user.entity.Role;
import com.project.main.payment_platform.user.entity.User;
import com.project.main.payment_platform.user.entity.UserStatus;
import com.project.main.payment_platform.user.repository.UserRepository;
import com.project.main.payment_platform.wallet.entity.LedgerEntry;
import com.project.main.payment_platform.wallet.entity.LedgerEntryType;
import com.project.main.payment_platform.wallet.entity.Wallet;
import com.project.main.payment_platform.wallet.entity.WalletStatus;
import com.project.main.payment_platform.wallet.repository.LedgerEntryRepository;
import com.project.main.payment_platform.wallet.repository.WalletRepository;
import com.project.main.payment_platform.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.BrokenBarrierException;

import java.math.BigDecimal;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class WalletConcurrencyTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("payment_platform")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .withEnv("TZ", "Asia/Kolkata")
                    .withEnv("PGTZ", "Asia/Kolkata");


    @DynamicPropertySource
    static void configureDatabase(
            DynamicPropertyRegistry registry) {

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
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void optimisticLockingShouldPreventLostUpdate() throws Exception {

        // Create test user
        User user = userRepository.save(
                User.builder()
                        .email("concurrency@test.com")
                        .passwordHash("hashed-password")
                        .firstName("Concurrency")
                        .lastName("Test")
                        .role(Role.CUSTOMER)
                        .status(UserStatus.ACTIVE)
                        .build()
        );

        // Create wallet
        Wallet wallet = walletService.createWallet(
                user.getId(),
                "INR"
        );

        // Initial ₹1000 credit
        walletService.creditWallet(
                user.getId(),
                "INR",
                new BigDecimal("1000"),
                "Initial balance",
                "INITIAL"
        );

        UUID walletId = wallet.getId();

        // Count ledger entries before concurrent transactions
        int ledgerCountBefore =
                ledgerEntryRepository
                        .findByWalletIdOrderByCreatedAtDesc(walletId)
                        .size();

        System.out.println(
                "Ledger count before concurrency = " + ledgerCountBefore
        );

        /*
         * Both transactions must first read version = 1
         * before either transaction attempts to update.
         */
        CyclicBarrier barrier = new CyclicBarrier(2);

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        // Request A
        Future<?> requestA = executorService.submit(() ->
                transactionTemplate.executeWithoutResult(status -> {

                    Wallet currentWallet = walletRepository.findById(walletId)
                            .orElseThrow();

                    // Both requests should read version 1
                    assertEquals(1L, currentWallet.getVersion());

                    // Wait until Request B has also read version 1
                    awaitBarrier(barrier);

                    // Add ₹500
                    currentWallet.setBalance(
                            currentWallet.getBalance()
                                    .add(new BigDecimal("500"))
                    );

                    /*
                     * Force Hibernate to execute the UPDATE immediately.
                     * This is where optimistic locking can fail.
                     */
                    walletRepository.saveAndFlush(currentWallet);

                    /*
                     * Ledger entry is created only if the wallet update
                     * succeeds.
                     */
                    LedgerEntry ledgerEntry = LedgerEntry.builder()
                            .wallet(currentWallet)
                            .amount(new BigDecimal("500"))
                            .entryType(LedgerEntryType.CREDIT)
                            .currency("INR")
                            .description("Concurrent credit A")
                            .referenceId("TEST-A")
                            .build();

                    ledgerEntryRepository.save(ledgerEntry);
                })
        );

        // Request B
        Future<?> requestB = executorService.submit(() ->
                transactionTemplate.executeWithoutResult(status -> {

                    Wallet currentWallet = walletRepository.findById(walletId)
                            .orElseThrow();

                    // Both requests should read version 1
                    assertEquals(1L, currentWallet.getVersion());

                    // Wait until Request A has also read version 1
                    awaitBarrier(barrier);

                    // Add ₹300
                    currentWallet.setBalance(
                            currentWallet.getBalance()
                                    .add(new BigDecimal("300"))
                    );

                    /*
                     * Force Hibernate to execute the UPDATE immediately.
                     * This is where optimistic locking can fail.
                     */
                    walletRepository.saveAndFlush(currentWallet);

                    /*
                     * Ledger entry is created only if the wallet update
                     * succeeds.
                     */
                    LedgerEntry ledgerEntry = LedgerEntry.builder()
                            .wallet(currentWallet)
                            .amount(new BigDecimal("300"))
                            .entryType(LedgerEntryType.CREDIT)
                            .currency("INR")
                            .description("Concurrent credit B")
                            .referenceId("TEST-B")
                            .build();

                    ledgerEntryRepository.save(ledgerEntry);
                })
        );

        int successCount = 0;
        int failureCount = 0;

        // Wait for Request A
        try {
            requestA.get();
            successCount++;
        } catch (ExecutionException ex) {
            failureCount++;
            ex.getCause().printStackTrace();
        }

        // Wait for Request B
        try {
            requestB.get();
            successCount++;
        } catch (ExecutionException ex) {
            failureCount++;
            ex.getCause().printStackTrace();
        }

        executorService.shutdown();

        // Fetch final wallet
        Wallet finalWallet = walletRepository.findById(walletId)
                .orElseThrow();

        // Fetch final ledger entries
        List<LedgerEntry> ledgerEntries =
                ledgerEntryRepository
                        .findByWalletIdOrderByCreatedAtDesc(walletId);

        int ledgerCountAfter = ledgerEntries.size();

        // Print results
        System.out.println("Final balance = " + finalWallet.getBalance());
        System.out.println("Final version = " + finalWallet.getVersion());
        System.out.println("Success count = " + successCount);
        System.out.println("Failure count = " + failureCount);
        System.out.println("Ledger count before = " + ledgerCountBefore);
        System.out.println("Ledger count after = " + ledgerCountAfter);

        ledgerEntries.forEach(entry ->
                System.out.println(
                        entry.getReferenceId() +
                                " : " +
                                entry.getAmount()
                )
        );

        // Exactly one transaction must succeed
        assertEquals(1, successCount);

        // Exactly one transaction must fail
        assertEquals(1, failureCount);

        // Only the successful transaction should create a new ledger entry
        assertEquals(
                ledgerCountBefore + 1,
                ledgerCountAfter
        );

        // Version should increase exactly once
        assertEquals(2L, finalWallet.getVersion());

        /*
         * Either Request A or Request B can win.
         *
         * A wins → ₹1500
         * B wins → ₹1300
         */
        assertTrue(
                finalWallet.getBalance()
                        .compareTo(new BigDecimal("1500")) == 0
                        ||
                        finalWallet.getBalance()
                                .compareTo(new BigDecimal("1300")) == 0
        );

        /*
         * Exactly one of TEST-A or TEST-B should exist.
         *
         * ^ is XOR:
         *
         * A = true,  B = false → valid
         * A = false, B = true  → valid
         * A = true,  B = true  → invalid
         * A = false, B = false → invalid
         */
        boolean testAExists = ledgerEntries.stream()
                .anyMatch(entry ->
                        "TEST-A".equals(entry.getReferenceId())
                );

        boolean testBExists = ledgerEntries.stream()
                .anyMatch(entry ->
                        "TEST-B".equals(entry.getReferenceId())
                );

        assertTrue(testAExists ^ testBExists);
    }

    private void awaitBarrier(CyclicBarrier barrier) {

        try {
            barrier.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        } catch (BrokenBarrierException ex) {
            throw new RuntimeException(ex);
        }
    }
}
