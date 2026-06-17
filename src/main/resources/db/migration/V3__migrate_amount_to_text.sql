-- ============================================================
-- V3__migrate_amount_to_text.sql
-- Migrate transactions.amount from REAL to TEXT so that
-- BigDecimalConverter can store exact decimal strings.
--
-- Background: V1 created the amount column as REAL (IEEE 754),
-- which loses precision for financial values.  TEXT + BigDecimalConverter
-- guarantees exact round-trip for any decimal amount.
-- ============================================================

ALTER TABLE transactions RENAME TO transactions_v2;

CREATE TABLE IF NOT EXISTS transactions (
    id          TEXT    NOT NULL PRIMARY KEY,
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL,
    version     INTEGER NOT NULL DEFAULT 0,
    checksum    TEXT    NOT NULL,
    date        TEXT    NOT NULL,               -- ISO-8601 via LocalDateConverter
    amount      TEXT    NOT NULL,               -- plain decimal string via BigDecimalConverter
    type        TEXT    NOT NULL CHECK(type IN ('INCOME', 'EXPENSE')),
    description TEXT,
    member_id   TEXT    REFERENCES members(id)              ON DELETE SET NULL,
    category_id TEXT    REFERENCES transaction_categories(id) ON DELETE SET NULL
);

-- Migrate existing rows; printf '%.2f' converts REAL → decimal string
INSERT OR IGNORE INTO transactions (
    id, created_at, updated_at, version, checksum,
    date, amount, type, description, member_id, category_id
)
SELECT
    id, created_at, updated_at, version, checksum,
    date, printf('%.10g', amount), type, description, member_id, category_id
FROM transactions_v2;

DROP TABLE transactions_v2;

-- Re-create indexes (dropped when original table was renamed)
CREATE INDEX IF NOT EXISTS idx_transactions_member   ON transactions(member_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date     ON transactions(date);
CREATE INDEX IF NOT EXISTS idx_transactions_type     ON transactions(type);
CREATE INDEX IF NOT EXISTS idx_transactions_category ON transactions(category_id);