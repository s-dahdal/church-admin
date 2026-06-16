-- ============================================================
-- V1__init.sql  —  Church Admin initial schema
-- ============================================================

-- Transaction categories (must exist before transactions reference them)
CREATE TABLE IF NOT EXISTS transaction_categories (
    id            TEXT    NOT NULL PRIMARY KEY,   -- UUID
    created_at    TEXT    NOT NULL,
    updated_at    TEXT    NOT NULL,
    version       INTEGER NOT NULL DEFAULT 0,
    checksum      TEXT    NOT NULL,

    name          TEXT    NOT NULL,
    type          TEXT    NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    is_default    INTEGER NOT NULL DEFAULT 0       -- 1 = true, 0 = false (SQLite boolean)
);

-- Members
CREATE TABLE IF NOT EXISTS members (
    id              TEXT    NOT NULL PRIMARY KEY,  -- UUID
    created_at      TEXT    NOT NULL,
    updated_at      TEXT    NOT NULL,
    version         INTEGER NOT NULL DEFAULT 0,
    checksum        TEXT    NOT NULL,

    full_name       TEXT    NOT NULL,
    phone           TEXT,
    email           TEXT,
    address         TEXT,
    join_date       TEXT,                          -- ISO-8601 date
    status          TEXT    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    category        TEXT,
    household_group TEXT
);

-- Transactions
CREATE TABLE IF NOT EXISTS transactions (
    id              TEXT    NOT NULL PRIMARY KEY,  -- UUID
    created_at      TEXT    NOT NULL,
    updated_at      TEXT    NOT NULL,
    version         INTEGER NOT NULL DEFAULT 0,
    checksum        TEXT    NOT NULL,

    date            TEXT    NOT NULL,              -- ISO-8601 date
    amount          REAL    NOT NULL,
    type            TEXT    NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    description     TEXT,

    member_id       TEXT    REFERENCES members(id) ON DELETE SET NULL,
    category_id     TEXT    REFERENCES transaction_categories(id) ON DELETE SET NULL
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_transactions_member   ON transactions(member_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date     ON transactions(date);
CREATE INDEX IF NOT EXISTS idx_transactions_type     ON transactions(type);
CREATE INDEX IF NOT EXISTS idx_members_status        ON members(status);
CREATE INDEX IF NOT EXISTS idx_members_household     ON members(household_group);

-- ============================================================
-- Default transaction categories (seeded, is_default = 1)
-- ============================================================

INSERT OR IGNORE INTO transaction_categories (id, created_at, updated_at, version, checksum, name, type, is_default) VALUES
    ('00000000-0000-0000-0001-000000000001', datetime('now'), datetime('now'), 0, 'seed', 'Membership Fee',   'INCOME',  1),
    ('00000000-0000-0000-0001-000000000002', datetime('now'), datetime('now'), 0, 'seed', 'Donation',         'INCOME',  1),
    ('00000000-0000-0000-0001-000000000003', datetime('now'), datetime('now'), 0, 'seed', 'Event Income',     'INCOME',  1),
    ('00000000-0000-0000-0001-000000000004', datetime('now'), datetime('now'), 0, 'seed', 'Utilities',        'EXPENSE', 1),
    ('00000000-0000-0000-0001-000000000005', datetime('now'), datetime('now'), 0, 'seed', 'Rent',             'EXPENSE', 1),
    ('00000000-0000-0000-0001-000000000006', datetime('now'), datetime('now'), 0, 'seed', 'Event Costs',      'EXPENSE', 1),
    ('00000000-0000-0000-0001-000000000007', datetime('now'), datetime('now'), 0, 'seed', 'Salaries',         'EXPENSE', 1),
    ('00000000-0000-0000-0001-000000000008', datetime('now'), datetime('now'), 0, 'seed', 'Maintenance',      'EXPENSE', 1);
