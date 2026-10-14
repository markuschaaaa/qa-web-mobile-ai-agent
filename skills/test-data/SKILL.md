---
name: test-data
description: Test data management — which data source to use (factory, curated static set, seeded fixture, existing account), isolation and cleanup, seeding via API instead of UI, environment-specific accounts without secrets in code or vault, synthetic realistic data (German formats, IBAN/postcodes/phones that are clearly test data), and no real personal data. Use when a test needs data, when tests interfere with each other, when designing data for test cases, or when preparing a test environment.
---

# Test data

## 1. Pick the source

| Need | Use |
|---|---|
| Valid "any" data for a happy path | **factory** (Faker / builder) generating fresh unique values per run |
| Curated edge cases (boundaries, invalid formats, umlauts) | **static set** in code (typed constants), referenced from test cases |
| Data that must exist before the test (user with role X, object in state Y) | **seed via API / DB / fixture** in setup — never by clicking through the UI |
| Shared reference data (tenants, master data, DSOs, catalogues) | read-only; never modify in tests |
| Accounts | per environment, stored in env/config/secret manager; vault and PROJECT.md only name **where** they are |

## 2. Isolation & cleanup

- Unique per run: suffix with run id / timestamp / worker index (`order-<run>-<n>`), so parallel and repeated runs don't collide.
- Whoever creates data deletes it (`afterEach`/`afterAll`, or a cleanup job by prefix). Tests that mutate shared state are tagged and restore it.
- Ordered stateful scenarios: the scenario owns its data for its whole chain; reset at the start, not only at the end.
- Never depend on data another test created, unless it is the same ordered scenario.

## 3. Realistic synthetic data

- Never real customers, employees or production copies. If prod-like data is unavoidable, it must be anonymised by the data owner — not by the agent.
- German formats when `doc_language: de` or the market is DE: dates `TT.MM.JJJJ`, decimal comma, PLZ 5 digits, umlauts and ß in names/streets (`Jürgen Müller-Weiß`, `Straße`), long compound words for length limits.
- Use values that are obviously test data: e-mail domains `example.com`/`example.org` (reserved, RFC 2606), names with a `Test` marker when the system allows, IBAN from bank documentation examples with a valid checksum (e.g. `DE89 3704 0044 0532 0130 00`), phone numbers marked as fictional.
- Faker locales: `@faker-js/faker` → `fakerDE`; Kotlin/Java → `net.datafaker:datafaker` with `Locale.GERMANY`.

## 4. Document

In the test case: preconditions name the data (role, state, tenant) — not passwords. In PROJECT.md: where accounts live, how to reset an environment, which data is shared/read-only. Missing or broken test data on an environment is an **environment** finding for triage, not a test failure.
