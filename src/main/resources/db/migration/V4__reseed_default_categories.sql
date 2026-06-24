-- ============================================================
-- V4__reseed_default_categories.sql
--
-- Re-inserts the 8 default transaction categories that may have
-- been wiped by a snapshot restore.
--
-- Uses INSERT OR IGNORE so this migration is safe to run on any
-- database:
--   • Empty table  → all 8 rows are inserted (restore scenario).
--   • Table intact → OR IGNORE skips every row, no-op.
--
-- The fixed UUIDs (00000000-0000-0001-*) match the V1 seed so
-- any existing data linked to these IDs is unaffected.
-- ============================================================

INSERT OR IGNORE INTO transaction_categories
    (id, created_at, updated_at, version, checksum, name, type, is_default)
VALUES
    ('00000000-0000-0000-0001-000000000001', datetime('now'), datetime('now'), 0, 'seed', 'Membership Fee', 'INCOME',  1),
    ('00000000-0000-0000-0001-000000000002', datetime('now'), datetime('now'), 0, 'seed', 'Donation',       'INCOME',  1),
    ('00000000-0000-0000-0001-000000000003', datetime('now'), datetime('now'), 0, 'seed', 'Event Income',   'INCOME',  1),
    ('00000000-0000-0000-0001-000000000004', datetime('now'), datetime('now'), 0, 'seed', 'Utilities',      'EXPENSE', 1),
    ('00000000-0000-0000-0001-000000000005', datetime('now'), datetime('now'), 0, 'seed', 'Rent',           'EXPENSE', 1),
    ('00000000-0000-0000-0001-000000000006', datetime('now'), datetime('now'), 0, 'seed', 'Event Costs',    'EXPENSE', 1),
    ('00000000-0000-0000-0001-000000000007', datetime('now'), datetime('now'), 0, 'seed', 'Salaries',       'EXPENSE', 1),
    ('00000000-0000-0000-0001-000000000008', datetime('now'), datetime('now'), 0, 'seed', 'Maintenance',    'EXPENSE', 1);