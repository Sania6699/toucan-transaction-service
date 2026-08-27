package com.example.transactionstarter.transaction;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The lifecycle states a transaction can be in, and the rules for moving
 * between them.
 *
 * <p>Each constant knows which states it is allowed to move to next, so the
 * transition rules live with the type they describe instead of being spread
 * across if-statements in the service. Adding a new status means editing this
 * one file.
 *
 * <p>The successor sets are assigned in the static initialiser below rather
 * than passed to the constructor, because an enum constant cannot reference
 * sibling constants that have not been constructed yet.
 *
 * <h2>Why these rules</h2>
 * <ul>
 *   <li>Every transaction starts {@link #PENDING} - it has been accepted and
 *       recorded, but the money has not moved.</li>
 *   <li>From PENDING the money either moves ({@link #COMPLETED}), is attempted
 *       and does not move ({@link #FAILED}), or is withdrawn before it settles
 *       ({@link #CANCELLED}).</li>
 *   <li>A COMPLETED transaction cannot be un-completed. If it has to be undone
 *       the record is moved to {@link #REVERSED}, which preserves the fact that
 *       the money moved and was then returned.</li>
 *   <li>FAILED, CANCELLED and REVERSED are final. A settled financial record
 *       must not be rewritten - the history is the point.</li>
 *   <li>No state may transition to itself. A no-op status update almost always
 *       means the caller has lost track of the current state, so it is reported
 *       rather than silently accepted.</li>
 * </ul>
 */
public enum TransactionStatus {

    /** Accepted and recorded, but the money has not moved yet. Every transaction starts here. */
    PENDING,

    /** The money moved successfully. */
    COMPLETED,

    /** The transaction was attempted and did not succeed. Final. */
    FAILED,

    /** Withdrawn before it settled. Final. */
    CANCELLED,

    /** A completed transaction that has since been undone. Final. */
    REVERSED;

    private Set<TransactionStatus> allowedNext;

    static {
        PENDING.allowedNext = Collections.unmodifiableSet(
                EnumSet.of(COMPLETED, FAILED, CANCELLED));
        COMPLETED.allowedNext = Collections.unmodifiableSet(
                EnumSet.of(REVERSED));
        FAILED.allowedNext = Collections.emptySet();
        CANCELLED.allowedNext = Collections.emptySet();
        REVERSED.allowedNext = Collections.emptySet();
    }

    /**
     * @param target the status being moved to
     * @return true if a transaction in this status is allowed to move to target
     */
    public boolean canTransitionTo(TransactionStatus target) {
        return target != null && allowedNext.contains(target);
    }

    /**
     * @return the statuses reachable from this one; empty if this status is final
     */
    public Set<TransactionStatus> allowedNextStates() {
        return allowedNext;
    }

    /**
     * @return true if a transaction in this status can never change again
     */
    public boolean isFinal() {
        return allowedNext.isEmpty();
    }
}
