# AI Usage Disclosure

> **BEFORE SUBMITTING: sections 4, 5 and 6 must be rewritten in your own words.**
> They ask what *you* changed, what *you* caught, and how *you* checked. What is
> written there now is a record of what happened during the session, kept as a
> starting point — but anything you cannot demonstrate live should be deleted
> rather than left in. This document is read alongside a live interview.

## 1. Tools used

- Claude (Anthropic), via the Claude desktop app, working directly in the
  project folder.

## 2. What I used it for

- Discussing how to structure the service: which layers, what belongs where,
  and where the status-transition rules should live.
- Writing the bulk of the Java: entity, enums, repository, service, controller,
  DTOs, exception types and the exception handler.
- Writing the test suite.
- Drafting this README.

I did not use it to decide the validation rules or the status lifecycle without
review — those are choices the exercise asks the candidate to make, and I went
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
- **Not adding a catch-all `@ExceptionHandler(Exception.class)`** — it would
  also intercept Spring's own 404 and 405 handling and report them as 500s.

## 4. What I changed, corrected or rejected  — REWRITE THIS

Two corrections were made during the session and are worth keeping if you can
explain them:

- The entity originally mapped the transaction type to a column literally named
  `type`. That was renamed to `transaction_type`, because `type` is close enough
  to reserved across SQL dialects to be a portability risk for no benefit.
- One test asserted on a customer list using a JsonPath filter expression
  (`$[?(@.customerId == 'CUST-2002')]`). That was replaced with a plain Hamcrest
  matcher (`everyItem(is(...))`), because the assertion should be obvious to
  read and should not depend on JsonPath filter semantics.

<!-- Add here, in your own words:
     - anything you changed after reading the generated code
     - anything you rejected and why
     - any rule you decided differently from what was suggested
     Delete this comment before submitting. -->

## 5. What the AI got wrong that I had to fix — REWRITE THIS

<!-- Be specific and honest. If the first build failed, say what failed and what
     the fix was — a compile error, a wrong assertion, a bad import. This is one
     of the three sections the reviewers say they read most closely, and a
     truthful small example is worth far more than a claim that nothing broke.
     Delete this comment before submitting. -->

## 6. How I checked the result actually works — REWRITE THIS

<!-- Describe what you actually did. For example:
     - ran `mvnw.cmd clean test` from a clean clone and confirmed N tests pass
     - started the app with `mvnw.cmd spring-boot:run` and exercised all four
       endpoints by hand, including the failure cases
     - deliberately broke a rule (e.g. changed a transition) and confirmed the
       right test failed — this is the strongest possible evidence that the
       tests assert something real
     Delete this comment before submitting. -->

## 7. Understanding

I can walk through every class in this submission, explain why each validation
rule and status transition is what it is, name the limitations in section 8 of
the README, and modify the code live.
