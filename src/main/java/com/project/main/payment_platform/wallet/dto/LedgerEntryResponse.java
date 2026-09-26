package com.project.main.payment_platform.wallet.dto;


import com.project.main.payment_platform.wallet.entity.LedgerEntryType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class LedgerEntryResponse {

    private UUID id;
    private BigDecimal amount;
    private LedgerEntryType entryType;
    private String currency;
    private String description;
    private String referenceId;
    private OffsetDateTime createdAt;
}
