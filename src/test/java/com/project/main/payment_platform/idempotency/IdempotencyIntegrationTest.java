package com.project.main.payment_platform.idempotency;

import com.project.main.payment_platform.idempotency.dto.IdempotencyResult;
import com.project.main.payment_platform.idempotency.entity.IdempotencyOperation;
import com.project.main.payment_platform.idempotency.service.IdempotencyService;
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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
}
