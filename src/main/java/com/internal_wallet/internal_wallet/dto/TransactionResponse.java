package com.internal_wallet.internal_wallet.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Jacksonized
public class TransactionResponse {

    private final Long transactionId;
    private final Long userId;
    private final String assetType;
    private final BigDecimal amount;
    private final String transactionType;
    private final BigDecimal newBalance;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime timestamp;
}
