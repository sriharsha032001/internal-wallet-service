package com.internal_wallet.internal_wallet.controller;

import com.internal_wallet.internal_wallet.dto.BonusRequest;
import com.internal_wallet.internal_wallet.dto.SpendRequest;
import com.internal_wallet.internal_wallet.dto.TopupRequest;
import com.internal_wallet.internal_wallet.dto.TransactionResponse;
import com.internal_wallet.internal_wallet.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/topup")
    public ResponseEntity<TransactionResponse> topUp(
            @RequestHeader("Idempotency-Key")
            @Size(min = 16, max = 64, message = "Idempotency-Key must be 16–64 characters")
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$",
                     message = "Idempotency-Key must contain only alphanumeric characters, hyphens, or underscores")
            String idempotencyKey,
            @Valid @RequestBody TopupRequest request) {

        return ResponseEntity.ok(executeWithIdempotency(
                idempotencyKey,
                () -> transactionService.topUp(request, idempotencyKey)));
    }

    @PostMapping("/bonus")
    public ResponseEntity<TransactionResponse> bonus(
            @RequestHeader("Idempotency-Key")
            @Size(min = 16, max = 64, message = "Idempotency-Key must be 16–64 characters")
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$",
                     message = "Idempotency-Key must contain only alphanumeric characters, hyphens, or underscores")
            String idempotencyKey,
            @Valid @RequestBody BonusRequest request) {

        return ResponseEntity.ok(executeWithIdempotency(
                idempotencyKey,
                () -> transactionService.bonus(request, idempotencyKey)));
    }

    @PostMapping("/spend")
    public ResponseEntity<TransactionResponse> spend(
            @RequestHeader("Idempotency-Key")
            @Size(min = 16, max = 64, message = "Idempotency-Key must be 16–64 characters")
            @Pattern(regexp = "^[a-zA-Z0-9_-]+$",
                     message = "Idempotency-Key must contain only alphanumeric characters, hyphens, or underscores")
            String idempotencyKey,
            @Valid @RequestBody SpendRequest request) {

        return ResponseEntity.ok(executeWithIdempotency(
                idempotencyKey,
                () -> transactionService.spend(request, idempotencyKey)));
    }

    // two concurrent requests with the same key will race to insert the idempotency record
    // the loser gets a DataIntegrityViolationException — we catch it here and re-query
    // the winner's result, so both callers get the same response
    private TransactionResponse executeWithIdempotency(
            String idempotencyKey, TransactionSupplier supplier) {

        try {
            return supplier.get();
        } catch (DataIntegrityViolationException ex) {
            return transactionService.resolveConflict(idempotencyKey)
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
