# Church Admin — Desktop Application

Offline-first church member and financial management application.  
Architecture: ADR-2026-001 | Version: 1.0 | Author: Samer Dahdal

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | JavaFX 21 + FXML + CSS |
| Framework | Spring Boot 3.2 |
| Database | SQLite (local file) |
| ORM | Spring Data JPA + Hibernate |
| Migrations | Flyway |
| Build | Maven |
| Export (Excel) | Apache POI |
| Export (PDF) | iText 8 |
| JSON | Jackson |
| Packaging | jpackage |

---

## Prerequisites

- Java 21+
- Maven 3.9+

No other infrastructure required — the app is fully self-contained.

---

## Run (development)

```bash
mvn javafx:run
```

## Build fat-jar

```bash
mvn clean package
java -jar target/church-admin-1.0.0.jar
```

---

## Data location

All data lives in `~/.churchadmin/`:

```
~/.churchadmin/
├── church_admin.db      # SQLite database
├── snapshots/           # Auto + manual backup snapshots
└── logs/                # Application logs
```

---

## Build Phases (ADR §11)

| Phase | Status | Deliverable |
|---|---|---|
| 1 — Foundation | ✅ Complete | Project scaffold, Spring+JavaFX bridge, BaseEntity, SQLite+Flyway, main window |
| 2 — Members | 🔲 Next | Member CRUD, search, household, categories |
| 3 — Transactions | 🔲 | General + member-linked transactions, balance ledger |
| 4 — i18n | 🔲 | EN/NL/AR + RTL layout |
| 5 — Import/Export | 🔲 | JSON export, smart merge import, conflict report |
| 6 — Snapshots | 🔲 | Auto/manual snapshots, restore, retention |
| 7 — Dashboard | 🔲 | Balance summary, recent transactions, active members |
| 8 — Reports | 🔲 | Monthly/yearly summaries, Excel + PDF export |
| 9 — Packaging | 🔲 | jpackage native installers |

---

## Architecture decisions

See `ADR-2026-001-Church-Admin-App.docx` for the full decision record.
