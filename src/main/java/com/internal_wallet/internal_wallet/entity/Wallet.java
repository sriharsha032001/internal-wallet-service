package com.internal_wallet.internal_wallet.entity;

import com.internal_wallet.internal_wallet.enums.WalletType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "wallets",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_wallets_name_asset",
        columnNames = {"wallet_name", "asset_type_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * NULL for system wallets (TREASURY, REVENUE).
     * Set to the logical user identifier for user wallets.
     */
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_type", nullable = false, length = 10)
    private WalletType walletType;

    /**
     * Human-readable name: TREASURY | REVENUE | USER_1 | USER_2 …
     * Must be unique per asset type.
     */
    @Column(name = "wallet_name", nullable = false, length = 50)
    private String walletName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_type_id", nullable = false)
    private AssetType assetType;

    /**
     * Cached balance — updated atomically alongside ledger entries within the
     * same transaction after a SELECT … FOR UPDATE lock is held on this row.
     * Never read or mutated outside a @Transactional boundary.
     */
    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * JPA optimistic-lock version — serves as a secondary safety net against
     * any accidental concurrent write that bypassed the pessimistic lock.
     */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
