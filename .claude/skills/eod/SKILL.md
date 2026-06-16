---
name: eod
description: >
  Write an End-of-Day (EOD) report and save it as a Markdown file under docs/eod/.
  Use this skill whenever the user says "write my EOD", "create an EOD", "EOD report",
  "end of day", or describes what they did today and wants it documented.
  Always trigger this skill even if the user only gives a brief summary of their day.
---

# EOD Report Skill

Produces a structured End-of-Day Markdown report and saves it to `docs/eod/`.

---

## Naming Convention

Files are always saved as:

```
docs/eod/EOD-YYYY-MM-DD.md
```

Use today's actual date. Example: `docs/eod/EOD-2026-06-16.md`

---

## Steps

1. **Get the date** — use `date +%Y-%m-%d` via bash to get today's date reliably.
2. **Collect content** — use what the user provided. If their input is brief or bullet-style, expand it into clear, readable prose. Do not ask clarifying questions; work with what you have.
3. **Create the directory** — run `mkdir -p docs/eod` to ensure the path exists.
4. **Write the file** — save to `docs/eod/EOD-YYYY-MM-DD.md` using the exact date from step 1.
5. **Present the file** — call `present_files` so the user can download it.

---

## Output Format

```markdown
# EOD — {Day of week}, {Month} {Day}, {Year}

## What I worked on today

{A clear, well-written summary of the day's work. Use bullet points if there are
multiple distinct tasks. Expand terse notes into readable sentences.}
```

Keep it professional and concise. No filler, no padding.

---

## Example

User says: *"write my EOD — worked on the church admin project, set up the Maven scaffold and all the entities"*

Saved to: `docs/eod/EOD-2026-06-16.md`

```markdown
# EOD — Tuesday, June 16, 2026

## What I worked on today

- Set up the Maven project scaffold for the Church Admin desktop application.
- Configured the Spring Boot + JavaFX integration bridge and application entry point.
- Defined all core JPA entities: `Member`, `Transaction`, and `TransactionCategory`,
  each extending a shared `BaseEntity` with UUID identity, audit timestamps, and
  SHA-256 checksums.
- Created Spring Data JPA repositories and initial service layer stubs.
```
