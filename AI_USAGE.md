# AI Usage Disclosure

## 1. Tools used

- Claude (Anthropic), via the Claude desktop app, working directly in the
  project folder.

## 2. What I used it for

- Discussing how to structure the service: which layers, what belongs where,
  and where the status-transition rules should live.
- Writing the bulk of the Java: entity, enums, repository, service, controller,
  DTOs, exception types and the exception handler.
- Writing the test suite.
- Drafting the README.

I did not use it to decide the validation rules or the status lifecycle without
review. Those are choices the exercise asks the candidate to make, and I went
through each one and can give the reasoning for it.

## 3. What the AI generated or suggested that was significant

- **Putting the transition table on the `TransactionStatus` enum**, with each
  constant holding its own set of permitted successors, rather than writing
  if-statements in the service. Suggested by the AI; I kept it because it means
  adding a status touches one file.
- **Putting the status guard on the entity** (`Transaction.changeStatusTo`)
  rather than in the service, so that no caller anywhere can produce an illegal
  state.
- **The explicit `existsById` duplicate check, and the reason it is necessary.**
  I had assumed the database primary key would reject a duplicate. The AI
  pointed out that with a caller-supplied ID, Spring Data's `save()` performs a
  merge and would have silently *overwritten* the original record. This is the
  single most important thing I learned doing this exercise, and there is now a
  test that asserts the original survives.
- **Reading the permitted enum values off the enum itself** when building the
  error message for a bad currency, instead of hardcoding the list in a string.
- **Not adding a catch-all `@ExceptionHandler(Exception.class)`**, because it
  would also intercept Spring's own 404 and 405 handling and report them as
  500s.

## 4. What I changed, corrected or rejected

- **The transaction type column was renamed.** The entity originally mapped it
  to a column literally named `type`. I renamed it to `transaction_type`,
  because `type` is close enough to reserved across SQL dialects to be a
  portability risk for no benefit.
- **A test assertion was rewritten to be readable.** One test checked the
  customer list using a JsonPath filter expression
  (`$[?(@.customerId == 'CUST-2002')]`). I replaced it with a plain Hamcrest
  matcher (`everyItem(is(...))`), because an assertion should be obvious to read
  and should not depend on JsonPath filter semantics.
- **I rejected removing `REVERSED` from the lifecycle.** Having questioned how
  it was modelled (see section 5), the cleaner-looking option was to delete the
  status and let an undo be a new `REFUND` transaction. I decided against it:
  the API exposes a single transaction resource with no field linking one record
  to another, so the link a real reversal needs could not be expressed anyway,
  and making `COMPLETED` terminal would leave a transition table with nothing in
  it worth testing. I documented the trade-off instead of hiding it.

## 5. What the AI got wrong that I had to fix

**Reversal was modelled the way a state machine wants, not the way a ledger
works, and it was presented to me without that caveat.**

The generated design moves a `COMPLETED` transaction to a `REVERSED` status.
That reads well as a lifecycle, but it is not how money is actually undone. A
real ledger is append-only: the completed record is left untouched and a new,
linked offsetting entry is posted for the money coming back. Reconciliation
needs two dated events — money left on the 3rd, came back on the 5th — and a
single row that merely ends in `REVERSED` loses when the reversal happened. A
status flag also cannot express a partial reversal, which is common.

I noticed this while reading back the status transitions and asked about it
directly. It also exposed an inconsistency the generated model had not
addressed: `REFUND` was a `TransactionType` — a new record — while a reversal
was a status mutation, so the same idea was expressed two different ways with no
stated rule for choosing between them.

What I did about it: I kept the status, for the reasons in section 4, but I
wrote both problems into README section 4 rather than leaving them unstated, and
I defined the rule that separates the two. A `REFUND` is a new transaction where
both movements genuinely happened and the customer is owed money back. A
reversal says the original movement should never have stood — an error, a
duplicate posting, a settlement that failed after completion — so it is recorded
against the transaction that was wrong.

The lesson I took from it is that generated code is confident about internal
consistency and silent about domain fit, and the domain fit is the part nobody
else was going to check.

## 6. How I checked the result actually works

- **I checked as I went, not only at the end.** After each part of the service
  was in place I started the application and exercised the endpoints by hand
  with `curl`, rather than waiting until everything was written and trusting the
  test suite to tell me whether it worked. Running a request against a live
  server catches things a test can miss - a wrong status code, a response body
  that is technically valid but useless to read, an endpoint that is not mapped
  at the URL I thought it was.
- I ran `mvnw.cmd clean test` from a Command Prompt in the submission folder
  (`toucan-repo`) on 31 August 2026. It compiled 17 main and 3 test sources and
  reported **27 tests run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**.
  That run is the output pasted in `TEST_OUTPUT.txt`; the timestamp in it is the
  run I am describing, not an older one.
- The tests exercise the real HTTP stack, the real service and a real H2
  database rather than mocks, so a pass means the application works end to end
  and not merely that the mocks were configured to agree with each other.
- Where a test asserts a rejection, it also asserts that nothing was written.
  The duplicate-ID test goes further and checks that the *original* record
  survived unchanged, because the failure mode there is a silent overwrite
  rather than an error.
- **Checking by hand found a defect the tests did not.** I ran the eight `curl`
  calls above and pasted the output back into the assistant to review, and it
  spotted that `createdAt` was returned as `...710593600Z` by the create call and
  came back as `...710594Z` on the fetch a moment later. The cause is that
  `Instant.now()` is nanosecond precision and H2 stores `TIMESTAMP` at
  microsecond precision, so the object in the create response and the row in the
  database do not hold the same value. No test caught it, because no test
  compares a create response against a subsequent read - the suite checks each
  operation against what it expects, never one operation against another. I have
  recorded it as limitation 11 in the README rather than patching it, because I
  found it late and would rather name a defect accurately than change the entity
  without a test to hold the fix in place.
- The build runs on a JDK 24 toolchain against `release 17`, so the submission
  compiles against the Java version the project actually targets.

## 7. Where the line falls between the AI's work and mine

Stated plainly, because it is the thing this disclosure exists to answer.

**The AI wrote most of the Java.** The class layout, the exception handler, the
Javadoc and the test suite are largely its output. The commenting is heavier
than I would write unaided; I kept it because the reasoning it records is
reasoning I agree with and can defend, not because I was hiding behind it.

**The decisions the exercise asks for are mine.** The permitted currencies, the
1,000,000 ceiling, the two-decimal rule, the ID formats, and the shape of the
status lifecycle are choices I made and can argue for, including the ones I
would make differently at scale - the ceiling being currency-blind is the
weakest of them, and it is named as limitation 9.

**One thing I would not have arrived at alone** is why the explicit `existsById`
check is necessary. I assumed a primary key would reject a duplicate; that
`save()` performs a merge and would have silently overwritten the original
record is the single most useful thing I learned here, and it is now pinned by a
test that asserts the first record survives.

**One thing the AI would not have arrived at alone** is that a `COMPLETED` to
`REVERSED` status change is not how a ledger undoes money. It produced a
lifecycle that was internally consistent and said nothing about whether it
matched the domain. That gap is the thing I would watch for if I were using
these tools on real payment code: they are confident about structure and silent
about consequence.

I can walk through any class here, explain why each validation rule and status
transition is what it is, name the limitations in README section 8, and change
the code live.
