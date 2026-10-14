---
description: Automate given test cases following the repo's conventions, run them 3x, update coverage
argument-hint: <test case ids or paths, e.g. PROJ-123-TC01..TC04>
---
Delegate to **automator**: automate $ARGUMENTS

Read PROJECT.md, the repo's own rules (CLAUDE.md / AGENTS.md / conventions) and the stack adapter first; reuse existing page objects.
Locators only from `app-map/` or a fresh exploration. Local branch `qa-agent/<KEY>-<slug>`, run affected tests 3×, update `automation/coverage.md`, local commit.
Red → **triage** classifies. Report: files changed, run results (pass/fail per run), anything blocked. Push/MR only after my OK.
