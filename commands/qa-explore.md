---
description: Explore and map part of the live app (web or mobile) into the app map — screens, flows, locators, findings
argument-hint: <area / flow / screen> [web|android|ios]
---
Delegate to **explorer**: map $ARGUMENTS

Use the environment, build and devices from PROJECT.md (never prod; don't trigger payments, real messages or destructive actions).
Write `app-map/screens/*`, `app-map/flows/*`, update `app-map/index.md`; exploratory findings → `bugs/drafts/`.
If the `qa-mobile` MCP tools are available, use `rank_locators` / `screen_to_markdown` on captured page sources.
Report: screens mapped, locators (stable / unstable count), findings, what could not be reached and why.
