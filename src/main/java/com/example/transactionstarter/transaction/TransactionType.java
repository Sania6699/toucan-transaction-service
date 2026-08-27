package com.example.transactionstarter.transaction;

/**
 * What kind of money movement a transaction represents.
 *
 * <p>The type is fixed when the transaction is created and never changes
 * afterwards. A transaction that turns out to be wrong is corrected by moving
 * it to a new status, not by rewriting what it was.
 */
public enum TransactionType {

    /** Money paid in to the customer's account. */
    DEPOSIT,

    /** Money taken out of the customer's account. */
    WITHDRAWAL,

    /** Money moved from this customer to another party. */
    TRANSFER,

    /** Money returned to the customer from an earlier payment. */
    REFUND
}
