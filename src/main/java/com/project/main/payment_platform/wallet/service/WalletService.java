package com.project.main.payment_platform.wallet.service;

import com.project.main.payment_platform.wallet.entity.LedgerEntry;
import com.project.main.payment_platform.wallet.entity.Wallet;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface WalletService {

    Wallet createWallet(UUID userId, String currency);

    Wallet getWallet(UUID userId, String currency);

    Wallet creditWallet(
            UUID userId,
            String currency,
            BigDecimal amount,
            String description,
            String referenceId
    );

    Wallet debitWallet(
            UUID userId,
            String currency,
            BigDecimal amount,
            String description,
            String referenceId
    );

    List<LedgerEntry> getLedgerHistory(
            UUID userId,
            String currency
    );
}
