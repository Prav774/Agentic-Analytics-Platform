# Day 1 Foundation Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a buildable React frontend and a Java 21-targeted Spring Boot Maven reactor with one REST backend and two separate database MCP server applications.

**Architecture:** A single local Git repository contains the Vite frontend and a Maven reactor. The reactor has separate backend, CRM/PostgreSQL MCP server, and E-Commerce/MySQL MCP server modules; Spring AI starters provide MCP client and Streamable HTTP server transport while Spring AI and Spring Boot BOMs manage their dependency versions.

**Tech Stack:** Java 21 bytecode compiled with installed JDK 25, Maven 3.9.16, Spring Boot 4.1.2, Spring AI 2.0.2, BOM-managed MCP SDK 2.1.0, Node 24.14.1, npm 11.11.0, React 19.3.1, React Router 8.5.1, Axios 1.21.1, Vite 8.3.8, TypeScript 7.0.2, Tailwind CSS 4.3.5.

**Spec:** User-provided Day 1 sprint brief, Milestone 2 corrections in conversation.

## Global Constraints

- Preserve workspace path `E:\Agentic_Analysis`.
- Initialize Git locally; do not create a remote or commit.
- Compile Java classes with release 21 using installed JDK 25.
- Use Spring Boot 4.1.2 and Spring AI 2.0.2 with BOM-managed dependencies.
- Let Spring AI manage MCP SDK transitives; do not manually override the SDK version.
- Keep CRM/PostgreSQL and E-Commerce/MySQL MCP servers as separate Spring Boot modules using official MCP Streamable HTTP transport.
- Keep database URLs, usernames, and passwords in environment variables; only example placeholders may appear in `.env.example`.
- Do not add database schemas, seed data, MCP tools, AI agents, or LLM behavior in this milestone.
- Verification requested: frontend production build and Maven compilation. Do not add or run tests in this milestone.

## Review Focus

- JDK 25 must emit Java 21-compatible class files; verify Maven compiler configuration and inspect effective compiler settings.
- Spring AI 2.0.2 and Spring Boot 4.1.2 must resolve together; run Maven compile for all three applications.
- MCP server transport must be Streamable HTTP and each database server must have its own port and credential environment variables; inspect module configuration.
- Frontend package versions must resolve into a lockfile and build under Node 24; run `npm run build`.
- Git must be initialized at the requested workspace root and `.env` must be ignored while `.env.example` remains tracked.

---

### Task 1: Root repository and Maven version management

**Files:**
- Create: `.gitignore`
- Create: `.env.example`
- Create: `pom.xml`
- Create: `README.md`

**Interfaces:**
- Produces the Maven parent with modules `backend`, `mcp-servers/crm-mcp-server`, and `mcp-servers/ecommerce-mcp-server`.
- Sets Java release 21, Spring Boot parent 4.1.2, and imports Spring AI BOM 2.0.2.

- [x] Initialize Git at `E:\Agentic_Analysis` and confirm it is the repository root.
- [x] Create the aggregator POM and shared dependency management; use Boot-managed versions for validation, JDBC, Actuator, Spring Web, JDBC drivers, and testing artifacts.
- [x] Add `.gitignore` rules for environment files, Java build output, Node dependencies/build output, and IDE files; explicitly preserve `.env.example`.
- [x] Add placeholder-only database and service environment variables to `.env.example`.
- [x] Add a concise README describing the module layout, version matrix, and build commands without claiming later milestone functionality.

### Task 2: Spring Boot application modules

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/analytics/AnalyticsApplication.java`
- Create: `backend/src/main/resources/application.properties`
- Create: `mcp-servers/crm-mcp-server/pom.xml`
- Create: `mcp-servers/crm-mcp-server/src/main/java/com/analytics/crm/CrmMcpServerApplication.java`
- Create: `mcp-servers/crm-mcp-server/src/main/resources/application.properties`
- Create: `mcp-servers/ecommerce-mcp-server/pom.xml`
- Create: `mcp-servers/ecommerce-mcp-server/src/main/java/com/analytics/ecommerce/EcommerceMcpServerApplication.java`
- Create: `mcp-servers/ecommerce-mcp-server/src/main/resources/application.properties`

**Interfaces:**
- Backend uses `spring-ai-starter-mcp-client`, Spring Web, Validation, and Actuator.
- Each MCP server uses `spring-ai-starter-mcp-server-webmvc`, JDBC, and its database driver.
- Server properties select `STREAMABLE`; ports are independently configurable and datasource credentials are required environment variables.

- [x] Add minimal Spring Boot entry points and module-specific dependencies.
- [x] Configure the backend MCP client with named Streamable HTTP connections using configurable CRM and E-Commerce base URLs.
- [x] Configure CRM MCP server on its own port with PostgreSQL driver and `CRM_DB_URL`, `CRM_DB_USERNAME`, `CRM_DB_PASSWORD` placeholders.
- [x] Configure E-Commerce MCP server on its own port with MySQL driver and `ECOMMERCE_DB_URL`, `ECOMMERCE_DB_USERNAME`, `ECOMMERCE_DB_PASSWORD` placeholders.
- [x] Keep database schemas and MCP tool beans out of these modules.

### Task 3: React/Vite frontend scaffold

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/package-lock.json`
- Create: `frontend/index.html`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.app.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/index.css`

**Interfaces:**
- Uses the exact frontend dependency versions in the agreed dependency matrix.
- Reads `VITE_API_BASE_URL` from the frontend environment for later API integration; no fake API data or statuses are added.

- [x] Create the minimal React entry point and Vite configuration.
- [x] Enable Tailwind CSS v4 through `@tailwindcss/vite` and its CSS import.
- [x] Pin direct dependency versions and generate the npm lockfile.
- [x] Build the scaffold with `npm run build`.

### Task 4: Verify and report scaffold

**Files:**
- Modify: `README.md` only if build instructions need correction.

- [x] Run `mvn -pl backend,mcp-servers/crm-mcp-server,mcp-servers/ecommerce-mcp-server -am compile` with Maven using JDK 25.
- [x] Confirm compiler output is configured for release 21.
- [x] Run `npm.cmd run build` in `frontend`.
- [x] Inspect Git status and enumerate every created file; report actual command results and any remaining failures.
