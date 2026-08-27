package com.example.transactionstarter.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * The business rules for transactions.
 *
 * <p>This layer sits between the controller and the repository and is where the
 * rules that are not expressible as annotations live: rejecting a duplicate ID,
 * turning a missing row into a meaningful failure, and driving the status
 * lifecycle.
 *
 * <p>It deliberately knows nothing about HTTP. It takes and returns domain
 * objects and throws domain exceptions; turning those into status codes is the
 * web layer's job. That keeps this class testable without a servlet and means
 * the same rules would hold if the service were driven by a message queue
 * instead of REST.
 *
 * <p>The create method takes individual values rather than the request DTO so
 * that the service does not depend on the shape of the API contract.
 */
@Service
public class TransactionService {

    private final TransactionRepository repository;

    /**
     * Constructor injection rather than field injection: the dependency is
     * visible, it can be final, and the class can be built in a unit test with
     * no Spring container at all.
     */
    public TransactionService(TransactionRepository repository) {
        this.repository = repository;
    }

    /**
     * Stores a new transaction.
     *
     * <p>The duplicate check is explicit rather than left to the database.
     * With a caller-supplied primary key, JPA's save() performs a merge - it
     * looks the row up and updates it if it is already there - so without this
     * check a repeated ID would silently overwrite the original record instead
     * of being rejected.
     *
     * @throws DuplicateTransactionException if the ID is already in use
     */
    @Transactional
    public Transaction create(String transactionId,
                              String customerId,
                              BigDecimal amount,
                              Currency currency,
                              TransactionType type) {
        if (repository.existsById(transactionId)) {
            throw new DuplicateTransactionException(transactionId);
        }
        return repository.save(new Transaction(transactionId, customerId, amount, currency, type));
    }

    /**
     * @param transactionId the transaction to fetch
     * @return the transaction
     * @throws TransactionNotFoundException if no transaction has that ID
     */
    @Transactional(readOnly = true)
    public Transaction getById(String transactionId) {
        return repository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    /**
     * Moves an existing transaction to a new status.
     *
     * <p>Whether the move is legal is decided by the entity, which owns that
     * invariant. This method's job is to find the right transaction, ask it to
     * change, and let both failure modes - unknown ID and illegal move -
     * surface as distinct exceptions so the caller learns which one happened.
     *
     * @throws TransactionNotFoundException if no transaction has that ID
     * @throws InvalidStatusTransitionException if the lifecycle forbids the move
     */
    @Transactional
    public Transaction updateStatus(String transactionId, TransactionStatus newStatus) {
        Transaction transaction = getById(transactionId);
        transaction.changeStatusTo(newStatus);
        return repository.save(transaction);
    }

    /**
     * @param customerId the customer to look up
     * @return that customer's transactions, newest first
     *
     * <p>An unknown customer returns an empty list rather than a 404. The
     * question "what has this customer done" has a correct answer - nothing -
     * and this service has no customer registry to say whether the customer
     * exists at all, so it does not pretend to know.
     */
    @Transactional(readOnly = true)
    public List<Transaction> getByCustomerId(String customerId) {
        return repository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }
}
