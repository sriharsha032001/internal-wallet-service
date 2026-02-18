package com.internal_wallet.internal_wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internal_wallet.internal_wallet.dto.BonusRequest;
import com.internal_wallet.internal_wallet.dto.SpendRequest;
import com.internal_wallet.internal_wallet.dto.TopupRequest;
import com.internal_wallet.internal_wallet.dto.TransactionResponse;
import com.internal_wallet.internal_wallet.entity.*;
import com.internal_wallet.internal_wallet.enums.EntryType;
import com.internal_wallet.internal_wallet.enums.TransactionType;
import com.internal_wallet.internal_wallet.exception.AssetTypeNotFoundException;
import com.internal_wallet.internal_wallet.exception.InsufficientBalanceException;
import com.internal_wallet.internal_wallet.exception.WalletNotFoundException;
import com.internal_wallet.internal_wallet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final WalletRepository walletRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    // ===================================================================
    // Public API
    // ===================================================================

    /**
     * Credits a user's wallet from TREASURY (paid top-up flow).
     * Flow: TREASURY → USER
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse topUp(TopupRequest req, String idempotencyKey) {
        TransactionResponse cached = getCachedResponse(idempotencyKey);
        if (cached != null) return cached;
        return executeTreasuryToUser(req.getUserId(), req.getAssetType(), req.getAmount(),
                TransactionType.TOPUP, req.getReferenceId(), idempotencyKey);
    }

    /**
     * Issues free credits to a user's wallet from TREASURY (bonus / incentive flow).
     * Flow: TREASURY → USER
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse bonus(BonusRequest req, String idempotencyKey) {
        TransactionResponse cached = getCachedResponse(idempotencyKey);
        if (cached != null) return cached;
        return executeTreasuryToUser(req.getUserId(), req.getAssetType(), req.getAmount(),
                TransactionType.BONUS, req.getReason(), idempotencyKey);
    }

    /**
     * Shared template for TOPUP and BONUS — both transfer from TREASURY to a user wallet.
     * All steps execute inside a single READ_COMMITTED transaction with PESSIMISTIC_WRITE
     * row locks held until commit.
     *
     * Lock order: wallets are locked in ascending ID order to prevent deadlocks.
     * TREASURY balance has no lower-bound check — it is the unlimited system source.
     */
    private TransactionResponse executeTreasuryToUser(Long userId, String assetTypeName,
                                                      BigDecimal amount, TransactionType type,
                                                      String referenceId, String idempotencyKey) {
        AssetType assetType      = resolveAssetType(assetTypeName);
        Wallet    userWallet     = resolveUserWallet(userId, assetType.getId());
        Wallet    treasuryWallet = resolveSystemWallet("TREASURY", assetType.getId());

        List<Wallet> locked = lockInOrder(treasuryWallet.getId(), userWallet.getId());
        Wallet lockedTreasury = findById(locked, treasuryWallet.getId());
        Wallet lockedUser     = findById(locked, userWallet.getId());

        lockedTreasury.setBalance(lockedTreasury.getBalance().subtract(amount));
        lockedUser.setBalance(lockedUser.getBalance().add(amount));
        walletRepository.saveAll(List.of(lockedTreasury, lockedUser));

        Transaction txn = transactionRepository.save(new Transaction(type, referenceId));
        createLedgerEntries(txn, lockedTreasury, lockedUser, amount, assetType);

        TransactionResponse response = buildResponse(txn, lockedUser, userId, amount, assetType.getName());
        saveIdempotencyKey(idempotencyKey, response);
        return response;
    }

    /**
     * Debits a user's wallet and credits REVENUE (spend / purchase flow).
     * Flow: USER → REVENUE
     *
     * CRITICAL — balance check is performed AFTER acquiring the PESSIMISTIC_WRITE
     * lock on the user wallet row.  Any check before locking would be vulnerable
     * to a TOCTOU race: two concurrent spend requests could both pass the
     * pre-lock check and together overdraw the balance.  Checking post-lock
     * guarantees that the balance seen is the authoritative, serialised value.
     *
     * Concurrent spend scenario:
     *   T1 and T2 both attempt to spend from the same wallet.
     *   One acquires the lock first and proceeds.  The other blocks at
     *   SELECT FOR UPDATE until T1 commits.  After T1 commits, T2 re-reads
     *   the updated (lower) balance and may then throw InsufficientBalanceException.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse spend(SpendRequest req, String idempotencyKey) {
        TransactionResponse cached = getCachedResponse(idempotencyKey);
        if (cached != null) return cached;

        AssetType assetType    = resolveAssetType(req.getAssetType());
        Wallet    userWallet   = resolveUserWallet(req.getUserId(), assetType.getId());
        Wallet    revenueWallet = resolveSystemWallet("REVENUE", assetType.getId());

        List<Wallet> locked = lockInOrder(userWallet.getId(), revenueWallet.getId());
        Wallet lockedUser    = findById(locked, userWallet.getId());
        Wallet lockedRevenue = findById(locked, revenueWallet.getId());

        // Balance check AFTER lock — serialised, race-condition-free
        if (lockedUser.getBalance().compareTo(req.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance: available=" + lockedUser.getBalance()
                            + ", requested=" + req.getAmount()
                            + ", assetType=" + assetType.getName());
        }

        lockedUser.setBalance(lockedUser.getBalance().subtract(req.getAmount()));
        lockedRevenue.setBalance(lockedRevenue.getBalance().add(req.getAmount()));
        walletRepository.saveAll(List.of(lockedUser, lockedRevenue));

        Transaction txn = transactionRepository.save(
                new Transaction(TransactionType.SPEND, req.getService()));
        createLedgerEntries(txn, lockedUser, lockedRevenue, req.getAmount(), assetType);

        TransactionResponse response = buildResponse(txn, lockedUser, req.getUserId(),
                req.getAmount(), assetType.getName());
        saveIdempotencyKey(idempotencyKey, response);
        return response;
    }

    // ===================================================================
    // Idempotency helpers
    // ===================================================================

    /**
     * Returns the cached TransactionResponse if this idempotency key was already
     * successfully processed, otherwise returns null.
     *
     * This method intentionally runs inside the caller's @Transactional context.
     * If the outer transaction rolls back, the idempotency key INSERT also rolls
     * back, allowing the client to safely retry with the same key.
     *
     * O(1) — unique index on idempotency_keys.idempotency_key.
     */
    private TransactionResponse getCachedResponse(String idempotencyKey) {
        return idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                .map(ik -> {
                    try {
                        return objectMapper.readValue(ik.getResponseBody(), TransactionResponse.class);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(
                                "Failed to deserialize cached idempotency response for key: "
                                        + idempotencyKey, e);
                    }
                })
                .orElse(null);
    }

    /**
     * Re-queries the idempotency store after a concurrent duplicate insert conflict.
     * Called by the controller when a DataIntegrityViolationException signals that
     * another request with the same key already committed.
     *
     * Returns an Optional so the controller can decide how to respond if the key
     * is unexpectedly absent (should not happen in normal flow).
     *
     * O(1) — unique index lookup.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<TransactionResponse> resolveConflict(String idempotencyKey) {
        return idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                .map(ik -> {
                    try {
                        return objectMapper.readValue(ik.getResponseBody(), TransactionResponse.class);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(
                                "Failed to deserialize idempotency response for key: " + idempotencyKey, e);
                    }
                });
    }

    /**
     * Persists the idempotency key and its associated response JSON within the
     * current transaction.  The unique constraint on idempotency_key ensures that
     * a concurrent duplicate request racing to insert the same key will trigger a
     * DataIntegrityViolationException, which the controller catches and resolves by
     * re-querying the now-committed cached response.
     */
    private void saveIdempotencyKey(String key, TransactionResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            idempotencyKeyRepository.save(new IdempotencyKey(key, json, 200));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotency response", e);
        }
    }

    // ===================================================================
    // Wallet resolution helpers  (all O(1) via indexed lookups)
    // ===================================================================

    private AssetType resolveAssetType(String name) {
        return assetTypeRepository.findByName(name)
                .orElseThrow(() -> new AssetTypeNotFoundException(name));
    }

    private Wallet resolveUserWallet(Long userId, Long assetTypeId) {
        return walletRepository.findByUserIdAndAssetTypeId(userId, assetTypeId)
                .orElseThrow(() -> new WalletNotFoundException(
                        "User wallet not found for userId=" + userId));
    }

    /**
     * System wallets (TREASURY, REVENUE) must always exist.
     * A missing system wallet is an infrastructure fault, not a client error.
     */
    private Wallet resolveSystemWallet(String name, Long assetTypeId) {
        return walletRepository.findByWalletNameAndAssetTypeId(name, assetTypeId)
                .orElseThrow(() -> new IllegalStateException(
                        "System wallet '" + name + "' not found for assetTypeId=" + assetTypeId
                                + ". Check seed data."));
    }

    // ===================================================================
    // Locking helpers
    // ===================================================================

    /**
     * Acquires PESSIMISTIC_WRITE (SELECT … FOR UPDATE) locks on the two wallets
     * in ascending ID order.
     *
     * Deadlock prevention rationale:
     *   If T1 locks wallets [A, B] and T2 locks wallets [B, A], they form a
     *   cycle and deadlock.  By always acquiring locks in ascending ID order,
     *   both transactions will attempt to lock A first, serialising access and
     *   eliminating the cycle.
     *
     * Complexity: O(1) for fixed 2-wallet transactions; O(n log n) if generalised.
     */
    private List<Wallet> lockInOrder(Long id1, Long id2) {
        List<Long> sortedIds = new ArrayList<>(List.of(id1, id2));
        sortedIds.sort(Long::compareTo);

        List<Wallet> result = new ArrayList<>();
        for (Long id : sortedIds) {
            Wallet locked = walletRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new WalletNotFoundException("Wallet not found with id=" + id));
            result.add(locked);
        }
        return result;
    }

    /** Retrieves a wallet from the locked list by its ID. */
    private Wallet findById(List<Wallet> wallets, Long id) {
        return wallets.stream()
                .filter(w -> w.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Locked wallet with id=" + id + " not found in list"));
    }

    // ===================================================================
    // Ledger helper
    // ===================================================================

    /**
     * Creates exactly 2 ledger entries for a logical transaction:
     *   - One DEBIT on the source wallet
     *   - One CREDIT on the destination wallet
     *
     * These entries are the immutable audit trail; balance fields on the wallet
     * rows are the performance-optimised cached projection of this trail.
     */
    private void createLedgerEntries(Transaction txn, Wallet debitWallet, Wallet creditWallet,
                                     BigDecimal amount, AssetType assetType) {
        ledgerEntryRepository.saveAll(List.of(
                new LedgerEntry(txn, debitWallet,  EntryType.DEBIT,  amount, assetType),
                new LedgerEntry(txn, creditWallet, EntryType.CREDIT, amount, assetType)
        ));
    }

    // ===================================================================
    // Response builder
    // ===================================================================

    private TransactionResponse buildResponse(Transaction txn, Wallet userWallet,
                                               Long userId, BigDecimal amount, String assetTypeName) {
        return TransactionResponse.builder()
                .transactionId(txn.getId())
                .userId(userId)
                .assetType(assetTypeName)
                .amount(amount)
                .transactionType(txn.getTransactionType().name())
                .newBalance(userWallet.getBalance())
                .timestamp(LocalDateTime.now(ZoneId.of("Asia/Kolkata")))
                .build();
    }
}
