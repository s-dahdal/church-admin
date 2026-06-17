# Phase 2 — Members: Claude Code Execution Prompt

You are helping me build **Phase 2 (Members)** of a JavaFX + Spring Boot + SQLite desktop
application called **Church Admin**. The application is already scaffolded and running. Read
everything below before writing a single line of code.

---

## Current project state

- Spring Boot + JavaFX integration via `loader.setControllerFactory(springContext::getBean)`
- SQLite database with Flyway managing schema migrations (currently at V1)
- `BaseEntity.java` exists with: `id` (UUID), `createdAt`, `updatedAt`, `version` (@Version),
  `checksum` (SHA-256 string)
- `ddl-auto=none` — Flyway owns the schema entirely, Hibernate never touches DDL
- `@EnableJpaRepositories` and `@EntityScan` explicitly target root package `com.churchadmin`
- Java 21, Maven, Lombok
- i18n via Spring `MessageSource` + `.properties` files:
  `messages_en.properties`, `messages_nl.properties`, `messages_ar.properties`
- `ChecksumService` in `utils/` computes SHA-256 of a given string
- Main window has a sidebar that loads FXML screens into a central content area
- Dashboard screen already works

---

## Package structure

```
com.churchadmin
├── app/                  ← JavaFX entry point
├── config/               ← Spring+JavaFX bridge, JPA config, MessageSource
├── models/               ← JPA @Entity classes extending BaseEntity
│   └── enums/            ← Java enums
├── repositories/         ← Spring Data JPA interfaces
├── services/             ← Business logic
├── controllers/          ← JavaFX @Controllers (Spring beans)
└── utils/                ← ChecksumService, DateUtils, etc.

resources/
├── fxml/                 ← FXML layout files
├── css/                  ← Stylesheets
├── i18n/                 ← messages_en/nl/ar.properties
└── db/migration/         ← Flyway SQL scripts
```

---

## Member fields

```java
// Identity
private String memberNumber;      // human-readable e.g. "2026-001", unique, NOT the PK

// Personal
private String fullName;
private String phoneNumber;
private String email;

// Address
private String address;
private String postalCode;        // Dutch format: "1234 AB" + house number
private String city;
private String place;

// Church
private String parish;

// Partner
private String partnerName;
private String partnerPhone;
private String partnerEmail;

// Children
private String child1;
private String child2;
private String child3;
private String child4;
private String child5;

// Membership application
private String applicationHolder;
private LocalDate signingDate;

// Financial
private String iban;
private PaymentMethod paymentMethod;  // enum: DIRECT_DEBIT, TRANSFER, CASH

// Status
private MemberStatus status;          // enum: ACTIVE, INACTIVE
private String category;
private String householdGroup;
```

---

## Build order — execute each step fully before moving to the next

---

### Step 2.1 — Entity, enums, and repository

**Create `models/Member.java`**
- `@Entity @Table(name = "members")`
- Extends `BaseEntity`
- Lombok: `@Data @NoArgsConstructor @AllArgsConstructor @Builder @SuperBuilder`
- All fields listed above with appropriate `@Column` annotations
- `signingDate` as `LocalDate`
- `status` and `paymentMethod` stored as `@Enumerated(EnumType.STRING)`

**Create `models/enums/MemberStatus.java`**
```java
public enum MemberStatus { ACTIVE, INACTIVE }
```

**Create `models/enums/PaymentMethod.java`**
```java
public enum PaymentMethod { DIRECT_DEBIT, TRANSFER, CASH }
```

**Create `repositories/MemberRepository.java`**
- Extends `JpaRepository<Member, UUID>`
- Custom queries:
  - `List<Member> findByStatus(MemberStatus status)`
  - `List<Member> findByParish(String parish)`
  - `List<Member> findByFullNameContainingIgnoreCase(String name)`
  - `Optional<Member> findByMemberNumber(String memberNumber)`
  - `List<Member> findAllByOrderByFullNameAsc()`

---

### Step 2.2 — Flyway migration

**Create `resources/db/migration/V2__create_members.sql`**

```sql
CREATE TABLE IF NOT EXISTS member_number_seq (
    next_val INTEGER NOT NULL DEFAULT 1
);
INSERT INTO member_number_seq (next_val) VALUES (1);

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
    signing_date        TEXT,
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
```

> Do not modify V1. Only add V2.

---

### Step 2.3 — MemberService

**Create `services/MemberService.java`**

Annotate with `@Service @Transactional`. Inject `MemberRepository`,
`ChecksumService`, and `EntityManager` (for the sequence counter).

