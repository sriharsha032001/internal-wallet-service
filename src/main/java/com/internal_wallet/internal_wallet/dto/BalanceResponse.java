package com.internal_wallet.internal_wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class BalanceResponse {

    private final Long userId;
    private final String assetType;
    private final BigDecimal balance;
}
