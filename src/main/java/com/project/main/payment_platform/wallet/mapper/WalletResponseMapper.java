package com.project.main.payment_platform.wallet.mapper;


import com.project.main.payment_platform.wallet.dto.WalletResponse;
import com.project.main.payment_platform.wallet.entity.Wallet;
import org.springframework.stereotype.Component;

@Component
public class WalletResponseMapper {

    public WalletResponse toResponse(Wallet wallet) {
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
}
