# FinSight backend

Requires Java 25 and an existing PostgreSQL database `finsight` on
`localhost:5432`, accessible as `postgres`.

## Local development

1. Copy `.env.example` to `.env` in the project root (beside `pom.xml`).
2. In your editor, replace the placeholder with your existing PostgreSQL password.
   Set `JWT_SECRET` to a private, cryptographically random value of at least 32
   UTF-8 bytes. Keep it stable across restarts; changing it invalidates existing JWTs.
3. From the project root, run:

```powershell
.\mvnw.cmd spring-boot:run
```

For IDE launches, set the working directory to this project root as well.
Stop the application with Ctrl+C.

Spring Boot loads `.env` through its built-in configuration import:

```properties
spring.config.import=optional:file:./.env[.properties]
spring.datasource.password=${DB_PASSWORD}
jwt.secret=${JWT_SECRET}
```

No dotenv dependency is needed. The extension hint loads `.env` as a Java
properties file, not as a shell script. Use `DB_PASSWORD=value` without surrounding
quotes, `export`, or inline comments. Quotes would become part of the password.
Escape each literal backslash as `\\`; escape any leading space as `\ `.
Keep the value on one line. Spring placeholder expressions such as `${...}`
have special meaning; use an environment variable instead if your password
contains that sequence.

The import is optional so deployments can supply `DB_PASSWORD` through the
 environment without a local file; a valid password is still required to connect.
Environment variables override file values. If authentication fails, check for
stale `DB_PASSWORD` or `SPRING_DATASOURCE_PASSWORD` environment overrides and
verify the existing password. Do not reset the password or recreate the database.

`.env` and `.env.*` are ignored by Git, except the secret-free `.env.example`.
Keep the real `.env` on your machine and outside `src/main/resources`; never
commit it, force-add it, share it, or paste its contents into logs or chat.

## JWT authentication

Registration and login under `/api/auth/**` remain public. For other endpoints,
send `Authorization: Bearer <token>` using the token returned by login.
Requests are stateless: no login session is saved. Missing, invalid, expired,
or unknown-user tokens receive HTTP 401 on protected endpoints.
The signing key is loaded from `.env` using the existing Spring Boot import;
no new dependency is required. Never put a real signing key in `.env.example`.

## Registration defaults, validation, and errors

New registrations atomically create six personal default categories (`isDefault=true`):
Food, Transport, Shopping, Bills, and Entertainment as EXPENSE; Salary as INCOME.
If initialization fails, registration rolls back. Initialization checks existing
defaults while locking the user's row, so sequential or concurrent retries do not
duplicate them. Repeating registration with the same email returns 409. Custom
category POSTs always create non-default categories; client-supplied default flags
are ignored. Existing accounts are not automatically backfilled.

Request JSON shapes are unchanged, but POST/PUT bodies now use validated request
DTOs. Validation applies to incoming data rather than imposing new database constraints:

