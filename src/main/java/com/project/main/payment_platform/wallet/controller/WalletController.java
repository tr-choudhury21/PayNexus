package com.project.main.payment_platform.wallet.controller;


import com.project.main.payment_platform.security.SecurityUtils;
import com.project.main.payment_platform.wallet.dto.CreateWalletRequest;
import com.project.main.payment_platform.wallet.dto.LedgerEntryResponse;
import com.project.main.payment_platform.wallet.dto.WalletResponse;
import com.project.main.payment_platform.wallet.dto.WalletTransactionRequest;
import com.project.main.payment_platform.wallet.entity.LedgerEntry;
import com.project.main.payment_platform.wallet.entity.Wallet;
import com.project.main.payment_platform.wallet.mapper.WalletResponseMapper;
import com.project.main.payment_platform.wallet.service.WalletService;
import com.project.main.payment_platform.idempotency.dto.IdempotencyResult;
import com.project.main.payment_platform.idempotency.entity.IdempotencyOperation;
import com.project.main.payment_platform.idempotency.service.IdempotencyService;
import com.project.main.payment_platform.wallet.mapper.WalletResponseMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final SecurityUtils securityUtils;
    private final IdempotencyService idempotencyService;
    private final WalletResponseMapper walletResponseMapper;


    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(
            @Valid @RequestBody CreateWalletRequest request) {

        UUID userId = securityUtils.getCurrentUserId();

        Wallet wallet = walletService.createWallet(
                userId,
                request.getCurrency()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(walletResponseMapper.toResponse(wallet));
    }

    @GetMapping("/{currency}")
    public ResponseEntity<WalletResponse> getWallet(
            @PathVariable String currency){

    UUID userId = securityUtils.getCurrentUserId();

        Wallet wallet = walletService.getWallet(
                userId,
                currency.toUpperCase()
        );

        return ResponseEntity.ok(
                walletResponseMapper.toResponse(wallet)
        );
    }

    @PostMapping("/{currency}/credit")
    public ResponseEntity<WalletResponse> creditWallet(
            @PathVariable String currency,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WalletTransactionRequest request) {

        UUID userId = securityUtils.getCurrentUserId();

        IdempotencyResult result =
                idempotencyService.executeWalletTransaction(
                        userId,
                        idempotencyKey,
                        currency.toUpperCase(Locale.ROOT),
                        IdempotencyOperation.CREDIT,
                        request
                );


        return ResponseEntity
                .status(result.status())
                .body(result.body());
    }

    @PostMapping("/{currency}/debit")
    public ResponseEntity<WalletResponse> debitWallet(
            @PathVariable String currency,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WalletTransactionRequest request) {

        UUID userId = securityUtils.getCurrentUserId();

        IdempotencyResult result =
                idempotencyService.executeWalletTransaction(
                        userId,
                        idempotencyKey,
                        currency.toUpperCase(Locale.ROOT),
                        IdempotencyOperation.DEBIT,
                        request
                );

        return ResponseEntity
                .status(result.status())
                .body(result.body());
    }

    @GetMapping("/{currency}/ledger")
    public ResponseEntity<List<LedgerEntryResponse>> getLedgerHistory(
            @PathVariable String currency) {

        UUID userId = securityUtils.getCurrentUserId();

        List<LedgerEntry> entries =
                walletService.getLedgerHistory(
                        userId,
                        currency.toUpperCase()
                );

        List<LedgerEntryResponse> response =
                entries.stream()
                        .map(this::toLedgerEntryResponse)
                        .toList();

        return ResponseEntity.ok(response);
    }

    private WalletResponse toWalletResponse(Wallet wallet) {

        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUser().getId())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    private LedgerEntryResponse toLedgerEntryResponse(
            LedgerEntry entry) {

        return LedgerEntryResponse.builder()
                .id(entry.getId())
                .amount(entry.getAmount())
                .entryType(entry.getEntryType())
                .currency(entry.getCurrency())
                .description(entry.getDescription())
                .referenceId(entry.getReferenceId())
                .createdAt(entry.getCreatedAt())
                .build();
    }

}
