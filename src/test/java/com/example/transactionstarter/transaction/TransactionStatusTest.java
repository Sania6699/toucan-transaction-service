package com.example.transactionstarter.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the status lifecycle.
 *
 * <p>These run without Spring. The transition rules are pure logic with no
 * dependencies, so loading an application context to test them would only make
 * the suite slower and the failures harder to read. The API-level tests in
 * {@link TransactionApiTest} check that these rules are actually enforced over
 * HTTP; these check that the rules themselves are right.
 */
class TransactionStatusTest {

    @Test
    @DisplayName("A pending transaction can complete, fail, or be cancelled")
    void pendingMovesToCompletedFailedOrCancelled() {
        assertThat(TransactionStatus.PENDING.canTransitionTo(TransactionStatus.COMPLETED)).isTrue();
        assertThat(TransactionStatus.PENDING.canTransitionTo(TransactionStatus.FAILED)).isTrue();
        assertThat(TransactionStatus.PENDING.canTransitionTo(TransactionStatus.CANCELLED)).isTrue();

        assertThat(TransactionStatus.PENDING.canTransitionTo(TransactionStatus.REVERSED)).isFalse();
    }

    @Test
    @DisplayName("A completed transaction can only be reversed, never un-completed")
    void completedOnlyMovesToReversed() {
        assertThat(TransactionStatus.COMPLETED.canTransitionTo(TransactionStatus.REVERSED)).isTrue();

        assertThat(TransactionStatus.COMPLETED.canTransitionTo(TransactionStatus.PENDING)).isFalse();
        assertThat(TransactionStatus.COMPLETED.canTransitionTo(TransactionStatus.FAILED)).isFalse();
        assertThat(TransactionStatus.COMPLETED.canTransitionTo(TransactionStatus.CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("Failed, cancelled and reversed are final and allow no further change")
    void finalStatusesAllowNoFurtherChange() {
        for (TransactionStatus finalStatus : new TransactionStatus[]{
                TransactionStatus.FAILED, TransactionStatus.CANCELLED, TransactionStatus.REVERSED}) {

            assertThat(finalStatus.isFinal())
                    .as("%s should be final", finalStatus)
                    .isTrue();

            for (TransactionStatus target : TransactionStatus.values()) {
                assertThat(finalStatus.canTransitionTo(target))
                        .as("%s should not be able to move to %s", finalStatus, target)
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("No status can transition to itself")
    void noStatusCanTransitionToItself() {
        for (TransactionStatus status : TransactionStatus.values()) {
            assertThat(status.canTransitionTo(status))
                    .as("%s should not be able to move to itself", status)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("A null target status is never a legal move")
    void nullTargetIsRejected() {
        assertThat(TransactionStatus.PENDING.canTransitionTo(null)).isFalse();
    }

    @Test
    @DisplayName("The exposed successor set cannot be modified by a caller")
    void allowedNextStatesCannotBeModifiedFromOutside() {
        assertThatThrownBy(() -> TransactionStatus.PENDING.allowedNextStates()
                .add(TransactionStatus.REVERSED))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
