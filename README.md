# Internal Wallet Service

A production-grade closed-loop virtual wallet service built with **Spring Boot 4**, **Java 21**, **Supabase (PostgreSQL)**, **Flyway**, and **Spring Data JPA**.

## Architecture

```
┌──────────────┐       ┌──────────────────┐       ┌────────────────┐
│   REST API   │──────▶│  Service Layer   │──────▶│   PostgreSQL   │
│ Controllers  │       │ (Transactional)  │       │  (Supabase)    │
└──────────────┘       └──────────────────┘       └────────────────┘
```

### Transaction Flows

| Flow    | Route              | Ledger |
|---------|--------------------|--------|
| Top-up  | TREASURY → USER    | DEBIT TREASURY / CREDIT USER |
| Bonus   | TREASURY → USER    | DEBIT TREASURY / CREDIT USER |
| Spend   | USER → REVENUE     | DEBIT USER / CREDIT REVENUE  |

### Concurrency & Safety

- **Pessimistic locking** (`SELECT … FOR UPDATE`) on wallet rows — balances are serialised per wallet.
- **Deadlock-free** — wallets are always locked in ascending `wallet_id` order.
- **Balance check after lock** — prevents TOCTOU race conditions on concurrent spend requests.
- **Idempotency** — duplicate requests with the same `Idempotency-Key` return the original response with no side effects.

---

## Prerequisites

- Java 21
- Maven (or use the included `./mvnw` wrapper)
- A Supabase project (or any PostgreSQL 14+ database)

---

## Configure Supabase DB Connection

Set the following environment variables before running:

```bash
export DB_HOST=db.<your-project-ref>.supabase.co
export DB_PORT=5432
export DB_NAME=postgres
export DB_USER=postgres
export DB_PASSWORD=<your-supabase-password>
```

Alternatively, create a `.env` file and source it, or pass them directly to Maven.

> **Supabase note:** Use the **direct connection** string (not the pooler) so that
> `SELECT … FOR UPDATE` works correctly. The direct host is
> `db.<project-ref>.supabase.co:5432`.

---

## Run Migrations

Flyway runs automatically on startup. Migrations live in:

```
src/main/resources/db/migration/
├── V1__schema.sql   — creates all 5 tables + indexes
└── V2__seed.sql     — seeds asset types, system wallets, and 2 users with initial balances
```

To run migrations manually (without starting the full app):

```bash
./mvnw flyway:migrate \
  -Dflyway.url=jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME \
  -Dflyway.user=$DB_USER \
  -Dflyway.password=$DB_PASSWORD
```

---

## Run Locally

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=wallet_db
export DB_USER=postgres
export DB_PASSWORD=password

./mvnw spring-boot:run
```

The service starts on **http://localhost:8080**.

---

## API Reference

### 1. Get Balance

```bash
curl -s "http://localhost:8080/api/v1/wallets/1/balance?assetType=GOLD_COINS" | jq .
```

**Response:**
```json
{
  "userId": 1,
  "assetType": "GOLD_COINS",
  "balance": 100.000000
}
```

---

### 2. Top-up (Purchase Credits)

```bash
curl -s -X POST http://localhost:8080/api/v1/transactions/topup \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: topup-$(uuidgen)" \
  -d '{
    "userId": 1,
    "assetType": "GOLD_COINS",
    "amount": 100,
    "referenceId": "PAYMENT-ABC-123"
  }' | jq .
```

**Response:**
```json
{
  "transactionId": 7,
  "userId": 1,
  "assetType": "GOLD_COINS",
  "amount": 100.000000,
  "transactionType": "TOPUP",
  "newBalance": 200.000000,
  "timestamp": "2024-01-15T12:00:00"
}
```

---

### 3. Bonus (Free Credits)

```bash
curl -s -X POST http://localhost:8080/api/v1/transactions/bonus \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: bonus-$(uuidgen)" \
  -d '{
    "userId": 1,
    "assetType": "DIAMONDS",
    "amount": 50,
    "reason": "REFERRAL_BONUS"
  }' | jq .
```

---

### 4. Spend (Use Credits)

```bash
curl -s -X POST http://localhost:8080/api/v1/transactions/spend \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: spend-$(uuidgen)" \
  -d '{
    "userId": 1,
    "assetType": "GOLD_COINS",
    "amount": 30,
    "service": "IN_GAME_ITEM"
  }' | jq .
```

---

### Idempotency Demo

Repeat the exact same request with the same `Idempotency-Key` — the response is identical and no duplicate ledger entries are created:

```bash
KEY="my-fixed-key-001"

# First call — processes the transaction
curl -s -X POST http://localhost:8080/api/v1/transactions/topup \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" \
  -d '{"userId":2,"assetType":"LOYALTY_POINTS","amount":100,"referenceId":"PAY-001"}' | jq .

# Second call — returns cached response, no side effects
curl -s -X POST http://localhost:8080/api/v1/transactions/topup \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" \
  -d '{"userId":2,"assetType":"LOYALTY_POINTS","amount":100,"referenceId":"PAY-001"}' | jq .
```

---

## Error Responses

| Status | Scenario |
|--------|----------|
| 400 | Missing `Idempotency-Key` header, validation failures, malformed JSON |
| 404 | Unknown `userId`/`assetType` combination or unknown asset type name |
| 422 | Insufficient balance on spend |
| 409 | Unexpected data conflict |
| 500 | Infrastructure fault (e.g. missing system wallet) |

**Example 422:**
```json
{
  "status": 422,
  "error": "Insufficient Balance",
  "message": "Insufficient balance: available=30.000000, requested=100.000000, assetType=GOLD_COINS",
  "timestamp": "2024-01-15T12:00:00"
}
```

**Example 400 (validation):**
```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "One or more fields failed validation",
  "fieldErrors": [
    { "field": "amount", "message": "amount must be greater than zero" }
  ],
  "timestamp": "2024-01-15T12:00:00"
}
```

---

## Seeded Data

| Entity | Details |
|--------|---------|
| Asset types | `GOLD_COINS`, `DIAMONDS`, `LOYALTY_POINTS` |
| System wallets | `TREASURY` (1,000,000 each), `REVENUE` (0 each) |
| User 1 | GOLD_COINS=100, DIAMONDS=50, LOYALTY_POINTS=200 |
| User 2 | GOLD_COINS=200, DIAMONDS=100, LOYALTY_POINTS=500 |

---

## Project Structure

```
src/main/java/com/internal_wallet/internal_wallet/
├── InternalWalletServiceApplication.java
├── controller/
│   ├── WalletController.java
│   └── TransactionController.java
├── dto/
│   ├── BalanceResponse.java
│   ├── TopupRequest.java
│   ├── BonusRequest.java
│   ├── SpendRequest.java
│   ├── TransactionResponse.java
│   └── ErrorResponse.java
├── entity/
│   ├── AssetType.java
│   ├── Wallet.java
│   ├── Transaction.java
│   ├── LedgerEntry.java
│   └── IdempotencyKey.java
├── enums/
│   ├── WalletType.java
│   ├── TransactionType.java
│   └── EntryType.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── InsufficientBalanceException.java
│   ├── WalletNotFoundException.java
│   └── AssetTypeNotFoundException.java
├── repository/
│   ├── AssetTypeRepository.java
│   ├── WalletRepository.java
│   ├── TransactionRepository.java
│   ├── LedgerEntryRepository.java
│   └── IdempotencyKeyRepository.java
└── service/
    ├─�� WalletService.java
    └── TransactionService.java

src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__schema.sql
    └── V2__seed.sql
```
