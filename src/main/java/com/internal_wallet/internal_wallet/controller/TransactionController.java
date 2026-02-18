package com.internal_wallet.internal_wallet.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internal_wallet.internal_wallet.dto.BonusRequest;
import com.internal_wallet.internal_wallet.dto.SpendRequest;
import com.internal_wallet.internal_wallet.dto.TopupRequest;
import com.internal_wallet.internal_wallet.dto.TransactionResponse;
import com.internal_wallet.internal_wallet.repository.IdempotencyKeyRepository;
import com.internal_wallet.internal_wallet.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    /**
     * POST /api/v1/transactions/topup
     * Header: Idempotency-Key (required)
     *
     * Credits a user's wallet from TREASURY.  If the same Idempotency-Key is
     * replayed the original response is returned without any side effects.
     *
     * Concurrent duplicate handling:
     *   If two requests arrive simultaneously with the same key, only one will
     *   succeed in inserting the idempotency record; the other will receive a
     *   DataIntegrityViolationException from PostgreSQL's unique index, which is
     *   caught here.  By the time the constraint fires the winning transaction
     *   has already committed, so re-querying the key is guaranteed to find it.
     */
    @PostMapping("/topup")
    public ResponseEntity<TransactionResponse> topUp(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TopupRequest request) {

        return ResponseEntity.ok(executeWithIdempotency(
                idempotencyKey,
                () -> transactionService.topUp(request, idempotencyKey)));
    }

    /**
     * POST /api/v1/transactions/bonus
     * Header: Idempotency-Key (required)
     *
     * Issues free credits to a user's wallet from TREASURY.
     */
    @PostMapping("/bonus")
    public ResponseEntity<TransactionResponse> bonus(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BonusRequest request) {

        return ResponseEntity.ok(executeWithIdempotency(
                idempotencyKey,
                () -> transactionService.bonus(request, idempotencyKey)));
    }

    /**
     * POST /api/v1/transactions/spend
     * Header: Idempotency-Key (required)
     *
     * Debits a user's wallet and credits REVENUE.
     * Returns 422 if the user has insufficient balance.
     */
    @PostMapping("/spend")
    public ResponseEntity<TransactionResponse> spend(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SpendRequest request) {

        return ResponseEntity.ok(executeWithIdempotency(
                idempotencyKey,
                () -> transactionService.spend(request, idempotencyKey)));
    }

    // ---------------------------------------------------------------
    // Idempotency conflict resolver
    // ---------------------------------------------------------------

    /**
     * Executes the given transaction supplier and handles the concurrent
     * duplicate-key edge case transparently.
     *
     * When two requests race with the same Idempotency-Key:
     *   1. Both enter the @Transactional service method.
     *   2. Both find no existing key (the winner hasn't committed yet).
     *   3. Both process the transaction.
     *   4. The winner commits first; the loser hits a unique-constraint
     *      violation on the idempotency_keys table.
     *   5. The loser's entire transaction is rolled back by Spring.
     *   6. We catch DataIntegrityViolationException here (outside any
     *      transaction), re-query the now-committed key, and return the
     *      winner's response — making both callers receive identical results.
     */
    private TransactionResponse executeWithIdempotency(
            String idempotencyKey, TransactionSupplier supplier) {

        try {
            return supplier.get();
        } catch (DataIntegrityViolationException ex) {
            return idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                    .map(ik -> {
                        try {
                            return objectMapper.readValue(ik.getResponseBody(), TransactionResponse.class);
                        } catch (JsonProcessingException e) {
                            throw new IllegalStateException(
                                    "Failed to deserialize cached idempotency response", e);
                        }
                    })
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Concurrent request conflict. Please retry."));
        }
    }

    @FunctionalInterface
    private interface TransactionSupplier {
        TransactionResponse get();
    }
}