- Registration: nonblank name (up to 255 characters), valid nonblank email (up to
  254 characters), and a nonblank password of at least 8 characters and at most
  72 UTF-8 bytes (BCrypt's limit). No composition rules are required.
- Login: valid nonblank email and nonblank password within the BCrypt byte limit.
- Categories: nonblank name (up to 255 characters) and required INCOME/EXPENSE type.
- Transactions: positive required amount, required type/date/category ID; optional
  description up to 255 characters.
- Budgets: positive required amount, month 1–12, year 1900–2100, and required category ID.
- Amounts allow up to 10 integer digits and 2 decimal places, matching existing columns.
- Category references retain the shape `"category": {"id": 123}` with a positive ID.

Error responses share this shape (field errors are present only for validation):

```json
{
  "status": 400,
  "message": "Validation failed",
  "timestamp": "2026-09-24T00:00:00Z",
  "errors": {"amount": "must be greater than 0"}
}
```

Other errors use an empty `errors` object. Invalid JSON/types/validation return 400;
invalid login, missing authentication, or rejected JWTs return 401; inaccessible
owned resources return 404; duplicate registration returns 409. Responses do not
include submitted password values, token values, database errors, or stack traces.

## User-owned financial data

Transactions, budgets, and categories belong to the user authenticated by the
Bearer token. Keep the existing request shapes (including `category: {"id": ...}`
for transactions and budgets). Do not send `user` or `userId` for ownership:
the server determines ownership, ignores supplied ownership fields, and never
includes the associated user or password in financial responses. POST always
creates a new record, even if an existing `id` is supplied.

Lists contain only the current user's records. Transaction and budget GET/PUT/DELETE
by ID return 404 for missing, other-user, or unowned legacy records. Category
references must also belong to the current user: inaccessible references return
404; a missing category ID returns 400. Category endpoints remain POST and GET
list only. The `isDefault` flag does not grant shared access to a category.

### Existing local database

The existing `spring.jpa.hibernate.ddl-auto=update` adds nullable `user_id` foreign
key columns referencing `users(id)` on `transactions`, `budgets`, and `categories`
when the backend next starts. Nullable columns deliberately allow pre-authentication
rows to remain intact. All new API-created records have an owner. No records are
automatically assigned, deleted, or recreated, and unowned rows are hidden from
every user (including legacy default categories).

No deletion or database reset is required. Keep your existing `.env` and restart the
backend. New accounts receive default categories; existing accounts can continue
using their personal categories or create them through POST `/api/categories`.
Old test data may be recreated through the authenticated APIs; its old
unowned copies remain hidden. If you need to retain old data in the API, back up
the database and explicitly assign only records whose owner you know to that user.
Assign each referenced category to the same user as its transactions/budgets; do
not bulk-assign everything to whichever user logs in first. Shared legacy categories
need separate per-user copies when records belong to different users.

## Dashboard and analytics

Both endpoints require `Authorization: Bearer <token>` and always use the
authenticated user. There is no `userId` input. Legacy unowned rows are excluded.

### GET /api/dashboard

Returns these fields:

| Field | Meaning |
| --- | --- |
| `month` | Current calendar month, formatted `YYYY-MM` |
| `currentBalance` | All recorded INCOME minus all recorded EXPENSE transactions |
| `monthlyIncome`, `monthlyExpenses` | Totals within the current calendar month |
| `activeBudgetCount` | Number of budget records for the current month and year |
| `totalBudgetCount` | Number of all budget records belonging to this user, across all periods |
| `recentTransactions` | At most five records, ordered by transaction date descending, then ID descending |
| `budgetOverview` | Current-month budgets with `id`, `category`, `budgetAmount`, `amountSpent`, `remainingAmount`, `percentageUsed` |
| `categorySpending` | Current-month EXPENSE totals, each with `category` and `amountSpent` |

`category` contains only `id`, `name`, and `type`. Recent transactions contain
`id`, `amount`, `type`, `date`, `description`, and this category summary. No user
entities, hashes, or secrets appear in reporting responses.

Budget spending matches the authenticated user, category ID, month, and year,
and includes only EXPENSE transactions. Remaining amount is budget minus spending;
percentage used is spending / budget * 100, rounded HALF_UP to two decimal places.
Overspent budgets show negative remaining amounts and percentages above 100.
Legacy zero/non-positive budgets return 0.00 percentage instead of dividing by zero.
If multiple budgets exist for the same category/month, each shows that category's
same spending against its own limit; transactions are not counted again in totals.

### GET /api/analytics?months=6

`months` defaults to 6 and accepts 1–24; invalid values return the existing JSON
400 response. The interval includes the current month and the preceding months.
Response fields are `startMonth`, `endMonth` (inclusive `YYYY-MM`), `months`,
`monthlyTrends`, and `categorySpending`.

Each trend contains `month`, `income`, `expenses`, and `netAmount` (income minus
expenses). Trends run oldest to newest, including zero-filled months with no data.
They directly support income/expense comparison and monthly net charts. Category
spending covers the whole selected interval, grouped by category ID and sorted by
amount descending, then category ID. Categories with no expense activity are omitted.

Amounts use BigDecimal and are returned as JSON numbers with two decimal places.
Transaction type determines income/expense, not the category's type. All-time balance
and recent transactions include future-dated stored records. Monthly windows cover
the full month (first day inclusive, first day of the following month exclusive),
including future-dated records within that month; future months are excluded from
current-month reports and analytics. Monthly `netAmount` is not a cumulative balance.

The server's JVM time zone determines the current month; run it in the intended
business time zone (for example `Asia/Colombo`). An injectable Clock fixes dates in
tests. New accounts return zero totals, empty lists, and zero-filled monthly trends.
Queries aggregate in the database and fetch categories with budgets/recent transactions
to avoid per-record queries. No schema changes or new dependencies are required.

For PostgreSQL/Postman verification, create income and expense entries on each side
of a month/year boundary. Check dashboard totals, a budget's spending/remaining values,
the latest five records, and `/api/analytics?months=6`. Repeat with another user's JWT
and an empty account. Check `months=0` returns 400 and requests without JWT return 401.
H2 tests verify behavior, but PostgreSQL aggregate-query execution and local time-zone
configuration should also be checked on your running backend.

### Tests

Run `./mvnw.cmd test` from the project root. Tests use a test-only H2 in-memory
database with PostgreSQL compatibility mode, isolated from `.env` and your local
PostgreSQL database. This verifies ownership queries and API behavior but does not
apply or verify the schema update against an existing PostgreSQL installation.

For a PostgreSQL/Postman smoke test, register two new accounts and log in as each.
Verify each sees six separate default categories, retry registration for a 409,
and create a transaction/budget with one of that account's category IDs. Try an
amount of 0, budget month 13, malformed email, short password, and another user's
category/record ID. Check 400/404 JSON errors; wrong login credentials or a missing
Bearer token should return 401. No schema migration or `.env` change is needed for
this validation/default-category milestone.
