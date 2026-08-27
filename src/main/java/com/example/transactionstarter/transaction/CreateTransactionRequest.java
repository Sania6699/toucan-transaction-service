package com.example.transactionstarter.transaction;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * The body of a create-transaction request.
 *
 * <p>This is a separate type from the {@link Transaction} entity on purpose:
 * <ul>
 *   <li>The API contract and the database schema can then change independently.</li>
 *   <li>A caller cannot set fields that are not on this record. Status is
 *       absent, so no request can create a transaction that is already
 *       COMPLETED - the service decides the starting status, not the caller.</li>
 *   <li>Validation rules belong to the request, not to the stored record.</li>
 * </ul>
 *
 * <p>A record rather than a class: it is immutable, and the constructor,
 * accessors, equals, hashCode and toString are all generated.
 */
public record CreateTransactionRequest(

        @NotBlank(message = "transactionId is required")
        @Size(min = 8, max = 36, message = "transactionId must be between 8 and 36 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$",
                message = "transactionId may contain only letters, digits, hyphen and underscore")
        String transactionId,

        @NotBlank(message = "customerId is required")
        @Size(min = 4, max = 36, message = "customerId must be between 4 and 36 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$",
                message = "customerId may contain only letters, digits, hyphen and underscore")
        String customerId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @DecimalMax(value = "1000000.00", message = "amount must not exceed 1000000.00")
        @Digits(integer = 7, fraction = 2,
                message = "amount may have at most 2 decimal places")
        BigDecimal amount,

        @NotNull(message = "currency is required and must be one of INR, USD, EUR, GBP")
        Currency currency,

        @NotNull(message = "type is required and must be one of DEPOSIT, WITHDRAWAL, TRANSFER, REFUND")
        TransactionType type
) {
}
