package com.example.transactionstarter.transaction;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape every failed request returns.
 *
 * <p>One consistent body means a client can parse failures the same way
 * regardless of what went wrong. The details list carries the specifics - one
 * entry per broken field for a validation failure, a single entry for
 * everything else - so a caller fixing a bad request gets every problem at
 * once rather than one per round trip.
 *
 * <p>Null fields are omitted from the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> details,
        String path
) {
}
