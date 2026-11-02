package com.project.main.payment_platform.idempotency.repository;

import com.project.main.payment_platform.idempotency.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(
            UUID userId,
            String idempotencyKey
    );

    @Modifying
    @Query(value = """
            INSERT INTO idempotency_records (
                id,
                user_id,
                idempotency_key,
                operation,
                request_hash,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :userId,
                :idempotencyKey,
                :operation,
                :requestHash,
                :now,
                :now
            )
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int tryCreateRecord(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("operation") String operation,
            @Param("requestHash") String requestHash,
            @Param("now") OffsetDateTime now
    );
}
