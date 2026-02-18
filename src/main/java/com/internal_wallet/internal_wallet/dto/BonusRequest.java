package com.internal_wallet.internal_wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class BonusRequest {

    @NotNull(message = "userId is required")
    private Long userId;

    @NotBlank(message = "assetType is required")
    private String assetType;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.000001", message = "amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "reason is required")
    private String reason;
}