**Methods to implement:**

```
save(Member member)
```
- If `memberNumber` is null or blank, generate one:
  - Read `next_val` from `member_number_seq` using a native query
  - Increment it: `UPDATE member_number_seq SET next_val = next_val + 1`
  - Format as `"YYYY-NNN"` using the current year and zero-padded counter, e.g. `"2026-001"`
- Recompute checksum: concatenate all business fields into one string, pass to `ChecksumService`
- Set `updatedAt = LocalDateTime.now()`
- Call `memberRepository.save(member)`

```
deactivate(UUID id)
```
- Find member by id, throw `EntityNotFoundException` if missing
- Set `status = MemberStatus.INACTIVE`
- Recompute checksum and save

```
findAll()
```
- Return `memberRepository.findAllByOrderByFullNameAsc()`

```
search(String query)
```
- If query is blank, return `findAll()`
- Otherwise filter by fullName (case-insensitive contains), email, memberNumber, or parish
- Combine results, deduplicate by UUID, return sorted by fullName

```
findById(UUID id)
```
- Return member or throw `EntityNotFoundException`

```
getTransactionsByMember(UUID memberId)
```
- **Stub only** — return `Collections.emptyList()`
- This will be replaced in Phase 3

---

### Step 2.4 — i18n keys

Add the following keys to **all three** locale files. Translate to Dutch for `_nl`
and Arabic for `_ar`. English values are shown below.

```properties
# Navigation
members.nav=Members

# Screen titles and actions
members.title=Members
members.add=Add member
members.edit=Edit member
members.deactivate=Deactivate
members.search.placeholder=Search by name, email, member number or parish...
members.count=Total members: {0}

# Tabs (member detail)
members.tab.info=Info
members.tab.transactions=Transactions
members.transactions.empty=No transactions recorded for this member.
members.transactions.addFee=Add monthly fee

# Field labels
members.field.memberNumber=Member number
members.field.fullName=Full name
members.field.phoneNumber=Phone number
members.field.email=Email
members.field.address=Address
members.field.postalCode=Postal code
members.field.city=City
members.field.place=Place
members.field.parish=Parish
members.field.partnerName=Partner name
members.field.partnerPhone=Partner phone
members.field.partnerEmail=Partner email
members.field.child1=Child 1
members.field.child2=Child 2
members.field.child3=Child 3
members.field.child4=Child 4
members.field.child5=Child 5
members.field.applicationHolder=Application holder
members.field.signingDate=Signing date
members.field.iban=IBAN
members.field.paymentMethod=Payment method
members.field.status=Status
members.field.category=Category
members.field.householdGroup=Household group

# Enum display values
members.status.active=Active
members.status.inactive=Inactive
members.payment.directDebit=Direct debit
members.payment.transfer=Transfer
members.payment.cash=Cash
```

> Add **all** keys to all three files before building any FXML. A missing key at
> runtime will crash the screen on load.

---

### Step 2.5 — Member list screen

**Create `resources/fxml/members.fxml`**

Layout (top to bottom):
- `HBox` top bar:
  - `TextField` fx:id="searchField" — prompt text from `%members.search.placeholder`
  - `ComboBox` fx:id="statusFilter" — items: All, Active, Inactive
  - `ComboBox` fx:id="parishFilter" — populated dynamically from distinct parishes in DB
  - `Button` text=`%members.add` — opens member detail in add mode
- `TableView` fx:id="membersTable" — takes remaining vertical space:
  - Column: Member Nr (`memberNumber`)
  - Column: Full name (`fullName`)
  - Column: Parish (`parish`)
  - Column: City (`city`)
  - Column: Status (`status`) — display as badge/label
  - Column: Phone (`phoneNumber`)
- `HBox` bottom bar:
  - `Label` fx:id="countLabel` — text from `%members.count` with count substituted

All text via `%key` ResourceBundle references — no hardcoded strings.

**Create `controllers/MembersController.java`**

```java
@Component
public class MembersController implements Initializable {

    @Autowired MemberService memberService;

    @FXML TableView<Member> membersTable;
    @FXML TextField searchField;
    @FXML ComboBox<String> statusFilter;
    @FXML ComboBox<String> parishFilter;
    @FXML Label countLabel;

    private ObservableList<Member> allMembers;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // set up table columns
        // load all members into allMembers
        // wire searchField listener → applyFilters()
        // wire statusFilter + parishFilter → applyFilters()
        // double-click row → openDetail(member)
        // populate parishFilter from distinct parish values
    }

    private void applyFilters() { /* filter allMembers, update table and count */ }
    private void openDetail(Member member) { /* load member-detail.fxml */ }

    @FXML
    private void onAddMember() { /* open detail with null member (add mode) */ }
}
```

