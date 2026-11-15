\set ON_ERROR_STOP on
\connect enterprise_crm

-- 1. Create dedicated auth schema
CREATE SCHEMA IF NOT EXISTS auth;

-- 2. Create dedicated auth user if not exists
DO
$do$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles
      WHERE rolname = 'auth_user') THEN
      CREATE ROLE auth_user WITH LOGIN PASSWORD 'auth_password_1234';
   END IF;
END
$do$;

-- 3. Grant schema permissions to auth_user
GRANT USAGE, CREATE ON SCHEMA auth TO auth_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA auth TO auth_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA auth TO auth_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA auth GRANT ALL ON TABLES TO auth_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA auth GRANT ALL ON SEQUENCES TO auth_user;

-- Ensure auth_user has no permissions on public schema (CRM tables)
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM auth_user;
REVOKE ALL ON SCHEMA public FROM auth_user;

-- Ensure crm_reader has no permissions on auth schema
REVOKE ALL ON SCHEMA auth FROM crm_reader;

-- 4. Create auth.users table
CREATE TABLE IF NOT EXISTS auth.users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ANALYST'
        CHECK (role IN ('ADMIN', 'ANALYST', 'VIEWER')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_auth_user_email CHECK (email LIKE '%@%.%')
);

CREATE INDEX IF NOT EXISTS idx_auth_users_username ON auth.users (username);
CREATE INDEX IF NOT EXISTS idx_auth_users_email ON auth.users (email);
