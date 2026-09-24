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

No deletion or database reset is required. Keep your existing `.env`, restart the
backend, log in, and create personal categories before using them in new transactions
or budgets. Old test data may be recreated through the authenticated APIs; its old
unowned copies remain hidden. If you need to retain old data in the API, back up
the database and explicitly assign only records whose owner you know to that user.
Assign each referenced category to the same user as its transactions/budgets; do
not bulk-assign everything to whichever user logs in first. Shared legacy categories
need separate per-user copies when records belong to different users.

### Tests

Run `./mvnw.cmd test` from the project root. Tests use a test-only H2 in-memory
database with PostgreSQL compatibility mode, isolated from `.env` and your local
PostgreSQL database. This verifies ownership queries and API behavior but does not
apply or verify the schema update against an existing PostgreSQL installation.
