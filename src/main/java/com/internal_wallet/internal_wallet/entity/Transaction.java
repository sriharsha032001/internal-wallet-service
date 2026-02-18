package com.internal_wallet.internal_wallet.entity;

import com.internal_wallet.internal_wallet.enums.TransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 10)
    private TransactionType transactionType;

    /**
     * External reference: payment ID for TOPUP, reason string for BONUS,
     * service name for SPEND.
     */
    @Column(name = "reference_id", length = 255)
    private String referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Transaction(TransactionType transactionType, String referenceId) {
        this.transactionType = transactionType;
        this.referenceId = referenceId;
    }
}
