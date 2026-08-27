# Customer Transactions Service

A small Spring Boot service that records customer transactions and manages their
lifecycle. Built on the supplied starter project.

Run it: `mvnw.cmd clean test` (Windows) or `./mvnw clean test` (Linux/macOS).
No manual setup, no database to install — H2 runs in memory.

**27 tests, 0 failures** from a clean build. Full output in `TEST_OUTPUT.txt`.

---

## 1. Understanding of the problem

The service is a system of record for money movements. Something else decides
that a payment should happen; this service records that it was asked for, and
tracks what became of it.

That framing drove two decisions that shape everything else:

- **The transaction ID comes from the caller, not from us.** The requirement to
  reject a duplicate ID only makes sense if the caller chooses it — so the ID is
  the primary key and nothing is generated.
- **A transaction record is mostly immutable.** What a transaction *was* — who,
  how much, in what currency, of what kind — is a historical fact. Only its
  position in the lifecycle changes. So `status` is the only mutable field; the
  rest have no setters and are mapped `updatable = false`.

---

## 2. Assumptions

I was not given a variant, so the permitted currencies, the amount ceiling and
the transaction types below are my own choices. They are all in one place and
are easy to change.

1. Transaction IDs are generated upstream (by a gateway or the caller's own
   system) and are globally unique. This service's job is to detect a collision,
   not to prevent one.
2. There is no customer registry here. A customer ID is an opaque reference to
   something owned elsewhere, so this service cannot say whether a customer
   exists — only whether it has seen transactions for one.
3. No authentication or authorisation. Out of scope for the exercise; noted as a
   limitation below rather than half-built.
4. Single currency per transaction, no conversion. A transaction's amount is in
   its stated currency and is never converted.
5. Amounts are recorded to two decimal places. This is true for all four
   permitted currencies. It would not be true for a zero-decimal currency such
   as JPY, which is one reason JPY is not on the list.
6. The service records status changes; it does not carry them out. Nothing here
   actually moves money.

---

## 3. Validation rules

Field rules are Bean Validation annotations on `CreateTransactionRequest`.
Anything an annotation cannot express lives in `TransactionService` or on the
`Transaction` entity itself.

| Field | Rule | Why |
|---|---|---|
| `transactionId` | Required, 8–36 chars, `[A-Za-z0-9_-]` only | It appears in a URL path, so it is restricted to URL-safe characters. The lower bound rejects obviously placeholder IDs like `1`. |
| `transactionId` | Must not already exist | The exercise requires it, and see the note below on why an explicit check is needed. |
| `customerId` | Required, 4–36 chars, `[A-Za-z0-9_-]` only | Same reasoning, shorter floor because customer references are often shorter. |
| `amount` | Required, ≥ 0.01 | Zero moves no money and a negative amount is not a transaction — direction is expressed by `type`, not by sign. Allowing negative amounts would create two ways to say the same thing, and they would eventually disagree. |
| `amount` | ≤ 1,000,000.00 | An arbitrary but deliberate ceiling. A cap that a caller can hit is better than no cap: an unbounded amount field is how a fat-fingered or hostile request becomes a very large problem. |
| `amount` | At most 2 decimal places | None of the permitted currencies has a smaller unit. Accepting `10.999` would mean silently rounding someone's money. |
| `currency` | Required, one of `INR`, `USD`, `EUR`, `GBP` | Enum, so an unsupported currency is rejected at deserialisation and no code downstream can ever receive one. |
| `type` | Required, one of `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, `REFUND` | Same reasoning. |
| `status` | **Not accepted on create** | See below. |

### Business rules beyond the annotations

**Status is never supplied by the caller.** `CreateTransactionRequest` has no
`status` field at all, so a caller cannot declare that a payment has already
completed. Every transaction begins `PENDING`. A request that includes a status
is accepted, and the status is ignored — there is a test for this.

**The duplicate check is explicit, and has to be.** With a caller-supplied
primary key, Spring Data's `save()` performs a *merge*: it looks the row up and
updates it if it is already there. Relying on the database to reject the
duplicate would therefore not work — the second request would silently overwrite
the first. `TransactionService.create` checks `existsById` first and throws.
`TransactionApiTest` asserts that the original record survives a duplicate
attempt, not merely that the second request failed.

**Amount precision is enforced, not corrected.** An amount with too many decimal
places is rejected rather than rounded. Rounding someone's money without telling
them is worse than refusing the request.

---

## 4. Status transitions

```
                 ┌──────────────┐
                 │   PENDING    │  ← every transaction starts here
                 └──┬────┬────┬─┘
        ┌───────────┘    │    └───────────┐
        ▼                ▼                ▼
  ┌───────────┐   ┌────────────┐   ┌────────────┐
  │ COMPLETED │   │   FAILED   │   │ CANCELLED  │
  └─────┬─────┘   └────────────┘   └────────────┘
        │              final             final
        ▼
  ┌────────────┐
  │  REVERSED  │  final
  └────────────┘
