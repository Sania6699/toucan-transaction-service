package com.example.transactionstarter.transaction;

import jakarta.validation.constraints.NotNull;

/**
 * The body of a status-update request.
 *
 * <p>Only the status is accepted. Nothing else about a transaction can be
 * changed after it is created, so nothing else appears here.
 */
public record UpdateStatusRequest(

        @NotNull(message = "status is required and must be one of "
                + "PENDING, COMPLETED, FAILED, CANCELLED, REVERSED")
        TransactionStatus status
) {
}