---

### Step 2.6 — Member detail screen

**Create `resources/fxml/member-detail.fxml`**

Root: `BorderPane` with a `TabPane` in the center.

**Tab 1 — Info** (label from `%members.tab.info`):

`ScrollPane` → `VBox` → sections as `TitledPane` or labeled `GridPane` groups:

- Personal: memberNumber (read-only TextField), fullName, phoneNumber, email
- Address: address, postalCode, city, place, parish
- Partner: partnerName, partnerPhone, partnerEmail
- Family: child1, child2, child3, child4, child5
- Membership: applicationHolder, signingDate (DatePicker), iban,
  paymentMethod (ComboBox), status (ComboBox), category, householdGroup

Bottom `HBox`: Save `Button` + Cancel `Button`

**Tab 2 — Transactions** (label from `%members.tab.transactions`):

- `HBox` header: member name label + total balance label (right-aligned)
- `TableView` fx:id="transactionsTable":
  - Column: Date
  - Column: Amount
  - Column: Description
  - Column: Type
- `Label` fx:id="emptyLabel" — text from `%members.transactions.empty`,
  visible only when table is empty
- `Button` text=`%members.transactions.addFee` — **disabled** (will be enabled in Phase 3)

**Create `controllers/MemberDetailController.java`**

```java
@Component
public class MemberDetailController implements Initializable {

    @Autowired MemberService memberService;

    @FXML TabPane tabPane;

    // Info tab fields (one per member field)
    @FXML TextField memberNumberField;
    @FXML TextField fullNameField;
    // ... all other fields

    // Transactions tab
    @FXML TableView<?> transactionsTable;
    @FXML Label emptyLabel;
    @FXML Button addFeeButton;

    private Member currentMember; // null = add mode

    public void setMember(Member member) {
        this.currentMember = member;
        if (member != null) populateForm(member);
        loadTransactions();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        addFeeButton.setDisable(true); // re-enabled in Phase 3
        // set up transactions table columns
    }

    private void populateForm(Member m) { /* fill all fields from member */ }
    private void loadTransactions() {
        if (currentMember == null) return;
        var txs = memberService.getTransactionsByMember(currentMember.getId());
        // populate transactionsTable
        emptyLabel.setVisible(txs.isEmpty());
    }

    @FXML private void onSave() { /* validate, build Member, call memberService.save() */ }
    @FXML private void onCancel() { /* close / navigate back */ }
}
```

---

### Step 2.7 — Navigation wiring

**Edit `resources/fxml/main.fxml`**
- Add a Members nav item to the sidebar after the Dashboard item
- Label text: `%members.nav`
- Icon: use whatever icon component pattern exists for other nav items

**Edit `controllers/MainController.java`**
- Add handler for the Members nav item
- Load `members.fxml` into the main content area using the same mechanism as Dashboard

---

## Hard constraints — do not violate these

1. **No hardcoded strings in FXML** — every user-visible label uses a `%key` ResourceBundle reference
2. **No Hibernate DDL** — `ddl-auto=none`. Schema is 100% owned by Flyway
3. **Do not modify V1** — only add `V2__create_members.sql`
4. **SQLite types** — all columns are TEXT or INTEGER. No TIMESTAMP, no UUID type. Dates stored as TEXT (ISO-8601)
5. **Checksum always recomputed in MemberService** before save — never in the entity
6. **memberNumber generated by service** — never by the database
7. **Transactions tab must be fully built in Step 2.6** (TableView, columns, empty state label, disabled button) even though data is a stub. Phase 3 only replaces the data call — no FXML changes allowed in Phase 3
8. **All i18n keys added to all three files** before any FXML is created

---

## Definition of done for Phase 2

- [ ] Application starts without errors after V2 migration runs
- [ ] Members nav item appears in sidebar and loads the list screen
- [ ] Add member form saves a new member with a generated memberNumber (e.g. "2026-001")
- [ ] Member list shows all members, search and filters work
- [ ] Double-clicking a member opens the detail screen with all fields populated
- [ ] Saving edits persists changes
- [ ] Deactivate sets status to INACTIVE and reflects in the list
- [ ] Transactions tab is visible with empty state message and disabled Add button
- [ ] All labels appear correctly in English, Dutch, and Arabic
- [ ] No hardcoded strings anywhere in FXML
