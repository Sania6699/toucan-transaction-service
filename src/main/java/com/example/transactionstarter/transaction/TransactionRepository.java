package com.example.transactionstarter.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persistence for transactions.
 *
 * <p>Spring Data implements this interface at runtime. The key type is String
 * because the caller-supplied transaction ID is the primary key, which gives
 * findById and existsById for free. The one method declared here is derived
 * from its own name - Spring Data reads "findByCustomerIdOrderByCreatedAtDesc"
 * and writes the query.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /**
     * @param customerId the customer to look up
     * @return that customer's transactions, newest first; an empty list if the
     *         customer has none or does not exist
     */
    List<Transaction> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
