package com.project.main.payment_platform.wallet.dto;

import com.project.main.payment_platform.wallet.entity.WalletStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;


@Getter
@Builder
public class WalletResponse {

    private UUID id;
    private UUID userId;
    private BigDecimal balance;
    private String currency;
    private WalletStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
