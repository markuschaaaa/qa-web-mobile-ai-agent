---
name: stack-playwright-ts
description: Stack adapter for web (and API) automation with Playwright Test + TypeScript. Tells the automator/explorer how to discover a repo's Playwright conventions, explore the live app with playwright-cli or Playwright MCP, write page objects/fixtures/tests that match the repo, choose locators, run a single test, and debug with traces. Use when PROJECT.md names stack-playwright-ts, or a repo has @playwright/test in package.json.
---

# Stack adapter — Playwright + TypeScript

Generic defaults (patterns adapted from the agentic-playwright scaffold, MIT). **Precedence:** repo `CLAUDE.md` / `AGENTS.md` / `.claude/skills` → PROJECT.md "Conventions" → this file.

## 1. Discover (once per project → PROJECT.md)

```bash
cat package.json | grep -E '"(test|e2e|lint)[^"]*"|@playwright/test|zod|faker'
cat playwright.config.ts            # projects, baseURL, storageState, retries, trace/screenshot settings
ls pages tests fixtures 2>/dev/null; grep -rl "test.extend" --include=*.ts . | grep -v node_modules | head
```
Record: where POs live, how they reach tests (fixtures via `test.extend` vs `new Page(page)`), single import point for `test/expect`, auth (setup project + storageState), env/config source, tags, data factories, API layer.

## 2. Explore before generating (mandatory)

Locators come only from the live app: `playwright-cli open <url>` → `snapshot` → interact → `snapshot` again (or Playwright MCP `browser_navigate` / `browser_snapshot`). Save findings to `app-map/screens/`. Login problems or the app doesn't load → stop and report; never invent selectors.

## 3. Locators

`getByRole(role, { name })` > `getByLabel` > `getByPlaceholder` > `getByText` > `getByTestId` > CSS. Never XPath, never nth-child chains. Scope inside containers (`dialog.getByRole(...)`). Strict-mode violations mean the locator is ambiguous — narrow it, don't `.first()` it away.

## 4. Page objects & fixtures

- Repo style first. Default: class per page, locators as `get` accessors, actions as async verbs, no assertions inside except `expectLoaded()`-style helpers if the repo has them.
- Register POs as fixtures and import `test/expect` from the repo's fixture entry point if it has one.

## 5. Tests

- `test.describe` per feature, `test.step` for logical steps (always for multi-call API tests), tags as the repo does (`@smoke`, `{ tag: [...] }`).
- Web-first assertions only (`await expect(locator).toBeVisible()`); no `waitForTimeout`, no manual `isVisible()` polling.
- API: validate responses against a schema (Zod if the repo uses it); documented contract (OpenAPI) is the source of truth — a mismatch is a bug, don't loosen the schema.
- Data: factories for happy paths, curated static sets for negative cases; each test creates and cleans up its own data.

## 6. Run & debug

- Single test: `npx playwright test <file> -g "<title>" --project=<p>`; repeat for flakiness: `--repeat-each=3`.
- Debug: `--trace on` then `npx playwright show-trace test-results/**/trace.zip`; `--headed`, `--ui`, `PWDEBUG=1`.
- Failure taxonomy: Timeout (locator/wait/env) · strict-mode (ambiguous locator) · assertion diff (bug or data) · schema error (contract drift → bug) · network (env).
- Lint/format/typecheck per repo scripts (`eslint`, `prettier --check`, `tsc --noEmit`) before committing.
