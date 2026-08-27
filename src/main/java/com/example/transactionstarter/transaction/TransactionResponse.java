package com.example.transactionstarter.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What the API returns for a transaction.
 *
 * <p>Returning this instead of the entity keeps the JPA model out of the
 * public contract, so a change to the persistence layer cannot accidentally
 * change what callers receive.
 */
public record TransactionResponse(
        String transactionId,
        String customerId,
        BigDecimal amount,
        Currency currency,
        TransactionType type,
        TransactionStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * @param transaction the stored transaction
     * @return the response view of it
     */
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getCustomerId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt());
    }
}
