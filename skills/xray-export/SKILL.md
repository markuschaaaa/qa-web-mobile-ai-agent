---
name: xray-export
description: Format test cases for Jira Xray — Manual tests as CSV for the Xray Test Case Importer, and Cucumber/Gherkin tests as .feature files — including the column mapping note the user needs at import time. Use when the user asks for Xray, Xray CSV, test import, Jira test cases, or Gherkin/Cucumber scenarios for Xray.
---

# Xray export

**Project mapping first:** check PROJECT.md "Test management" — projects differ (e.g. one automated class = one Xray `Generic` test, functions = steps; or manual tests per TC). Follow it; the formats below are the defaults.

Source of truth stays in the vault (`$P/testcases/`). Exports are generated into `$P/testcases/export/` and never edited by hand.

## Manual tests → CSV (Xray Test Case Importer)

One row per **step**. The first row of a test carries the test-level fields; following rows of the same test repeat only `TCID` + step fields. `TCID` groups rows into one test (the importer's "Test Case Identifier" mapping).

Columns (UTF-8, comma separator, every field quoted):
```
"TCID","Summary","Test Type","Priority","Labels","Precondition","Description","Action","Data","Expected Result"
```
- `Test Type` = `Manual`.
- `Labels` space-separated, no spaces inside a label (e.g. `regression smoke offline`).
- Multi-line text: keep newlines inside the quoted field; escape `"` as `""`.
- `Priority` must match the Jira priority names used in the project (read PROJECT.md → `xray.priority_names`; ask if missing).

Always write a sibling `README-import.md` with: file name, row/test count, and the mapping to choose in the importer (TCID → Test Case Identifier, Summary → Summary, Action/Data/Expected Result → Manual Test Step fields, etc.). The user saves the mapping config in Xray once and reuses it.

## Cucumber tests → .feature

- One feature file per story/feature; `Feature:` = story title; tags `@<JIRA-KEY>` for the requirement link, plus `@P1` etc.
- If `doc_language: de`, add `# language: de` and use German keywords (`Funktionalität`, `Szenario`, `Angenommen`, `Wenn`, `Dann`, `Und`) **only if** the project's existing features do so — check first.
- `Scenario Outline` + `Examples` for partition/boundary tables.

## Rules

- Uploading/importing into Jira is a **publish action** → prepare files, then ask for approval (qa-core §2). Without API access the user imports the file manually.
- Report: N tests, M steps, file paths.
