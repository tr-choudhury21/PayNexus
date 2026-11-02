package com.project.main.payment_platform.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.project.main.payment_platform.exception.IdempotencyKeyReuseException;
import com.project.main.payment_platform.idempotency.dto.IdempotencyResult;
import com.project.main.payment_platform.idempotency.entity.IdempotencyOperation;
import com.project.main.payment_platform.idempotency.entity.IdempotencyRecord;
import com.project.main.payment_platform.idempotency.repository.IdempotencyRecordRepository;
import com.project.main.payment_platform.user.repository.UserRepository;
import com.project.main.payment_platform.wallet.dto.WalletResponse;
import com.project.main.payment_platform.wallet.dto.WalletTransactionRequest;
import com.project.main.payment_platform.wallet.entity.Wallet;
import com.project.main.payment_platform.wallet.mapper.WalletResponseMapper;
import com.project.main.payment_platform.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final WalletResponseMapper walletResponseMapper;
    private final RequestHashService requestHashService;
    private final JsonMapper objectMapper;

    @Transactional
    public IdempotencyResult executeWalletTransaction(
            UUID userId,
            String idempotencyKey,
            String currency,
            IdempotencyOperation operation,
            WalletTransactionRequest request
    ) {
        validateKey(idempotencyKey);

        String normalizedCurrency =
                currency.toUpperCase(Locale.ROOT);

        BigDecimal normalizedAmount =
                request.getAmount().stripTrailingZeros();

        String requestHash = requestHashService.generateHash(
                userId,
                operation.name(),
                normalizedCurrency,
                normalizedAmount.toPlainString(),
                request.getDescription(),
                request.getReferenceId()
        );

        int claimed = idempotencyRepository.tryCreateRecord(
                UUID.randomUUID(),
                userId,
                idempotencyKey,
                operation.name(),
                requestHash,
                OffsetDateTime.now()
        );

        if (claimed == 0) {
            return replayExistingRequest(
                    userId,
                    idempotencyKey,
                    requestHash
            );
        }

        IdempotencyRecord record =
                idempotencyRepository
                        .findByUserIdAndIdempotencyKey(
                                userId,
                                idempotencyKey
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Claimed idempotency record was not found"
                                )
                        );

        // Execute the existing wallet business logic.
        Wallet wallet = switch (operation) {
            case CREDIT -> walletService.creditWallet(
                    userId,
                    normalizedCurrency,
                    request.getAmount(),
                    request.getDescription(),
                    request.getReferenceId()
            );

            case DEBIT -> walletService.debitWallet(
                    userId,
                    normalizedCurrency,
                    request.getAmount(),
                    request.getDescription(),
                    request.getReferenceId()
            );
        };

        WalletResponse response =
                walletResponseMapper.toResponse(wallet);

        record.setResponseStatus(200);
        record.setResponseBody(serialize(response));

        idempotencyRepository.save(record);

        return new IdempotencyResult(200, response);
    }

    private IdempotencyResult replayExistingRequest(
            UUID userId,
            String idempotencyKey,
            String requestHash
    ) {
        IdempotencyRecord existingRecord =
                idempotencyRepository
                        .findByUserIdAndIdempotencyKey(
                                userId,
                                idempotencyKey
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Idempotency record was not found"
                                )
                        );

        if (!existingRecord.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReuseException();
        }

        if (existingRecord.getResponseStatus() == null
                || existingRecord.getResponseBody() == null) {
            throw new IllegalStateException(
                    "Idempotency record has no completed response"
            );
        }

        return new IdempotencyResult(
                existingRecord.getResponseStatus(),
                deserialize(existingRecord.getResponseBody())
        );
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 255) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must contain between 1 and 255 characters"
            );
        }
    }

    private String serialize(WalletResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JacksonException ex) {
            throw new IllegalStateException(
                    "Could not serialize wallet response",
                    ex
            );
        }
    }

    private WalletResponse deserialize(String responseBody) {
        try {
            return objectMapper.readValue(
                    responseBody,
                    WalletResponse.class
            );
        } catch (JacksonException ex) {

            System.out.println("Stored JSON response:");
            System.out.println(responseBody);

            throw new IllegalStateException(
                    "Could not deserialize stored wallet response",
                    ex
            );
        }
    }
}
