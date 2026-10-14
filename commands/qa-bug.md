---
description: Turn an observation into a complete bug report draft (reproduce, evidence, severity) — not filed until you approve
argument-hint: <what you saw, where, which build/device>
---
Delegate to **triage** (with **explorer** if reproduction on the app is needed): bug draft for: $ARGUMENTS

Reproduce on dev/qa if possible, capture evidence (screenshot, page source, logs), find the expected behaviour's source (AC / rule / meeting), check `bugs/` for duplicates.
Write `bugs/drafts/<date>-<slug>.md` using the bug-report template in `doc_language`. Filing in Jira = approval gate: show me the final text first.
