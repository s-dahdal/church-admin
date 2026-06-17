# Phase 3 — Transactions Implementation Prompt

## Project context

You are continuing development of **Church Admin**, a standalone JavaFX + Spring Boot + SQLite desktop application. Phase 2 (Members) is complete and committed (`99a983f`). The tech stack is:

- **UI**: JavaFX + FXML + CSS (liturgical color theme in `app.css`)
- **Framework**: Spring Boot with Spring Data JPA + Hibernate
- **Database**: SQLite (single file, local)
- **Migrations**: Flyway
- **ORM converters in place**: `LocalDateConverter` and `LocalDateTimeConverter` with `@Converter(autoApply=true)` — all `LocalDate`/`LocalDateTime` fields are handled automatically
- **i18n**: `messages_en.properties`, `messages_nl.properties`, `messages_ar.properties`; RTL support via `rtl.css`
- **Architecture pattern**: `models/` → `repositories/` → `services/` → `controllers/` + `resources/fxml/`

All entities extend `BaseEntity` (fields: `id` UUID, `createdAt`, `updatedAt`, `version`, `checksum`).

---

## Goal

Implement **Phase 3 — Transactions** end-to-end, following the same layered pattern used in Phase 2.

---

## Step 1 — Data layer

### 1a. `BigDecimalConverter`

Before touching entities, add a `BigDecimalConverter` to `utils/`:

```java
@Converter(autoApply = true)
public class BigDecimalConverter implements AttributeConverter<BigDecimal, String> {
    // Store as TEXT in SQLite to avoid REAL precision loss
    // Convert null safely in both directions
}
```

### 1b. `TransactionType` enum

```java
public enum TransactionType { INCOME, EXPENSE }
```

### 1c. `TransactionCategory` entity

Fields: `name` (String), `type` (TransactionType), `isDefault` (boolean).  
Extends `BaseEntity`.

### 1d. `Transaction` entity

Fields: `date` (LocalDate), `amount` (BigDecimal), `type` (TransactionType), `description` (String), `member` (ManyToOne → Member, nullable), `category` (ManyToOne → TransactionCategory).  
Extends `BaseEntity`.  
`member` being null indicates a general church transaction (not linked to any member).

### 1e. Repositories

**`TransactionCategoryRepository`** — standard CRUD; add `findByType(TransactionType)`.

**`TransactionRepository`** — add:
- `findByMemberId(UUID memberId)`
- `findByDateBetween(LocalDate from, LocalDate to)`
- `findByType(TransactionType type)`
- `findByCategoryId(UUID categoryId)`
- `@Query` for sum of amounts by type (used for balance calculation)

### 1f. Flyway V3 migration — `V3__create_transactions.sql`

- Create `transaction_categories` table
- Create `transactions` table (`member_id` nullable FK → `members.id`)
- Seed default categories in the same script — mark them with `is_default = 1` so they cannot be deleted. Suggested defaults:

  | Name | Type |
  |---|---|
  | Membership fee | INCOME |
  | Donation | INCOME |
  | Event income | INCOME |
  | Utilities | EXPENSE |
  | Rent | EXPENSE |
  | Event costs | EXPENSE |
  | Other | INCOME |
  | Other | EXPENSE |

---

## Step 2 — Service layer

### `TransactionCategoryService`

- `findAll()`, `findByType(TransactionType)`
- `save(TransactionCategory)` — recalculate checksum before save
- `delete(TransactionCategory)` — block if any transactions reference this category; block if `isDefault == true`

### `FinancialService`

- `save(Transaction)` — recalculate checksum before save
- `delete(Transaction)` — no guard; transactions can always be deleted
- `getCurrentBalance()` — sum of all INCOME minus sum of all EXPENSE
- `getFilteredTransactions(LocalDate from, LocalDate to, TransactionType type, UUID categoryId, UUID memberId)` — all parameters optional/nullable
- `getTotalByMember(UUID memberId)` — sum of all transactions linked to a member (for member contribution display)

---

## Step 3 — UI: Transactions list screen

**Files**: `Transactions.fxml` + `TransactionsController`

Layout:

1. **Balance strip** at top — three tiles: current balance (large, prominent), total income, total expenses. Updates live when filters change.
2. **Filter bar** — date-from picker, date-to picker, type ComboBox (All / Income / Expense), category ComboBox (All + category names), free-text search field on description.
3. **TableView** — columns: Date, Description, Category, Type, Amount, Member (name or "—" if null). Amount column right-aligned; INCOME amounts in success color, EXPENSE in danger color (use CSS variables `--color-text-success` / `--color-text-danger`).
4. **Footer** — transaction count label (same pattern as Members list).
5. **Add button** — opens `TransactionDetail` for a new transaction.
6. **Double-click row** — opens `TransactionDetail` in edit mode.