```

**The reasoning:**

- `PENDING` means accepted and recorded, but the money has not moved. From
  there the money either moves (`COMPLETED`), is attempted and does not move
  (`FAILED`), or is withdrawn before it settles (`CANCELLED`).
- **A completed transaction cannot be un-completed.** If it must be undone, it
  moves to `REVERSED`, which preserves the fact that money moved and then came
  back. Moving it back to `PENDING` would destroy that history and leave the
  record claiming something that never happened.
- **`FAILED`, `CANCELLED` and `REVERSED` are final.** A settled financial record
  should not be rewritten — the history is the point. A transaction that needs
  to happen after all is a *new* transaction.
- **No status may transition to itself.** A no-op update almost always means the
  caller has lost track of the current state, so it is reported rather than
  silently accepted. This is a defensible call rather than an obvious one: the
  alternative is to treat it as idempotent and return 200, which is friendlier
  to a client retrying after a timeout. I chose the stricter reading because a
  status transition table with self-loops is not a state machine, but I would
  change this without much argument if callers were unreliable networks rather
  than services.

**Where these rules live:** on the `TransactionStatus` enum, where each constant
holds its own set of permitted successors, and enforced by
`Transaction.changeStatusTo()`. Because that method is the only way to alter a
status, no caller — including one written later — can put a transaction into an
illegal state. Adding a status means editing one file.

---

## 5. API

Base path `/api/transactions`. All bodies are JSON.

### Create a transaction

```
POST /api/transactions
```

```json
{
  "transactionId": "TXN-00000001",
  "customerId": "CUST-1001",
  "amount": 250.75,
  "currency": "INR",
  "type": "DEPOSIT"
}
```

`201 Created`, with a `Location` header pointing at the new resource:

```json
{
  "transactionId": "TXN-00000001",
  "customerId": "CUST-1001",
  "amount": 250.75,
  "currency": "INR",
  "type": "DEPOSIT",
  "status": "PENDING",
  "createdAt": "2026-08-27T10:15:30.123456Z",
  "updatedAt": "2026-08-27T10:15:30.123456Z"
}
```

### Get one transaction

```
GET /api/transactions/{transactionId}
```

`200 OK` with the same shape, or `404` if there is no such transaction.

### Update the status

```
PATCH /api/transactions/{transactionId}/status
```

```json
{ "status": "COMPLETED" }
```

`200 OK` with the updated transaction. `PATCH` rather than `PUT` because the
request changes one part of the resource and leaves the rest alone.

### Get a customer's transactions

```
GET /api/transactions?customerId=CUST-1001
```

`200 OK` with an array, newest first. A customer with no transactions gets an
empty array, **not** a 404 — the question has a correct answer, and it is
"nothing". Modelled as a filter on the transactions collection rather than a
`/customers/{id}/transactions` sub-resource because this service owns
transactions and does not model customers at all.

### Errors

Every failure returns the same shape:

```json
{
  "timestamp": "2026-08-27T10:15:30.123456Z",
  "status": 409,
  "error": "Conflict",
  "message": "Duplicate transaction ID",
  "details": ["A transaction with ID 'TXN-00000001' already exists"],
  "path": "/api/transactions"
}
```

| Code | When |
|---|---|
| `400` | The request itself is wrong — a field failed validation, the body is not valid JSON, a value is outside a permitted enum, or a required query parameter is missing. |
| `404` | The request is fine; the transaction is not there. |
| `409` | The request is fine and well formed, but conflicts with the current state of the world: the ID is already taken, or the lifecycle forbids the status move. |

`details` carries **every** problem, not the first one — a caller fixing a bad
request gets all the field errors in one response instead of one per round trip.

A bad enum value is worth a note. Jackson rejects it while deserialising, before
Bean Validation ever runs, so by default it produces an opaque 400.
`ApiExceptionHandler` catches that case and reads the permitted values off the
enum itself, so `"currency": "JPY"` returns
`currency: 'JPY' is not permitted; allowed values are [INR, USD, EUR, GBP]` —
and the message stays correct if a currency is ever added.

---

## 6. Structure

```
transaction/
├── Transaction.java                     entity — owns the status invariant
├── TransactionStatus.java               lifecycle + transition rules
├── TransactionType.java, Currency.java  permitted values
├── TransactionRepository.java           Spring Data
├── TransactionService.java              business rules; knows nothing about HTTP
├── TransactionController.java           binds, calls, maps; no logic
├── CreateTransactionRequest.java        request DTOs, with the field rules
├── UpdateStatusRequest.java
├── TransactionResponse.java             response DTO
├── ApiError.java                        the one error shape
├── ApiExceptionHandler.java             exceptions → status codes, in one place
└── three exception types
```

Package-by-feature rather than package-by-layer: everything about transactions
sits together, so a second feature would be a sibling package rather than four
more classes spread across four existing ones.

The layering rule is that **dependencies point inwards**. The controller knows
the service; the service knows the repository and the entity; the entity knows
nothing. The service never mentions HTTP — it throws
`TransactionNotFoundException`, not `ResponseStatusException` — which is what
lets the business rules be tested without a servlet.

The entity is not exposed by the API. Response DTOs mean the persistence model
and the public contract can change independently, and request DTOs mean a caller
cannot set a field simply because the entity has one.

---

## 7. Testing

27 tests pass from a clean build. 26 of them are mine, in two classes split on
purpose; the 27th is the starter project's `contextLoads` sample, kept because a
test that proves the application context actually assembles is worth keeping.

**`TransactionApiTest`** (20 tests) — `@SpringBootTest` with `MockMvc`, driving the real
controller, the real service and a real H2 database. These are integration tests
rather than mocked unit tests because what is worth checking is what a caller
actually sees: the status code, the body, and whether the row really landed in
the database. A mocked repository would let all four operations pass while the
application was broken end to end. Several tests assert against the repository
*after* the HTTP call for exactly that reason.

**`TransactionStatusTest`** (6 tests) — plain JUnit, no Spring. The transition rules are
pure logic with no dependencies, so loading a context to test them would only
make the suite slower and the failures harder to read.

The database is emptied before each test, so no test depends on another having
run and they can be run in any order.

Coverage, beyond the four required cases:

- create: success, negative amount, over the ceiling, too many decimal places,
  duplicate ID (asserting the original *survives*), unsupported currency, all
  field errors reported at once, malformed JSON, caller-supplied status ignored
- get: found, not found
- status: valid move, completed→pending refused, change to a final status
  refused, same-status refused, unknown transaction, unknown status value
- customer: only that customer's transactions, empty list for an unknown
  customer, missing `customerId` parameter

Request bodies in the tests are JSON strings rather than serialised DTOs. That
is deliberate — it tests the wire format a caller actually sends, and it allows
sending values the DTO could not hold, which is what the validation tests need.

---

## 8. Known limitations

Named honestly rather than hidden.

1. **The duplicate check is not atomic.** `existsById` followed by `save` is
   check-then-act. Two concurrent requests with the same ID could both pass the
   check. `ApiExceptionHandler` catches `DataIntegrityViolationException` as a
   backstop so such a request fails with a 409 rather than a 500, but the real
   fix is to make the insert itself authoritative — have the entity implement
   `Persistable` so JPA issues a plain `INSERT` instead of a merge, and let the
   primary key constraint decide. I left the simpler version in because it is
   the one I can defend line by line.
2. **No optimistic locking.** Two concurrent status updates on the same
   transaction could interleave and the second could act on stale state. A
   `@Version` column would fix it; I did not add one because nothing else in the
   exercise is concurrent and it would be untested machinery.
3. **No catch-all exception handler.** This is deliberate. An
   `@ExceptionHandler(Exception.class)` would also intercept Spring's own
   exceptions — the 404 for an unknown URL, the 405 for a wrong method — and
   report them as 500s. So genuinely unexpected errors fall through to Spring's
   default error response and do not use the `ApiError` shape. The clean fix is
   to extend `ResponseEntityExceptionHandler`, which handles Spring's exceptions
   properly and still lets me control the body.
4. **Timestamps come from `Instant.now()` inside the entity.** That makes
   time-dependent behaviour untestable — I cannot assert that `updatedAt`
   changed by a known amount. Injecting a `Clock` would fix it.
5. **The customer lookup is unpaginated.** A customer with 100,000 transactions
   returns all of them in one response. It needs a `Pageable`.
6. **No authentication or authorisation.** Anyone who can reach the service can
   read any customer's transactions.
7. **H2 in memory — all data is lost on restart.** Correct for the exercise,
   obviously not for anything real.
8. **No database migrations.** The schema is generated by Hibernate
   (`ddl-auto: create-drop`). A real service needs Flyway or Liquibase; you
   cannot version a schema you did not write down.
9. **The 1,000,000 ceiling is per-transaction and currency-blind.** 1,000,000
   INR and 1,000,000 GBP are very different amounts of money. Per-currency
   limits would be more honest.
10. **`updateStatus` calls `repository.save()` redundantly.** Inside a
    `@Transactional` method the entity is managed and Hibernate's dirty checking
    would persist the change anyway. I left the explicit `save()` because it
    makes the intent readable at the call site, but it is not doing any work.

## 9. What I would do next, in priority order

1. Make the insert authoritative (limitation 1) — it is the only limitation here
   that can actually lose a record.
2. Add `@Version` for optimistic locking on status updates.
3. Extend `ResponseEntityExceptionHandler` so every failure, including
   unexpected ones, returns the `ApiError` shape.
4. Paginate the customer lookup.
5. Inject a `Clock` so timestamps are testable.
6. Add Flyway, and stop generating the schema at startup.
7. Authentication, and scope a customer lookup to the caller's own data.
