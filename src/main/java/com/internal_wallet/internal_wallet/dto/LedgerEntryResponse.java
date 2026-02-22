package com.internal_wallet.internal_wallet.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class LedgerEntryResponse {

    private final Long ledgerEntryId;
    private final Long transactionId;

    /** TOPUP | BONUS | SPEND */
    private final String transactionType;

    /** DEBIT | CREDIT — from this wallet's perspective */
    private final String entryType;

    private final BigDecimal amount;
    private final String assetType;

    /**
     * Contextual reference:
     *   TOPUP  → payment / reference ID (e.g. "PAYMENT-ABC-123")
     *   BONUS  → reason string           (e.g. "REFERRAL_BONUS")
     *   SPEND  → service name            (e.g. "IN_GAME_ITEM")
     */
    private final String referenceId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime timestamp;
}
