# Agentic Analytics Platform

Day 1 foundation scaffold for an application-independent analytics platform. The repository contains a React frontend, a Spring Boot REST backend, and two independently runnable Spring Boot MCP server modules.

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

Copy `.env.example` to `.env`, replace the database URL, username, and password placeholders, and export the variables in the shell that starts the services. `.env` is ignored by Git. Database credentials are read from environment variables by each database server.

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

This milestone only establishes buildable application shells and configuration. Database schemas, MCP tools, analytics endpoints, and AI/LLM behavior are later work.
