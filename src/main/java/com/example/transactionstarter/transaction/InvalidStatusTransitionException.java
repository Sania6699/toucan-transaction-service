package com.example.transactionstarter.transaction;

/**
 * Thrown when a status update asks for a move the lifecycle does not allow.
 *
 * <p>The message names the current status, the requested status and what would
 * have been legal, so the caller can correct the request without reading the
 * source. Translated to HTTP 409 in {@link ApiExceptionHandler}.
 */
public class InvalidStatusTransitionException extends RuntimeException {

    private final String transactionId;
    private final TransactionStatus from;
    private final TransactionStatus to;

    public InvalidStatusTransitionException(String transactionId,
                                            TransactionStatus from,
                                            TransactionStatus to) {
        super(buildMessage(transactionId, from, to));
        this.transactionId = transactionId;
        this.from = from;
        this.to = to;
    }

    private static String buildMessage(String transactionId,
                                       TransactionStatus from,
                                       TransactionStatus to) {
        if (from == to) {
            return "Transaction '" + transactionId + "' is already " + from
                    + "; a status update must change the status";
        }
        if (from.isFinal()) {
            return "Transaction '" + transactionId + "' is " + from
                    + ", which is a final status and cannot be changed";
        }
        return "Transaction '" + transactionId + "' cannot move from " + from
                + " to " + to + "; allowed from " + from + ": " + from.allowedNextStates();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public TransactionStatus getFrom() {
        return from;
    }

    public TransactionStatus getTo() {
        return to;
    }
}
