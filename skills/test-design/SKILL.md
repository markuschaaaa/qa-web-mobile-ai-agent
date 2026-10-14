---
name: test-design
description: Turn user stories, acceptance criteria and business rules into a minimal, high-coverage set of test cases using formal techniques — equivalence partitioning, boundary value analysis, decision tables, state transition, pairwise, error guessing, plus AC ambiguity review. Use when writing test cases, checklists, or test ideas from a ticket/US/AC, when asked "what should we test for X", or when reviewing AC quality.
---

# Test design

## 1. Review the AC first (static testing)

For each AC, check: testable? measurable? complete (error paths, empty states, permissions, limits)? consistent with `domain/` rules? Write every gap as `> [!question]` in the story file **before** designing tests. Ambiguity found here is the cheapest bug.

## 2. Model, then pick the technique

| What the requirement looks like | Technique | Output |
|---|---|---|
| Input field / parameter with ranges or formats | Equivalence partitioning + BVA | classes table, boundaries (min-1, min, max, max+1) |
| Several conditions → different outcomes | Decision table (collapse irrelevant columns) | one TC per rule column |
| Object with lifecycle (order, ticket, device sync, offline/online) | State transition | states/transitions table; cover all valid transitions + key invalid ones |
| Many independent config options (OS × locale × role × network) | Pairwise | combination table |
| Roles & permissions | Role × action matrix | allow/deny per cell |
| Everything | Error guessing / heuristics: empty, null, max length, special chars & umlauts (ä ö ü ß), double submit, back button, rotation, background/foreground, offline, slow network, timezone/DST, concurrency | extra negative TCs |

Write the model (table) into the story file — it justifies the TC set.

## 3. Write test cases

- One objective per TC. Title: `<Feature>: <behaviour> when <condition>`.
- Preconditions explicit (role, data, env, feature flags).
- Steps are user actions; expected results are observable outcomes (UI text, state, API response) — never "works correctly".
- Test data concrete and realistic (German formats when `doc_language: de`: dates `TT.MM.JJJJ`, decimal comma, PLZ, IBAN test values).
- Priority: P1 = money/safety/legal/data loss or main journey; P2 = important alternative paths; P3 = cosmetic/rare.
- Tag `automation: candidate | manual-only | automated` with a one-line reason.
- Traceability: every TC links to its AC or rule (`covers: [[US-123#AC2]]`). Every AC must be covered by ≥1 TC — produce the coverage table at the end.

## 4. Minimise

Merge TCs that exercise the same partition; prefer one boundary TC over three middle-value TCs. State the final count and why it is enough.
