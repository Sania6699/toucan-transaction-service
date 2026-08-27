# Transaction Starter Project

This is the starter project for the Customer Transactions exercise.

## Before you start

The first thing you should do after cloning the repository is:

### Linux / macOS

```bash
./mvnw clean test
```

### Windows

```bat
mvnw.cmd clean test
```

The sample test should pass before you begin implementing the exercise.

## What is already provided

- Java 17
- Spring Boot
- Maven wrapper
- Spring Web
- Spring Data JPA
- H2 embedded database
- JUnit / Spring Boot Test
- A sample REST endpoint: `GET /api/sample`
- A sample test that loads the Spring context


## Exercise

Implement these four operations:

1. Create transaction
2. Get transaction
3. Update transaction status
4. Get all transactions for a customer


You may change the surrounding design if you believe your solution is better.

## Transaction fields

Every transaction contains:

- Transaction ID
- Customer ID
- Amount
- Currency
- Transaction Type
- Transaction Status

### Validation rules

Define what makes a transaction valid. At minimum, consider:

- Transaction ID
- Customer ID
- Amount
- Currency
- Transaction type
- Initial status

Also explain any business validation you add beyond the annotations already supplied.

## API skeleton

### Create

`TODO`

### Get

`TODO`

### Update status

`TODO`

### Get customer transactions

`TODO`

## Testing expectations

Add at least four meaningful tests.

You decide exactly which tests provide the best coverage.
