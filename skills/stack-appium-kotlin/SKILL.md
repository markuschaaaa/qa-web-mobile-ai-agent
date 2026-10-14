---
name: stack-appium-kotlin
description: Stack adapter for mobile UI automation with Kotlin + Appium (java-client, UiAutomator2 / XCUITest) + JUnit5 + Gradle, optionally Allure and Sauce Labs. Tells the automator/explorer how to discover a repo's mobile framework, write page objects and tests that match it, pick locators, run a single test, and debug failures. Use when PROJECT.md names stack-appium-kotlin, or when a repo has build.gradle(.kts) with io.appium:java-client.
---

# Stack adapter — Kotlin + Appium + JUnit5

Generic defaults. **Precedence:** repo `CLAUDE.md` / `AGENTS.md` / `CODING_RULES.md` / `.claude/commands` → PROJECT.md "Conventions" → this file. Read those first; if they disagree with this file, they win.

## 1. Discover the framework (once per project → PROJECT.md)

```bash
find . -name "build.gradle*" -not -path "*/build/*" | xargs grep -lE "io.appium|junit-jupiter|allure"
grep -rhoE "class \w+ ?: ?\w*Tests?\(\)" --include=*.kt . | sort | uniq -c | sort -rn | head   # base test class
grep -rlE "object \w+(Screen|Page)|class \w+(Screen|Page)" --include=*.kt . | head            # page objects
grep -rhoE "@(Tag|Order|Disabled|DisplayName|\w+Test|\w+Issues?)\b" --include=*.kt . | sort | uniq -c | sort -rn | head -20
```
Record: module with tests, base class, driver lifecycle owner (base class vs JUnit extension), element abstraction (raw `AppiumBy` vs a custom wrapper like `Element.create(android=…, ios=…)`), wait helper, test data location, config source (properties file / env / system props), stage/app-id switch, tags, Xray/Jira annotations, reporting.

**If the repo has its own element/wait/driver abstraction, always use it.** Never call `driver.findElement` directly in a repo that wraps it.

## 2. Page objects

- Follow the repo's form (Kotlin `object` singletons vs classes). One PO per screen; OS-only screens (system alerts, permission dialogs, pickers) in OS-specific packages if the repo has them.
- Elements defined once per platform at the PO level; actions are verbs (`tapSave()`, `enterPostcode(v)`); assertions as `assertIsPresent()` / `assertXyz()` if that is the repo style.
- No business logic or test data inside POs.

## 3. Locators (from `app-map/` or live page source only)

| Priority | Android (UiAutomator2) | iOS (XCUITest) |
|---|---|---|
| 1 | `AppiumBy.accessibilityId` (RN `accessibilityLabel` / `testID` → content-desc) | `AppiumBy.accessibilityId` (`testID` → name) |
| 2 | `AppiumBy.id("pkg:id/…")` | `-ios predicate string` (`name == 'x' AND type == 'XCUIElementTypeButton'`) |
| 3 | `-android uiautomator` (`new UiSelector().text("…")`, `textContains`) | `-ios class chain` |
| 4 | xpath — last resort, mark unstable in app-map | xpath — last resort |

React Native: ask devs for `testID`s on elements you can't reach stably; note it as a finding, don't hack around it with index-based xpath. Localised texts (German) are fragile locators — prefer ids.

## 4. Tests

- Respect the repo's model: if classes are **ordered stateful scenarios** (`@TestMethodOrder(OrderAnnotation)` + `@Order(n)`), add steps to that chain and keep it explicit; otherwise keep tests independent.
- Naming, tags, Jira/Xray annotations exactly as existing tests. If a script generates Xray issues/annotations, use it in dry-run mode and ask before `--apply` (publish gate).
- Waits: the repo's explicit-wait helper; never `Thread.sleep`.
- Mobile-specific cases to consider: permissions (first launch), keyboard covering fields, scroll-to-element, background/foreground, rotation, offline / network switch (`driver.setConnection` / `adb shell svc wifi|data`), app restart state, deep links.

## 5. Run

- Single test: `./gradlew :<module>:test --tests "<fqcn>.<method>" <-Dprops>` (exact command in PROJECT.md).
- Local device prerequisites: `adb devices` shows the emulator/device; Appium server reachable (`curl -s localhost:4723/status`) unless the framework starts it; correct APK/IPA path or app id in the local config file (never commit it).
- Formatting/lint the repo uses (`ktlintFormat`, `detekt`) before committing.
- Sauce Labs runs: only via the repo's config preset; credentials stay in env/config, never in code or vault.

## 6. Debug a failure

1. Read the JUnit/Gradle output (`build/test-results/test/*.xml`, `build/reports/tests/test/index.html`), Allure results if present.
2. Get evidence: screenshot + page source at failure (repo hook, or `adb exec-out screencap -p > shot.png`, `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml`), Appium server log, `adb logcat -d | tail -300` for crashes (`FATAL EXCEPTION`, `ReactNativeJS`).
3. Classify: locator changed (compare page source) / timing / state from previous ordered step / data / environment (MW down, login) / real bug → `triage`.
