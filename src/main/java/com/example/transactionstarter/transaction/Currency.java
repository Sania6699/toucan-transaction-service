package com.example.transactionstarter.transaction;

/**
 * The currencies this service is willing to accept.
 *
 * <p>Modelling this as an enum rather than a free-text String means an
 * unsupported currency is rejected the moment the request is deserialised,
 * and no code further in can ever be handed a currency the service does not
 * understand.
 */
public enum Currency {

    /** Indian Rupee. */
    INR,

    /** United States Dollar. */
    USD,

    /** Euro. */
    EUR,

    /** Pound Sterling. */
    GBP
}
