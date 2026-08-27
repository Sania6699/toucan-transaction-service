package com.example.transactionstarter.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A single customer transaction, and the stored form of the record.
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li><b>The transaction ID is supplied by the caller and is the primary
 *       key.</b> The exercise requires that a repeated ID is rejected, which
 *       only makes sense if the caller chooses it. Nothing is generated here.</li>
 *   <li><b>Amount is a {@link BigDecimal}.</b> double and float cannot represent
 *       most decimal fractions exactly, so money held in them drifts. BigDecimal
 *       is exact and is the only correct choice for currency amounts.</li>
 *   <li><b>Currency, type and status are enums stored as strings.</b>
 *       EnumType.STRING rather than ORDINAL, because ordinal values silently
 *       change meaning the moment someone reorders the enum constants.</li>
 *   <li><b>Everything except status is immutable after creation.</b> The fields
 *       are marked updatable = false and have no setters. What a transaction was
 *       is a fact; only where it is in its lifecycle changes.</li>
 *   <li><b>The status guard lives here, not in the service.</b> Because
 *       {@link #changeStatusTo} is the only way to alter the status, no caller
 *       anywhere can put a transaction into an illegal state.</li>
 * </ul>
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false, length = 36)
    private String transactionId;

    @Column(name = "customer_id", nullable = false, updatable = false, length = 36)
    private String customerId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, updatable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Required by JPA, which needs a no-argument constructor to build entities
     * when reading them back. Not for application code - protected so it cannot
     * be called by mistake from outside this package.
     */
    protected Transaction() {
    }

    /**
     * Creates a new transaction. The status is not a parameter: a caller does
     * not get to declare that a payment has already completed, so every
     * transaction begins {@link TransactionStatus#PENDING}.
     */
    public Transaction(String transactionId,
                       String customerId,
                       BigDecimal amount,
                       Currency currency,
                       TransactionType type) {
        this.transactionId = transactionId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.type = type;
        this.status = TransactionStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Moves this transaction to a new status, if the lifecycle allows it.
     *
     * @param newStatus the status to move to
     * @throws InvalidStatusTransitionException if the move is not permitted from
     *         the current status
     */
    public void changeStatusTo(TransactionStatus newStatus) {
        if (!status.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(transactionId, status, newStatus);
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Two transactions are the same transaction when they carry the same ID.
     * The ID is supplied by the caller and never changes, so it is a safe basis
     * for equality - unlike a generated key, which is null before the entity is
     * first saved.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Transaction that)) {
            return false;
        }
        return Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }

    @Override
    public String toString() {
        return "Transaction[id=" + transactionId
                + ", customerId=" + customerId
                + ", amount=" + amount
                + ", currency=" + currency
                + ", type=" + type
                + ", status=" + status + "]";
    }
}
