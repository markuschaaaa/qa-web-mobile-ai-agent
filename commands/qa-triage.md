---
description: Analyse a failed test run — group by root cause, classify bug/test/flaky/env, draft bugs and a run summary
argument-hint: [path to test results / Allure / CI log] (default: from PROJECT.md)
---
Delegate to **triage**: analyse $ARGUMENTS

Group failures by root signature, classify (product bug with AC/rule source · test defect · flaky · environment), rerun suspicious ones once, check `bugs/` for duplicates.
Write `runs/<date>-<suite>.md`, update `runs/flaky.md` (use the `flaky-quarantine` skill), bug drafts in `bugs/drafts/`.
Hand test defects to **automator** with a fix hint. End with an 8-line summary I can read on the phone.
