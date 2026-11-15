# Agentic Analytics Platform

Foundation for an application-independent analytics platform. The repository contains a React frontend, a Spring Boot REST backend, separate CRM and E-Commerce MCP server modules, and SQL initialization and seed scripts for their databases.

## Dependency baseline

- Java bytecode target: 21 (built with the installed JDK 25)
- Spring Boot: 4.1.2
- Spring AI: 2.0.2
- MCP Java SDK: BOM-managed transitively by Spring AI (resolved as 2.1.0)
- Node.js: 24.14.1
- React/React DOM: 19.3.1; React Router: 8.5.1; Axios: 1.21.1
- Vite: 8.3.8; React Vite plugin: 6.3.0; TypeScript: 7.0.2; Tailwind CSS and Vite plugin: 4.3.5
- React type packages: 19.3.0; Node type package: 26.6.6

Spring AI 2.0.x supports Spring Boot 4.0.x and 4.1.x. The root Maven POM imports the Spring AI BOM; MCP SDK artifacts are not version-overridden. The database drivers are managed by Spring Boot 4.1.2 (PostgreSQL 42.7.14 and MySQL Connector/J 9.7.0).

## Environment

Copy `.env.example` to `.env`, fill in the blank values, and export the variables in the shell that starts the services. `.env` is ignored by Git. Runtime database credentials are read from environment variables by each database server. The `*_ADMIN_DB_*` variables are only for schema and seed setup; provision the runtime usernames as read-only database accounts separately.

## Database foundation

The schema scripts create the fixed sample databases `enterprise_crm` and `enterprise_ecommerce`. They use PostgreSQL `psql` and the MySQL command-line client; no Docker or application dependency is required. Seed scripts use fixed IDs and upserts, so rerunning them refreshes the deterministic sample rows. They do not drop tables or databases.

The CRM database contains customers, leads, opportunities, and activities. The E-Commerce database contains customers, products, orders, order items, and payments. Foreign keys are enforced by PostgreSQL and InnoDB. The seed scripts print row counts and execute monthly pipeline/revenue and relationship rollup queries.

The scripts require database administrator usernames/passwords for initialization. They do not create users or grant privileges. Keep runtime credentials in the `*_DB_USERNAME` and `*_DB_PASSWORD` variables; use dedicated read-only accounts for the MCP servers.

Load the variables in `.env` into the current PowerShell process without displaying their values:

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^([A-Z0-9_]+)=(.*)$') {
    [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
  }
}
```

Initialize and seed CRM. The schema script creates the database if needed, then creates its tables and indexes. Both scripts connect to the `postgres` maintenance database first and switch to `enterprise_crm` internally.

```powershell
$crmUri = [Uri]($env:CRM_DB_URL -replace '^jdbc:', '')
$crmPort = if ($crmUri.Port -gt 0) { $crmUri.Port } else { 5432 }
$crmAdminUrl = "postgresql://$($crmUri.Host):$crmPort/postgres$($crmUri.Query)"
$env:PGUSER = $env:CRM_ADMIN_DB_USERNAME
$env:PGPASSWORD = $env:CRM_ADMIN_DB_PASSWORD
psql $crmAdminUrl -X -v ON_ERROR_STOP=1 -f database/crm/schema.sql
psql $crmAdminUrl -X -v ON_ERROR_STOP=1 -f database/crm/seed.sql
```

Initialize and seed E-Commerce. Ensure `mysql` is available on `PATH` and that the admin account can create databases and tables.

```powershell
$ecommerceUri = [Uri]($env:ECOMMERCE_DB_URL -replace '^jdbc:', '')
$ecommercePort = if ($ecommerceUri.Port -gt 0) { $ecommerceUri.Port } else { 3306 }
$env:MYSQL_PWD = $env:ECOMMERCE_ADMIN_DB_PASSWORD
Get-Content -Raw database/ecommerce/schema.sql | mysql --protocol=TCP --host $ecommerceUri.Host --port $ecommercePort --user $env:ECOMMERCE_ADMIN_DB_USERNAME --default-character-set=utf8mb4
Get-Content -Raw database/ecommerce/seed.sql | mysql --protocol=TCP --host $ecommerceUri.Host --port $ecommercePort --user $env:ECOMMERCE_ADMIN_DB_USERNAME --default-character-set=utf8mb4
```

Expected seed fixture sizes are CRM: 24 customers, 48 leads, 36 opportunities, and 120 activities; E-Commerce: 24 customers, 24 products, 540 orders, 1,080 order items, and 540 payments. Generated activity and order dates span more than 12 months.

## Build

From the repository root:

```powershell
mvn -pl backend,mcp-servers/crm-mcp-server,mcp-servers/ecommerce-mcp-server -am compile
```

From `frontend/`:

```powershell
npm.cmd install
npm.cmd run build
```

## Modules

- `backend/` — Spring Boot REST application and Spring AI MCP client dependency.
- `mcp-servers/crm-mcp-server/` — separate PostgreSQL MCP server configured for Streamable HTTP.
- `mcp-servers/ecommerce-mcp-server/` — separate MySQL MCP server configured for Streamable HTTP.
- `frontend/` — Vite, React, TypeScript, and Tailwind CSS application scaffold.

## MCP database tools

The CRM and E-Commerce servers expose `list_tables`, `get_table_schema`, `get_relationships`, and `execute_readonly_query` over Streamable HTTP. The query tool accepts one parsed `SELECT` statement, restricts table references to its own database schema, uses the runtime read-only account, and applies a query timeout and row limit. Both servers bind to loopback by default.

Load `.env` into the current PowerShell process using the command in the Environment section above. Start each server from the repository root in a separate terminal:

```powershell
mvn -pl mcp-servers/crm-mcp-server spring-boot:run
mvn -pl mcp-servers/ecommerce-mcp-server spring-boot:run
```

The MCP endpoints are `http://127.0.0.1:8081/mcp` for CRM and `http://127.0.0.1:8082/mcp` for E-Commerce. `MCP_QUERY_TIMEOUT_SECONDS` and `MCP_QUERY_MAX_ROWS` configure query timeout and the server-side row cap; defaults are 10 seconds and 500 rows. The REST backend and AI/LLM behavior remain later work.
