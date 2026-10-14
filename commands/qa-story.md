---
description: Story end-to-end — business rules & questions → test cases (+ Xray) → automation plan, with your approval before code
argument-hint: <JIRA-KEY> <story + AC text, or path to a file>
---
Run the full story pipeline for: $ARGUMENTS

Follow qa-core. Steps (delegate each to the named subagent; pass project, input paths and expected output paths):
1. **business-analyst** — create/update `stories/<KEY>.md` (story, AC, related rules from `domain/`, impacted areas, risks). Every gap or ambiguity → `> [!question]`.
2. **test-designer** — AC review, test model (technique tables), test cases in `testcases/<feature>/`, coverage AC → TC, Xray export (`xray-export`, project mapping from PROJECT.md).
3. If test cases need screens that are not in `app-map/` → **explorer** maps them (dev/qa only).
4. Stop and show me: number of TCs per priority, automation candidates, open questions, export path, confidence.
5. Only after my OK: **automator** implements the P1 candidates on a local `qa-agent/<KEY>-<slug>` branch, runs them 3×; red → **triage**.
End with the qa-lead final report.
