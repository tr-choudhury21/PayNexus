package com.project.main.payment_platform.wallet.service;

import com.project.main.payment_platform.user.entity.User;
import com.project.main.payment_platform.user.repository.UserRepository;
import com.project.main.payment_platform.wallet.entity.LedgerEntry;
import com.project.main.payment_platform.wallet.entity.LedgerEntryType;
import com.project.main.payment_platform.wallet.entity.Wallet;
import com.project.main.payment_platform.wallet.entity.WalletStatus;
import com.project.main.payment_platform.wallet.repository.LedgerEntryRepository;
import com.project.main.payment_platform.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Wallet createWallet(UUID userId, String currency) {

        if (walletRepository.existsByUserIdAndCurrency(
                userId,
                currency)) {

            throw new IllegalStateException(
                    "Wallet already exists for this currency"
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found"
                        )
                );

        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .currency(currency)
                .status(WalletStatus.ACTIVE)
                .build();

        return walletRepository.save(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public Wallet getWallet(
            UUID userId,
            String currency) {

        return walletRepository
                .findByUserIdAndCurrency(userId, currency)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Wallet not found"
                        )
                );
    }

    @Override
    @Transactional
    public Wallet creditWallet(
            UUID userId,
            String currency,
            BigDecimal amount,
            String description,
            String referenceId) {

        validateAmount(amount);

        Wallet wallet = getWallet(userId, currency);

        validateWallet(wallet);

        wallet.setBalance(
                wallet.getBalance().add(amount)
        );

        Wallet savedWallet = walletRepository.save(wallet);

        LedgerEntry ledgerEntry = LedgerEntry.builder()
                .wallet(savedWallet)
                .amount(amount)
                .entryType(LedgerEntryType.CREDIT)
                .currency(currency)
                .description(description)
                .referenceId(referenceId)
                .build();

        ledgerEntryRepository.save(ledgerEntry);

        return savedWallet;
    }

    @Override
    @Transactional
    public Wallet debitWallet(
            UUID userId,
            String currency,
            BigDecimal amount,
            String description,
            String referenceId) {

        validateAmount(amount);

        Wallet wallet = getWallet(userId, currency);

        validateWallet(wallet);

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Insufficient wallet balance"
            );
        }

        wallet.setBalance(
                wallet.getBalance().subtract(amount)
        );

        Wallet savedWallet = walletRepository.save(wallet);

        LedgerEntry ledgerEntry = LedgerEntry.builder()
                .wallet(savedWallet)
                .amount(amount)
                .entryType(LedgerEntryType.DEBIT)
                .currency(currency)
                .description(description)
                .referenceId(referenceId)
                .build();

        ledgerEntryRepository.save(ledgerEntry);

        return savedWallet;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntry> getLedgerHistory(
            UUID userId,
            String currency) {

        Wallet wallet = getWallet(userId, currency);

        return ledgerEntryRepository
                .findByWalletIdOrderByCreatedAtDesc(
                        wallet.getId()
                );
    }

    private void validateAmount(BigDecimal amount) {

        if (amount == null ||
                amount.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Amount must be greater than zero"
            );
        }
    }

    private void validateWallet(Wallet wallet) {

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Wallet is not active"
            );
        }
    }
}
