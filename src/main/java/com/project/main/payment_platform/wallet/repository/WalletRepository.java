package com.project.main.payment_platform.wallet.repository;

import com.project.main.payment_platform.wallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;


public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserIdAndCurrency(
            UUID userId,
            String currency
    );

    boolean existsByUserIdAndCurrency(
            UUID userId,
            String currency
    );
}
