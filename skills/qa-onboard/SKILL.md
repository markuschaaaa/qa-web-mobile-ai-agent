---
name: qa-onboard
description: Onboard the QA agent onto a new or existing project — create the project folder in the Obsidian vault, interview the user, scan the test/app repositories to detect the stack and conventions, pick or generate a stack adapter, and fill PROJECT.md. Use when the user says "new project", "onboard", "set up project X", "I started a new job", when PROJECT.md is missing, or when the stack changed.
---

# Project onboarding

Goal: a complete `$P/PROJECT.md` so every role can work without asking the same questions again.

## Steps

1. **Create** — if `$QA_VAULT/projects/<name>/` does not exist, run `qa new <name>` (copies the vault template, sets `.active`). Otherwise `qa use <name>`.
2. **Interview** — ask only what you cannot discover. Batch questions, max ~8 per round:
   - Product in one paragraph; who the users are; what money/risk flows through it.
   - Platforms (web / Android / iOS / API), environments + URLs, build sources (APK/IPA paths, TestFlight, app-center).
   - Test repo path(s) and app repo path(s) on disk.
   - Test management (Xray? project key, test types used), tracker, docs location.
   - Test accounts: **where they are stored** (env file path / password manager entry name) — never paste secrets into the vault.
   - `doc_language`, `llm_policy`, CI system, device setup (local emulator / real device / Sauce Labs).
3. **Scan repos** (read-only) — read the repo's own AI/dev docs first (`CLAUDE.md`, `AGENTS.md`, `CODING_RULES.md`, `.claude/commands/`, README). Summarise them into PROJECT.md "Conventions" with a link — don't copy them wholesale. Then detect:
   - Language/build: `package.json`, `build.gradle(.kts)`, `pom.xml`, `pyproject.toml`.
   - Framework: `@playwright/test`, `io.appium:java-client`, `junit-jupiter`, `cucumber`, `selenide`, `webdriverio`, `cypress`.
   - Conventions: folder layout, page-object style, locator strategy, base classes, fixtures, test data, tagging, reporting (Allure?), CI files.
   - Record 2–3 representative example files as "follow these" references.
4. **Stack adapter** — look in `~/.claude/skills/` for `stack-<id>` (e.g. `stack-playwright-ts`, `stack-appium-kotlin`).
   - Found → reference it in PROJECT.md, note project-specific deviations under "Conventions".
   - Not found → generate `~/qa-agent/skills/stack-<id>/SKILL.md` from what the repo actually does (structure, how to add a page object, how to add a test, how to run one test, how to read results). Run `~/qa-agent/install.sh` so it gets linked. Tell the user a new adapter was created.
5. **Verify run** — run the smallest existing test (or the project's smoke command) once. Record the exact command and result in PROJECT.md.
6. **Write PROJECT.md** using `$QA_VAULT/_system/templates/PROJECT.md`. Leave unknown fields as `> [!question]` — not blank, not guessed.
7. **Handoff** — suggest: explorer → map the app; business-analyst → process `inbox/`.
