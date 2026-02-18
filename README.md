# Internal Wallet Service

A production-grade closed-loop virtual wallet service built with **Spring Boot 4**, **Java 21**, **Supabase (PostgreSQL)**, **Flyway**, and **Spring Data JPA**.

---

## Architecture

```
┌──────────────────┐     ┌──────────────────────┐     ┌─────────────────┐
│  REST Controllers │────▶│    Service Layer      │────▶│   PostgreSQL     │
│  (Validation,    │     │  (Transactional,      │     │   (Supabase)     │
│   Idempotency)   │     │   Locking, Caching)   │     │                 │
└──────────────────┘     └──────────────────────┘     └─────────────────┘
```

### Transaction Flows

| Flow   | Route             | Ledger                          |
|--------|-------------------|---------------------------------|
| Top-up | TREASURY → USER   | DEBIT TREASURY / CREDIT USER    |
| Bonus  | TREASURY → USER   | DEBIT TREASURY / CREDIT USER    |
| Spend  | USER → REVENUE    | DEBIT USER / CREDIT REVENUE     |

### Concurrency & Safety

- **Pessimistic locking** (`SELECT … FOR UPDATE`) on wallet rows — balances serialised per wallet.
- **Deadlock-free** — wallets always locked in ascending `wallet_id` order.
- **Balance check after lock** — prevents TOCTOU race on concurrent spend requests.
- **Optimistic lock** (`@Version`) on `Wallet` — secondary safety net against stale writes.
- **Idempotency** — duplicate requests with the same `Idempotency-Key` return the cached original response with zero side effects, including for concurrent duplicate racing handled via DB unique constraint.

### Design Patterns

| Pattern | Where Applied |
|---------|---------------|
| Repository | `*Repository` interfaces (Spring Data JPA) |
| Service Layer | `WalletService`, `TransactionService` |
| DTO / Request-Response | `dto/` package — clean API boundary |
| Builder | `TransactionResponse`, `ErrorResponse` (`@Builder + @Jacksonized`) |
| Template Method | `executeTreasuryToUser()` shared by TOPUP and BONUS |
| Pessimistic Locking | `findByIdForUpdate()` + ascending ID lock order |
| Double-Entry Ledger | `createLedgerEntries()` — 1 DEBIT + 1 CREDIT per transaction |
| Idempotency | `IdempotencyKey` entity + `resolveConflict()` on concurrent duplicate |
| In-Memory Cache | `@Cacheable("assetTypes")` on `findByName()` — zero DB hits after warm-up |
| Centralized Exception Handler | `GlobalExceptionHandler` (`@RestControllerAdvice`) |
| Dependency Injection | Constructor injection via `@RequiredArgsConstructor` |

---

## Prerequisites

- Java 21
- Maven (or use the included `./mvnw` wrapper)
- A Supabase project (or any PostgreSQL 14+ database)

---

## Database Connection

Credentials are configured directly in `src/main/resources/application.yml`.

> **Supabase note:** Use the **direct connection** string (not the pooler) so that
> `SELECT … FOR UPDATE` works correctly. The direct host is
> `db.<project-ref>.supabase.co:5432` with `?sslmode=require`.

---

## Run Locally

```bash
./mvnw spring-boot:run
```

Flyway runs automatically on startup and applies `V1__schema.sql` + `V2__seed.sql` if not already applied. The service starts on **http://localhost:8080**.

---

## API Reference

### Idempotency-Key rules

Every mutating endpoint (`/topup`, `/bonus`, `/spend`) requires an `Idempotency-Key` header:

- Must be **16–64 characters**
- Must contain only **alphanumeric characters, hyphens (`-`), or underscores (`_`)**
- Recommended format: **UUID v4** — e.g. `550e8400-e29b-41d4-a716-446655440000`

```bash
# Generate a valid key on macOS / Linux
uuidgen | tr '[:upper:]' '[:lower:]'
```

---

### 1. Get Balance

```http
GET /api/v1/wallets/{userId}/balance?assetType={assetType}
```