---

## Step 4 — UI: Transaction detail form

**Files**: `TransactionDetail.fxml` + `TransactionDetailController`

Layout — single panel (no tabs needed):

- **Date** — DatePicker (locale-aware)
- **Type** — ComboBox or ToggleGroup: Income / Expense. Changing type should refresh the category ComboBox to show only matching categories.
- **Category** — ComboBox filtered by selected type
- **Amount** — TextField with `TextFormatter` restricted to numeric input, max 2 decimal places. Dutch locale uses comma decimal separator — honour active locale via `CurrencyFormatter`.
- **Description** — TextArea (3–4 rows)
- **Link to member** — CheckBox "Link to member". When checked, show a searchable ComboBox (autocomplete by member name). When unchecked, clear member selection.
- **Button bar** — Save / Cancel / Delete. Apply double-submit guard: `saveButton.setDisable(true)` at start of `onSave()`, re-enable only on error (same pattern as MemberDetail).

**Pre-fill behaviour**:
- When opened from a member's Transactions tab: pre-fill and lock the member field; default type to INCOME.
- When opened for a new general transaction: member field unchecked and hidden by default.

**Nav highlight fix** — call `contentArea.requestFocus()` after pushing this screen via `showView()`, matching the fix applied in Phase 2 for MemberDetail, so the Transactions nav button stays highlighted while the detail form is open.

---

## Step 5 — Member Transactions tab (integration)

Wire up the currently empty Transactions tab in `MemberDetail.fxml` / `MemberDetailController`:

- Add a `TableView` inside the tab showing transactions filtered to this member only (columns: Date, Description, Category, Type, Amount).
- Show member contribution total at the bottom of the tab.
- Add an **"Add fee"** button — opens `TransactionDetail` with member pre-filled and locked, type defaulted to INCOME.

---

## Step 6 — i18n

Add all new keys to all three locale files: `messages_en.properties`, `messages_nl.properties`, `messages_ar.properties`.

Keys needed (at minimum):
- Field labels: `transaction.date`, `transaction.type`, `transaction.category`, `transaction.amount`, `transaction.description`, `transaction.member`
- Type display values: `transaction.type.income`, `transaction.type.expense`
- Balance strip: `balance.current`, `balance.income`, `balance.expense`
- Filter labels: `filter.dateFrom`, `filter.dateTo`, `filter.allTypes`, `filter.allCategories`
- Buttons and status: `transaction.addFee`, `transaction.linkToMember`, `transaction.saved`, `transaction.deleted`
- Validation: `validation.amountInvalid`, `validation.categoryRequired`
- Category management errors: `category.deleteBlocked.inUse`, `category.deleteBlocked.default`

Currency display: verify `CurrencyFormatter` produces `€ 1.234,56` for Dutch and `€1,234.56` for English.

---

## Step 7 — Dashboard wiring

Extend `DashboardController` to wire the existing balance summary widget and recent transactions widget (currently placeholders):

- **Balance summary** → call `FinancialService.getCurrentBalance()` on load.
- **Recent transactions** → show last 5 transactions (date + description + amount). Clicking the widget or a "View all" link navigates to the Transactions list screen.

This partially fulfils Phase 7 (Dashboard) without requiring a dedicated pass.

---

## Known risks / things to verify

| Risk | Mitigation |
|---|---|
| `BigDecimal` precision in SQLite | Store as `TEXT` via `BigDecimalConverter`; never use `REAL` column type |
| Date range queries with SQLite `TEXT` columns | ISO-8601 strings sort correctly as text — verify with edge cases (month boundaries, year boundaries) |
| Member ComboBox autocomplete performance | Loading all active members at once is fine for current scale; add lazy loading only if degradation is observed |
| Nav highlight on TransactionDetail | Apply `contentArea.requestFocus()` fix — same as Phase 2 MemberDetail |
| Dutch decimal separator in amount input | `TextFormatter` must parse both `.` and `,` as decimal separator depending on active locale |

---

## Commit message

```
feat: Phase 3 Transactions — categories, general & member-linked transactions, balance ledger, dashboard wiring
```

---

## Out of scope for this phase

- Excel / PDF export of transaction reports (Phase 8)
- Import/export JSON serialisation of transactions (Phase 5 — entities just need to be export-ready)
- Snapshot system changes (Phase 6)
