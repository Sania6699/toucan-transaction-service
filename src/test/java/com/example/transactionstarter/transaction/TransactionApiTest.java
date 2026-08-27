package com.example.transactionstarter.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the four operations through the real HTTP stack, the real service and
 * a real H2 database.
 *
 * <p>These are integration tests rather than mocked unit tests because what is
 * worth checking here is the behaviour a caller actually sees: the status code,
 * the body, and whether the row really ended up in the database. A mocked
 * repository would let all four operations pass while the application was
 * broken end to end.
 *
 * <p>The database is emptied before each test so that no test depends on
 * another having run first, and the tests can be run in any order.
 *
 * <p>Request bodies are written as JSON strings rather than serialised from the
 * DTO. That is deliberate: it tests the wire format a caller sends, and it
 * allows sending values the DTO could never hold - an unsupported currency, a
 * missing field - which is exactly what the validation tests need.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransactionApiTest {

    private static final String VALID_ID = "TXN-00000001";
    private static final String VALID_CUSTOMER = "CUST-1001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository repository;

    @BeforeEach
    void emptyTheDatabase() {
        repository.deleteAll();
    }

    // ---------------------------------------------------------------------
    // A. Create transaction
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("A valid transaction is created, returns 201, and is actually stored")
    void createTransaction_storesItAndReturns201() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(VALID_ID, VALID_CUSTOMER, "250.75", "INR", "DEPOSIT")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/transactions/" + VALID_ID))
                .andExpect(jsonPath("$.transactionId").value(VALID_ID))
                .andExpect(jsonPath("$.customerId").value(VALID_CUSTOMER))
                .andExpect(jsonPath("$.amount").value(250.75))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        Transaction stored = repository.findById(VALID_ID).orElseThrow();
        assertThat(stored.getCustomerId()).isEqualTo(VALID_CUSTOMER);
        assertThat(stored.getAmount()).isEqualByComparingTo("250.75");
        assertThat(stored.getCurrency()).isEqualTo(Currency.INR);
        assertThat(stored.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(stored.getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    @DisplayName("A transaction with a non-positive amount is rejected with 400 and nothing is stored")
    void createTransaction_rejectsNonPositiveAmount() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(VALID_ID, VALID_CUSTOMER, "-10.00", "INR", "WITHDRAWAL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details", hasItem(containsString("amount"))));

        assertThat(repository.findById(VALID_ID)).isEmpty();
    }

    @Test
    @DisplayName("An amount over the maximum is rejected")
    void createTransaction_rejectsAmountAboveTheLimit() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(VALID_ID, VALID_CUSTOMER, "1000000.01", "USD", "TRANSFER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(containsString("amount"))));

        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("An amount with more than two decimal places is rejected")
    void createTransaction_rejectsTooManyDecimalPlaces() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(VALID_ID, VALID_CUSTOMER, "10.999", "USD", "DEPOSIT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(containsString("2 decimal places"))));
    }

    @Test
    @DisplayName("A duplicate transaction ID is rejected with 409 and does not overwrite the original")
    void createTransaction_rejectsDuplicateTransactionId() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(VALID_ID, VALID_CUSTOMER, "100.00", "INR", "DEPOSIT")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(VALID_ID, "CUST-9999", "999.00", "USD", "WITHDRAWAL")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Duplicate transaction ID"))
                .andExpect(jsonPath("$.details", hasItem(containsString("already exists"))));

        // The second request must not have overwritten the first.
        assertThat(repository.count()).isEqualTo(1);
        Transaction stored = repository.findById(VALID_ID).orElseThrow();
        assertThat(stored.getCustomerId()).isEqualTo(VALID_CUSTOMER);
        assertThat(stored.getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("A currency outside the permitted set is rejected, and the message lists what is allowed")
    void createTransaction_rejectsUnsupportedCurrency() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(VALID_ID, VALID_CUSTOMER, "10.00", "JPY", "DEPOSIT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request"))
                .andExpect(jsonPath("$.details", hasItem(containsString("JPY"))))
                .andExpect(jsonPath("$.details", hasItem(containsString("INR"))));

        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("Every broken field is reported in one response, not one per request")
    void createTransaction_reportsAllFieldErrorsAtOnce() throws Exception {
        String body = """
                {
                  "transactionId": "",
                  "customerId": "ab",
                  "amount": 0,
                  "currency": "INR",
                  "type": "DEPOSIT"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(containsString("transactionId"))))
                .andExpect(jsonPath("$.details", hasItem(containsString("customerId"))))
                .andExpect(jsonPath("$.details", hasItem(containsString("amount"))));
    }

    @Test
    @DisplayName("A caller cannot choose the starting status; every transaction begins PENDING")
    void createTransaction_alwaysStartsPendingEvenIfTheCallerAsksOtherwise() throws Exception {
        String body = """
                {
                  "transactionId": "%s",
                  "customerId": "%s",
                  "amount": 50.00,
                  "currency": "EUR",
                  "type": "REFUND",
                  "status": "COMPLETED"
                }
                """.formatted(VALID_ID, VALID_CUSTOMER);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(repository.findById(VALID_ID).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    @DisplayName("A body that is not valid JSON is rejected with 400, not a 500")
    void createTransaction_rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request"));
    }

    // ---------------------------------------------------------------------
    // B. Get transaction
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Fetching a transaction that does not exist returns 404 with a clear message")
    void getTransaction_returns404WhenItDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/transactions/TXN-DOES-NOT-EXIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Transaction not found"))
                .andExpect(jsonPath("$.details", hasItem(containsString("TXN-DOES-NOT-EXIST"))));
    }

    @Test
    @DisplayName("Fetching a stored transaction returns it")
    void getTransaction_returnsTheStoredTransaction() throws Exception {
        givenTransaction(VALID_ID, VALID_CUSTOMER, "75.50", "GBP", "TRANSFER");

        mockMvc.perform(get("/api/transactions/" + VALID_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.transactionId").value(VALID_ID))
                .andExpect(jsonPath("$.amount").value(75.50))
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ---------------------------------------------------------------------
    // C. Update transaction status
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("A pending transaction can be completed")
    void updateStatus_movesPendingToCompleted() throws Exception {
        givenTransaction(VALID_ID, VALID_CUSTOMER, "20.00", "INR", "DEPOSIT");

        mockMvc.perform(patch("/api/transactions/" + VALID_ID + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(repository.findById(VALID_ID).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("A completed transaction cannot be moved back to pending")
    void updateStatus_rejectsMovingCompletedBackToPending() throws Exception {
        givenTransaction(VALID_ID, VALID_CUSTOMER, "20.00", "INR", "DEPOSIT");
        patchStatus(VALID_ID, "COMPLETED");

        mockMvc.perform(patch("/api/transactions/" + VALID_ID + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Invalid status transition"))
                .andExpect(jsonPath("$.details", hasItem(containsString("cannot move from COMPLETED to PENDING"))));

        assertThat(repository.findById(VALID_ID).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("A final status cannot be changed again")
    void updateStatus_rejectsAnyChangeToAFinalStatus() throws Exception {
        givenTransaction(VALID_ID, VALID_CUSTOMER, "20.00", "INR", "DEPOSIT");
        patchStatus(VALID_ID, "CANCELLED");

        mockMvc.perform(patch("/api/transactions/" + VALID_ID + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details", hasItem(containsString("final status"))));
    }

    @Test
    @DisplayName("Setting the status a transaction already has is reported rather than silently accepted")
    void updateStatus_rejectsSettingTheSameStatusAgain() throws Exception {
        givenTransaction(VALID_ID, VALID_CUSTOMER, "20.00", "INR", "DEPOSIT");

        mockMvc.perform(patch("/api/transactions/" + VALID_ID + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details", hasItem(containsString("already PENDING"))));
    }

    @Test
    @DisplayName("Updating the status of a transaction that does not exist returns 404")
    void updateStatus_returns404ForAnUnknownTransaction() throws Exception {
        mockMvc.perform(patch("/api/transactions/TXN-NOT-HERE/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found"));
    }

    @Test
    @DisplayName("A status value that is not part of the lifecycle is rejected with 400")
    void updateStatus_rejectsAnUnknownStatusValue() throws Exception {
        givenTransaction(VALID_ID, VALID_CUSTOMER, "20.00", "INR", "DEPOSIT");

        mockMvc.perform(patch("/api/transactions/" + VALID_ID + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SETTLED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(containsString("SETTLED"))));
    }

    // ---------------------------------------------------------------------
    // D. Get customer transactions
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("A customer lookup returns that customer's transactions and nobody else's")
    void customerTransactions_returnsOnlyThatCustomersTransactions() throws Exception {
        givenTransaction("TXN-AAAA0001", VALID_CUSTOMER, "10.00", "INR", "DEPOSIT");
        givenTransaction("TXN-AAAA0002", VALID_CUSTOMER, "20.00", "INR", "WITHDRAWAL");
        givenTransaction("TXN-BBBB0001", "CUST-2002", "30.00", "USD", "DEPOSIT");

        mockMvc.perform(get("/api/transactions").param("customerId", VALID_CUSTOMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].customerId", everyItem(is(VALID_CUSTOMER))));

        List<Transaction> found = repository.findByCustomerIdOrderByCreatedAtDesc(VALID_CUSTOMER);
        assertThat(found).extracting(Transaction::getTransactionId)
                .containsExactlyInAnyOrder("TXN-AAAA0001", "TXN-AAAA0002");
    }

    @Test
    @DisplayName("A customer with no transactions gets an empty list, not a 404")
    void customerTransactions_returnsEmptyListForAnUnknownCustomer() throws Exception {
        mockMvc.perform(get("/api/transactions").param("customerId", "CUST-NOBODY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("A customer lookup with no customerId is rejected with 400")
    void customerTransactions_requiresTheCustomerIdParameter() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing request parameter"));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Creates a transaction through the API and asserts it was accepted. */
    private void givenTransaction(String transactionId, String customerId,
                                  String amount, String currency, String type) throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(transactionId, customerId, amount, currency, type)))
                .andExpect(status().isCreated());
    }

    /** Moves a transaction to a new status through the API and asserts it worked. */
    private void patchStatus(String transactionId, String status) throws Exception {
        mockMvc.perform(patch("/api/transactions/" + transactionId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    /** The amount is not quoted so that tests can send numbers the DTO could not hold. */
    private static String createBody(String transactionId, String customerId,
                                     String amount, String currency, String type) {
        return """
                {
                  "transactionId": "%s",
                  "customerId": "%s",
                  "amount": %s,
                  "currency": "%s",
                  "type": "%s"
                }
                """.formatted(transactionId, customerId, amount, currency, type);
    }
}
