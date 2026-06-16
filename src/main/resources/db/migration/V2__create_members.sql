-- ============================================================
-- V2__create_members.sql
-- Expand the members table with all Phase 2 fields and add
-- the member_number sequence counter.
-- ============================================================

-- Sequence table for generating member numbers (YYYY-NNN)
CREATE TABLE IF NOT EXISTS member_number_seq (
    next_val INTEGER NOT NULL DEFAULT 1
);
INSERT OR IGNORE INTO member_number_seq (next_val) VALUES (1);

-- SQLite does not support ADD COLUMN with constraints or RENAME COLUMN,
-- so we recreate the members table with the full Phase 2 schema and
-- migrate any existing rows from V1.

ALTER TABLE members RENAME TO members_v1;

CREATE TABLE IF NOT EXISTS members (
    id                  TEXT PRIMARY KEY,
    member_number       TEXT UNIQUE NOT NULL,
    full_name           TEXT NOT NULL,
    phone_number        TEXT,
    email               TEXT,
    address             TEXT,
    postal_code         TEXT,
    city                TEXT,
    place               TEXT,
    parish              TEXT,
    partner_name        TEXT,
    partner_phone       TEXT,
    partner_email       TEXT,
    child1              TEXT,
    child2              TEXT,
    child3              TEXT,
    child4              TEXT,
    child5              TEXT,
    application_holder  TEXT,
    signing_date        TEXT,                           -- ISO-8601 date
    iban                TEXT,
    payment_method      TEXT CHECK(payment_method IN ('DIRECT_DEBIT','TRANSFER','CASH')),
    status              TEXT NOT NULL DEFAULT 'ACTIVE'
                             CHECK(status IN ('ACTIVE','INACTIVE')),
    category            TEXT,
    household_group     TEXT,
    created_at          TEXT NOT NULL,
    updated_at          TEXT NOT NULL,
    version             INTEGER NOT NULL DEFAULT 0,
    checksum            TEXT
);

-- Migrate existing V1 rows.
-- phone  → phone_number   (renamed column)
-- join_date → signing_date (renamed column)
-- member_number gets a LEGACY-{uuid} placeholder — updated on next save.
INSERT OR IGNORE INTO members (
    id, member_number, full_name, phone_number, email, address,
    signing_date, status, category, household_group,
    created_at, updated_at, version, checksum
)
SELECT
    id,
    'LEGACY-' || id,
    full_name,
    phone,
    email,
    address,
    join_date,
    status,
    category,
    household_group,
    created_at,
    updated_at,
    version,
    checksum
FROM members_v1;

DROP TABLE members_v1;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_members_status        ON members(status);
CREATE INDEX IF NOT EXISTS idx_members_household     ON members(household_group);
CREATE INDEX IF NOT EXISTS idx_members_member_number ON members(member_number);
CREATE INDEX IF NOT EXISTS idx_members_parish        ON members(parish);