```bash
curl -s "http://localhost:8080/api/v1/wallets/1/balance?assetType=GOLD_COINS"
```

**Response `200`:**
```json
{
  "userId": 1,
  "assetType": "GOLD_COINS",
  "balance": 100.000000
}
```

---

### 2. Top-up (Purchase Credits)

```http
POST /api/v1/transactions/topup
Idempotency-Key: <uuid>
```

```bash
curl -s -X POST http://localhost:8080/api/v1/transactions/topup \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "userId": 1,
    "assetType": "GOLD_COINS",
    "amount": 100,
    "referenceId": "PAYMENT-ABC-123"
  }'
```

**Response `200`:**
```json
{
  "transactionId": 9,
  "userId": 1,
  "assetType": "GOLD_COINS",
  "amount": 100,
  "transactionType": "TOPUP",
  "newBalance": 200.000000,
  "timestamp": "2026-02-19T07:30:00"
}
```

---

### 3. Bonus (Free Credits)

```http
POST /api/v1/transactions/bonus
Idempotency-Key: <uuid>
```

```bash
curl -s -X POST http://localhost:8080/api/v1/transactions/bonus \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 661f9511-f3ac-52e5-b827-557766551111" \
  -d '{
    "userId": 1,
    "assetType": "DIAMONDS",
    "amount": 50,
    "reason": "REFERRAL_BONUS"
  }'
```

---

### 4. Spend (Use Credits)

```http
POST /api/v1/transactions/spend
Idempotency-Key: <uuid>
```

```bash
curl -s -X POST http://localhost:8080/api/v1/transactions/spend \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 772a0622-04bd-63f6-c938-668877662222" \
  -d '{
    "userId": 1,
    "assetType": "GOLD_COINS",
    "amount": 30,
    "service": "IN_GAME_ITEM"
  }'
```

---

### Idempotency Replay

Repeat the exact same request with the same `Idempotency-Key` — the response is identical and no duplicate ledger entries are created:

```bash
KEY="550e8400-e29b-41d4-a716-446655440000"

# First call — processes the transaction
curl -s -X POST http://localhost:8080/api/v1/transactions/topup \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" \
  -d '{"userId":1,"assetType":"GOLD_COINS","amount":100,"referenceId":"PAY-001"}'

# Second call (same key) — returns exact same response, DB unchanged
curl -s -X POST http://localhost:8080/api/v1/transactions/topup \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $KEY" \
  -d '{"userId":1,"assetType":"GOLD_COINS","amount":100,"referenceId":"PAY-001"}'
```

---

## Error Responses

| Status | Trigger |
|--------|---------|
| `400` | Missing or invalid `Idempotency-Key`, DTO validation failure, malformed JSON |
| `404` | Unknown `userId`/`assetType` combination or unknown asset type name |
| `409` | Unexpected data conflict |
| `422` | Insufficient balance on spend |
| `500` | Infrastructure fault (e.g. missing system wallet) |

**400 — Invalid Idempotency-Key:**
```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "idempotencyKey: Idempotency-Key must be 16–64 characters",
  "fieldErrors": null,
  "timestamp": "2026-02-19T07:30:00"
}
```

**400 — DTO validation:**
```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "One or more fields failed validation",
  "fieldErrors": [
    { "field": "amount", "message": "amount must be greater than zero" }
  ],
  "timestamp": "2026-02-19T07:30:00"
}
```

**422 — Insufficient balance:**
```json
{
  "status": 422,
  "error": "Insufficient Balance",
  "message": "Insufficient balance: available=30.000000, requested=99999, assetType=GOLD_COINS",
  "fieldErrors": null,
  "timestamp": "2026-02-19T07:30:00"
}
```

---

## Seeded Data

| Entity | Details |
|--------|---------|
| Asset types | `GOLD_COINS`, `DIAMONDS`, `LOYALTY_POINTS` |
| System wallets | `TREASURY` (1,000,000 each asset), `REVENUE` (0 each asset) |
| User 1 | GOLD_COINS=100, DIAMONDS=50, LOYALTY_POINTS=200 |
| User 2 | GOLD_COINS=200, DIAMONDS=100, LOYALTY_POINTS=500 |

