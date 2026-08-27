package com.example.transactionstarter.transaction;

/**
 * Thrown when a transaction ID does not match anything stored.
 *
 * <p>Unchecked because a caller asking for a transaction that is not there is a
 * request problem, not something service code can recover from. It is
 * translated to HTTP 404 in {@link ApiExceptionHandler}.
 */
public class TransactionNotFoundException extends RuntimeException {

    private final String transactionId;

    public TransactionNotFoundException(String transactionId) {
        super("No transaction found with ID '" + transactionId + "'");
        this.transactionId = transactionId;
    }

    public String getTransactionId() {
        return transactionId;
    }
}
