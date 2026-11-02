package com.project.main.payment_platform.idempotency.dto;

import com.project.main.payment_platform.wallet.dto.WalletResponse;

public record IdempotencyResult(

        int status,
        WalletResponse body
) {

}