---

## Database Schema

```
asset_types        — id, name (UNIQUE), created_at
wallets            — id, user_id, wallet_type, wallet_name, asset_type_id, balance, version, created_at
                     UNIQUE(wallet_name, asset_type_id)
                     CHECK(wallet_type IN ('USER','SYSTEM'))
                     CHECK(wallet_type = 'SYSTEM' OR balance >= 0)
transactions       — id, transaction_type, reference_id, created_at
                     CHECK(transaction_type IN ('TOPUP','BONUS','SPEND'))
ledger_entries     — id, transaction_id, wallet_id, entry_type, amount, asset_type_id, created_at
                     CHECK(entry_type IN ('DEBIT','CREDIT'))
                     CHECK(amount > 0)
idempotency_keys   — id, idempotency_key (UNIQUE), response_body, http_status, created_at
```

### ACID Enforcement

| Property | Layer | Mechanism |
|----------|-------|-----------|
| **Atomicity** | Application | `@Transactional` — all ledger writes succeed or all roll back |
| **Consistency** | Database | `CHECK` constraints (V3) + `NOT NULL` + `UNIQUE` + FK references — invalid data rejected even via direct SQL |
| **Isolation** | Application + DB | `SELECT … FOR UPDATE` (pessimistic lock) + `READ_COMMITTED` isolation — concurrent spends serialised |
| **Durability** | Database | PostgreSQL WAL (Write-Ahead Logging) — committed transactions survive crashes |

### Indexes

| Index | Columns | Purpose |
|-------|---------|---------|
| `uq_asset_types_name` | `asset_types(name)` | O(1) asset type lookup |
| `uq_wallets_name_asset` | `wallets(wallet_name, asset_type_id)` | O(1) system wallet lookup |
| `idx_wallets_user_asset` | `wallets(user_id, asset_type_id)` | O(1) user wallet lookup per transaction |
| `idx_ledger_wallet` | `ledger_entries(wallet_id)` | O(1) all entries for a wallet |
| `idx_ledger_txn` | `ledger_entries(transaction_id)` | O(1) both ledger lines for a transaction |
| `idx_ledger_wallet_entry_type` | `ledger_entries(wallet_id, entry_type)` | Fast debit/credit audit per wallet |
| `idx_ledger_entry_type_created_at` | `ledger_entries(entry_type, created_at DESC)` | Fast global DEBIT/CREDIT reporting by time |

---

## Project Structure

```
src/main/java/com/internal_wallet/internal_wallet/
├── InternalWalletServiceApplication.java
├── config/
│   ├── CacheConfig.java          — in-memory ConcurrentMapCacheManager
│   ├── FlywayConfig.java         — explicit Flyway bean + EntityManagerFactory ordering
│   └── JacksonConfig.java        — ObjectMapper with JavaTimeModule
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
│   ├── AssetTypeRepository.java  — @Cacheable("assetTypes")
│   ├── WalletRepository.java     — SELECT FOR UPDATE
│   ├── TransactionRepository.java
│   ├── LedgerEntryRepository.java
│   └── IdempotencyKeyRepository.java
└── service/
    ├── WalletService.java
    └── TransactionService.java   — executeTreasuryToUser(), resolveConflict()

src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__schema.sql            — 5 tables + indexes
    ├── V2__seed.sql              — asset types, system wallets, 2 users
    └── V3__constraints_and_indexes.sql — CHECK constraints + DEBIT/CREDIT indexes
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 21, Spring Boot 4.0.2 |
| Web | Spring MVC (`spring-boot-starter-webmvc`) |
| Persistence | Spring Data JPA + Hibernate |
| Database | PostgreSQL via Supabase |
| Migrations | Flyway |
| Validation | Jakarta Bean Validation (`spring-boot-starter-validation`) |
| Caching | Spring Cache + `ConcurrentMapCacheManager` |
| JSON | Jackson + `jackson-datatype-jsr310` |
| Boilerplate | Lombok |
| Connection pool | HikariCP (tuned for Supabase free tier) |
