# qa-mobile — MCP server for mobile test automation (Kotlin)

An [MCP](https://modelcontextprotocol.io) server that gives AI agents QA-specific tools for **Android and iOS page sources**: capture a screen, rank locators by stability, write an app-map screen note, generate a Kotlin page object, and diff two builds of a screen.

It **complements** the [Appium MCP](https://github.com/appium/appium-mcp) (which drives the device): Appium MCP taps and types, qa-mobile turns what's on screen into stable locators and documentation.

Built with the official [Kotlin MCP SDK](https://github.com/modelcontextprotocol/kotlin-sdk) 0.15.0 · Kotlin 2.4 · JVM 17+ · stdio transport.

## Tools

| Tool | What it does |
|---|---|
| `capture_android` | Page source XML + screenshot + density + focused activity via `adb` — **no Appium session needed**. Saves files, returns paths and a summary. |
| `appium_source` | Page source of an existing Appium session (iOS or Android) over the Appium HTTP API. |
| `rank_locators` | Every interactive (and text) element with locator candidates ranked by stability, plus warnings. |
| `screen_to_markdown` | App-map screen note (element table: best + fallback locator, stability, findings); optionally writes the `.md`. |
| `generate_page_object` | Kotlin page object with `AppiumBy` locators (`val x: By` or `fun x(driver): WebElement`); unstable elements left out by default. |
| `diff_screens` | Old vs new page source: removed (tests will break), changed (locator / label / text / type), added. |

All tools take the page source inline (`source`) or as a file (`source_path`).

### Ranking

| Score | Android (UiAutomator2) | iOS (XCUITest) |
|---|---|---|
| 95 | `accessibility id` (content-desc) | `accessibility id` (name) |
| 90 | `id` (resource-id) | — |
| 75 / 70 | — | `-ios predicate string` / `-ios class chain` on type + label |
| 60 | `-android uiautomator` text | — |
| 15 | absolute `xpath` | absolute `xpath` |

Penalties: not unique on the screen (−50), value looks dynamic — UUID, long number, date, time (−35), generated resource id like `id/0x7f0a…` (−30). **Stable = score ≥ 70.**

Warnings: interactive element without accessibility label · content-desc equal to the resource id (screen readers read an id) · touch target below 48 dp (Android, needs density) / 44 pt (iOS) · no stable locator → ask devs for an accessibility id / `testID`.

Example (`rank_locators` on the bundled Android fixture):

```
platform: android · elements: 9 · stable: 4 · unstable: 5

emailField [input, EditText] "E-Mail"
   95  accessibility id: E-Mail
   90  id: com.example.demoshop:id/email
   15  xpath: /hierarchy[1]/node[1]/node[2]  — absolute xpath — breaks on any layout change

hilfeButton [button, Button] "Hilfe"
   15  xpath: /hierarchy[1]/node[1]/node[8]  — absolute xpath — breaks on any layout change
   10  -android uiautomator: new UiSelector().text("Hilfe")  — visible text …; not unique (2 matches)
  ⚠ no stable locator — ask devs for an accessibility id / testID

loginButton [button, Button] "login_button"
   95  accessibility id: login_button
   90  id: com.example.demoshop:id/login_button
  ⚠ a11y: content-desc equals the resource id — screen readers will read an id
```

## Build & register

```bash
cd mcp/qa-mobile
./gradlew test installDist
claude mcp add -s user qa-mobile -- "$PWD/build/install/qa-mobile-mcp/bin/qa-mobile-mcp"
```

(`../../install.sh` does both automatically when Java is installed.) Requirements: JDK 17+, `adb` on `PATH` for `capture_android` (or `ADB=/path/to/adb`).

Try it on a device or emulator **that no Appium session is using** (`uiautomator dump` can disturb a running UiAutomator2 session):

```
Use qa-mobile: capture_android, then screen_to_markdown with screen_id "login", title "Login".
```

## Design notes

- **stdout is the protocol.** Logging goes to stderr (slf4j-simple, warnings only); the kotlin-logging startup banner is disabled (`-Dkotlin-logging.logStartupMessage=false`).
- **Safe XML parsing:** DOCTYPE and external entities are rejected (XXE). Trailing text from `uiautomator dump` is tolerated.
- **No shell:** `adb` is called with an argument list; device serial and session id are validated.
- Naming: page-object property names come from developer ids (`email` → `emailField`), not localised labels; umlauts are transliterated.
- Page objects target plain Appium java-client. Frameworks with their own element wrapper (e.g. cross-platform `Element.create(android = …, ios = …)`) should map from the `by_fields` output.

## Tests

`./gradlew test` — 13 tests on synthetic Android/iOS fixtures (`src/test/resources`): ranking, uniqueness, dynamic values, a11y warnings, iOS fallbacks, invisible elements, markdown format, page object output, diff, XXE rejection, tool registration.
