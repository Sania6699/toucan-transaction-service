package com.example.transactionstarter.transaction;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * The HTTP surface for transactions.
 *
 * <p>This class does three things and nothing else: bind the request, call the
 * service, map the result to a response. There is no business logic here - no
 * validation beyond the annotations, no lifecycle rules, no decisions. Anything
 * that could go wrong leaves as an exception and is turned into a status code
 * by {@link ApiExceptionHandler}, which keeps every method here on its happy
 * path and readable in one glance.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    /**
     * Creates a transaction.
     *
     * <p>201 Created with a Location header, because a new resource now exists
     * at a URL the caller can fetch. 200 would say "here is a result"; 201 says
     * "something was made, and it lives here".
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody CreateTransactionRequest request) {

        Transaction created = service.create(
                request.transactionId(),
                request.customerId(),
                request.amount(),
                request.currency(),
                request.type());

        return ResponseEntity
                .created(URI.create("/api/transactions/" + created.getTransactionId()))
                .body(TransactionResponse.from(created));
    }

    /**
     * Fetches one transaction. 404 if it does not exist.
     */
    @GetMapping("/{transactionId}")
    public TransactionResponse getOne(@PathVariable String transactionId) {
        return TransactionResponse.from(service.getById(transactionId));
    }

    /**
     * Changes a transaction's status.
     *
     * <p>PATCH rather than PUT: the request changes one part of the transaction
     * and leaves the rest alone, which is exactly what PATCH means. PUT would
     * imply the body replaces the whole resource.
     */
    @PatchMapping("/{transactionId}/status")
    public TransactionResponse updateStatus(
            @PathVariable String transactionId,
            @Valid @RequestBody UpdateStatusRequest request) {

        return TransactionResponse.from(service.updateStatus(transactionId, request.status()));
    }

    /**
     * Lists a customer's transactions.
     *
     * <p>Modelled as a filter on the transactions collection rather than a
     * separate /customers/{id}/transactions resource, because this service owns
     * transactions and knows nothing about customers as entities in their own
     * right. An unknown customer returns 200 with an empty list, not 404: the
     * collection exists and is empty.
     */
    @GetMapping
    public List<TransactionResponse> getForCustomer(@RequestParam String customerId) {
        return service.getByCustomerId(customerId).stream()
                .map(TransactionResponse::from)
                .toList();
    }
}
