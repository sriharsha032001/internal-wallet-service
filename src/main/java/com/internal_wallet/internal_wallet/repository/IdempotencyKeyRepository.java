package com.internal_wallet.internal_wallet.repository;

import com.internal_wallet.internal_wallet.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    /**
     * O(1) — backed by UNIQUE constraint on idempotency_keys.idempotency_key.
     */
    Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);
}
