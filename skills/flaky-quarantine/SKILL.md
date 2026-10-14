---
name: flaky-quarantine
description: Detect, classify, quarantine and fix flaky tests — rerun-based detection, root-cause taxonomy (timing, state leakage, ordering, data, environment, locator, product race), the quarantine contract (ticket, owner, expiry), how to quarantine in JUnit5/Gradle and Playwright, and the runs/flaky.md tracking format. Use when a test passes on rerun, when CI is red but local is green, when asked "is this flaky", or when cleaning up an unstable suite.
---

# Flaky tests

## 1. Prove it's flaky

- Same commit, same environment, reruns: Playwright `npx playwright test <file> -g "<title>" --repeat-each=10 --workers=1` (then with the normal worker count); Gradle `./gradlew test --tests "<fqcn>.<method>" --rerun` in a loop, or the `org.gradle.test-retry` plugin to collect pass-after-retry signals in CI.
- Record pass/fail counts. 10/10 red = not flaky (it's broken). Mixed = flaky.
- Compare a passing and a failing run: trace / screenshot / page source / logs at the failing step.

## 2. Classify the root cause

| Cause | Typical signal | Fix direction |
|---|---|---|
| Timing / wait | element not ready, animation, spinner | wait for a state (web-first assertion, explicit wait on condition); never longer sleeps |
| State leakage | fails only after another test | own setup/cleanup; reset app/device state; isolate accounts |
| Order dependence | fails when run alone or in another order | make the dependency explicit (ordered scenario) or remove it |
| Test data | unique constraint, data consumed by a parallel run | unique per-run data, factories, cleanup (see `test-data`) |
| Environment | backend slow/down, device farm hiccup, expired account | health check before suite; classify as env, not test |
| Locator | matches 2 elements sometimes, dynamic ids | stable locator from `app-map/` (accessibility id / role+name) |
| Product race | real concurrency bug in the app | **not flaky — a bug**: draft it, keep the test |
| Parallelism | passes with 1 worker only | shared resources; per-worker data |

## 3. Quarantine contract

A quarantined test still runs (separate job) but does not block the pipeline. Allowed only with: a ticket/link, an owner, a classified cause, and an expiry date (default 2 sprints). Never quarantine a product bug or a test that is 100 % red.

| Stack | How |
|---|---|
| JUnit5 + Gradle | `@Tag("quarantine")` on the method; main task `useJUnitPlatform { excludeTags("quarantine") }`; separate task with `includeTags("quarantine")` |
| Playwright | tag `@quarantine` in the title or `{ tag: '@quarantine' }`; main run `--grep-invert @quarantine`; quarantine run `--grep @quarantine` |

If the repo already has its own mechanism (`@Disabled("…")` + ticket, custom annotation), use that instead.

## 4. Track in `runs/flaky.md`

```
| Test | Cause | Evidence | Runs (pass/total) | Quarantined | Ticket | Owner | Expires | Status |
|---|---|---|---|---|---|---|---|---|
```
Update counts on every triage. Expired entries → fix or delete the test, decided with the user. Triage summary always reports: new flaky, fixed flaky, overdue quarantines.
