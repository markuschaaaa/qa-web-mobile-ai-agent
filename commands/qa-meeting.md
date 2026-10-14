---
description: Process a meeting recording or transcript into decisions, business rules, action items and open questions
argument-hint: <path to .mp4/.m4a/.wav/.txt> [de|en|ru]
---
Delegate to **business-analyst**: process the meeting $ARGUMENTS

- Recording → transcribe locally with `qa-transcribe <file> <lang|auto> $P/meetings/raw/` (nothing leaves the machine).
- Write `meetings/YYYY-MM-DD-<topic>.md` (decisions, rules stated/changed, action items, open questions, timestamps from the `.srt`).
- Update `domain/` (rules, glossary, states) and affected `stories/`; flag contradictions with existing rules.
- Report: decisions, changed rules (with links), questions for me, which stories/test cases are now stale.
