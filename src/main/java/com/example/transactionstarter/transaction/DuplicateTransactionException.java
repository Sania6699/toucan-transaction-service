package com.example.transactionstarter.transaction;

/**
 * Thrown when a create request supplies a transaction ID that is already in use.
 *
 * <p>Transaction IDs come from the caller, so this is a real and expected
 * failure rather than a programming error. It is translated to HTTP 409 in
 * {@link ApiExceptionHandler}: the request is well formed, but it conflicts
 * with what already exists.
 */
public class DuplicateTransactionException extends RuntimeException {

    private final String transactionId;

    public DuplicateTransactionException(String transactionId) {
        super("A transaction with ID '" + transactionId + "' already exists");
        this.transactionId = transactionId;
    }

    public String getTransactionId() {
        return transactionId;
    }
}
