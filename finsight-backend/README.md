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

The application uses Hibernate `ddl-auto=update`; normal startup may update
schema as entities are added. The context-loading test also requires a working
database and password configuration.
