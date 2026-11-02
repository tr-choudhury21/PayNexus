package com.project.main.payment_platform.idempotency.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RequestHashService {

    public String generateHash(
            UUID userId,
            String operation,
            String currency,
            String amount,
            String description,
            String referenceId
    ) {
        String canonicalRequest = String.join(
                "\n",
                userId.toString(),
                operation,
                currency,
                amount,
                description == null ? "" : description,
                referenceId == null ? "" : referenceId
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    canonicalRequest.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    ex
            );
        }
    }
}
