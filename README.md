# qa-agent — a stack-agnostic AI QA team for Claude Code

A team of specialised AI roles that does the day-to-day work of a QA engineer — from understanding the business and writing test cases to exploring the live app, automating tests and triaging failures — on **any project and any stack**.

You give it a project once (repos, environments, test management, language). From then on you talk to one **QA Lead**, who delegates to the right role, keeps everything in a shared **Obsidian memory**, and asks you before anything leaves your machine.

```
You ──► QA Lead ──► Business Analyst · Test Strategist · Test Designer
                    Explorer · Automator · Triage
              │
              ├── Memory: Obsidian vault (one folder per project)
              ├── Tools:  Playwright MCP / playwright-cli · Appium MCP · adb · whisper.cpp
              └── Phone:  Claude app (control) · Telegram via Hermes (reports, notes, live board)
```

---

## What it can do

| Area | Examples |
|---|---|
| **Business understanding** | Transcribe meeting recordings locally (German/English/Russian), extract business rules, glossary, roles & permissions, state machines, decisions, contradictions |
| **Test strategy & planning** | Living test strategy, sprint/release test plans, risk matrix, regression scope, release readiness (go / no-go) |
| **Test design** | Review AC for gaps, design test cases with EP, BVA, decision tables, state transitions, pairwise; Xray CSV / Gherkin export |
| **Exploration** | Walk the web app (Playwright) or mobile app (Appium, Android & iOS) on dev/qa, record screens, flows, stable locators, exploratory findings |
| **Automation** | Write tests that follow the repo's own conventions, using only verified locators; run them until green (3× for flakiness); local branch + commit |
| **Triage** | Group failures by root cause, classify product bug / test defect / flaky / environment, draft bug reports, run summaries, flaky quarantine with ticket/owner/expiry |
| **Specialist testing** | API (contract-first REST/GraphQL), offline & bad-network mobile testing, accessibility (WCAG 2.2, EAA), test data strategy |
| **Mobile locators** | Own Kotlin MCP server ([`mcp/qa-mobile`](mcp/qa-mobile/README.md)): capture a screen, rank locators by stability, a11y findings, app-map notes, Kotlin page objects, build-to-build screen diff |
| **Phone** | Follow and steer sessions in the Claude app; Telegram digest every morning, `/note` into the project inbox, read-only status commands, live agent board |

---

## The team

| Role | Thinks like | Produces (in the vault) |
|---|---|---|
| **qa-lead** | QA lead / orchestrator | Plan, delegation, approval requests, short final report |
| **business-analyst** | BA who thinks like a tester | `meetings/`, `domain/` (rules, glossary, states), `stories/` |
| **test-strategist** | Test manager | `strategy/`, `plans/`, risk matrix, release readiness |
| **test-designer** | Senior manual QA | `testcases/`, coverage AC → TC, `testcases/export/` (Xray) |
| **explorer** | Exploratory tester / cartographer | `app-map/screens`, `app-map/flows`, locators, exploratory bug drafts |
| **automator** | Automation engineer | Test code in the project repo, `automation/coverage.md` |
| **triage** | Failure analyst | `runs/`, `runs/flaky.md`, `bugs/drafts/` |

Each role is a Claude Code subagent (`agents/*.md`). All roles share one constitution: [`skills/qa-core`](skills/qa-core/SKILL.md).

### Skills the roles load on demand

| Skill | Covers |
|---|---|
| `qa-core` | constitution: memory, approval gates, evidence, confidence gate, handoff, which skill when |
| `qa-onboard` | onboarding a project, stack detection, adapter generation |
| `test-design` | EP, BVA, decision tables, state transition, pairwise, AC review, minimisation |
| `xray-export` | Xray Manual CSV + Gherkin, project-specific mapping |
| `api-testing` | contract-first REST/GraphQL, coverage matrix, Playwright / REST Assured implementation |
| `mobile-network` | offline, sync, network switching, throttling — adb / emulator / Appium / iOS commands |
| `accessibility` | WCAG 2.2 AA, axe-core for web, mobile labels / touch targets / text scaling / dark mode |
| `test-data` | factories vs static sets vs seeding, isolation, cleanup, synthetic German data, no PII |
| `flaky-quarantine` | detection by reruns, root-cause taxonomy, quarantine contract, JUnit5 / Playwright mechanics |
| `stack-appium-kotlin` · `stack-playwright-ts` | stack adapters — how to work in a repo of that stack |

---

## How it works

### Three layers — why it works on any project

```
ROLE            how a test designer / automator / … works          agents/, never changes
STACK ADAPTER   how this framework works (Playwright TS, Kotlin     skills/stack-<id>/
                Appium, …) — generated from the repo if missing
PROJECT PROFILE this project: product, envs, repos, conventions,    vault/projects/<name>/PROJECT.md
                test management, language, policies
```

Changing job or stack = `qa new <project>` + onboarding. If the stack is new, onboarding writes a new adapter **from the conventions of the existing repo**. The roles stay the same. A repo's own `CLAUDE.md` / `AGENTS.md` / conventions always take precedence over the generic rules.

### Memory

Everything durable lives in an **Obsidian vault** (plain Markdown, linked notes). Sessions can be short; knowledge accumulates per project:

```
QA-Vaults/work/
  _system/templates/            PROJECT, story, test case, screen, bug report, meeting, test plan
  projects/<name>/
    PROJECT.md  domain/  meetings/  stories/  testcases/  app-map/
    strategy/   plans/   automation/  runs/  bugs/  decisions/  inbox/
  .active                       the project the agents work on
```

### Rules every role follows

- **Ask before publishing.** Free: read, explore dev/qa, write vault notes, write test code, run tests, local commits on `qa-agent/*` branches. **Needs your OK:** push, MR/PR, Jira/Xray/Confluence writes, messages, prod, deleting outside the vault. Push/MR is additionally enforced by Claude Code permission rules.
- **No invention.** Locators only from a live snapshot/page source; business rules only from AC, docs, meetings or observed behaviour, with source. Unknowns become `> [!question]` callouts instead of guesses.
- **Confidence gate.** Non-trivial plans state a 1–10 confidence and unknowns; below 5 the agent asks instead of producing.
- **Verify before "done".** Code compiles and the affected tests actually ran.
- **Data policy per project.** `llm_policy: claude-only` → project material only goes to Claude (the OmniRoute launcher refuses to run).
- **No AI traces in project artifacts** — no AI mentions or `Co-Authored-By` in client code, commits, MRs or tickets.

---

## Requirements

macOS (Apple Silicon tested). Everything except Claude Code is optional, depending on what you test.

| Component | Needed for | Install |
|---|---|---|
| **Claude Code** + Claude subscription (Pro / Max / Team / Enterprise) | everything | <https://claude.com/claude-code> |
| Node.js ≥ 20 | MCP servers, playwright-cli, Playwright projects | `brew install node` |
| tmux | sessions that survive closing the terminal | `brew install tmux` |
| Python 3 | installer, hooks (macOS system Python is fine) | — |
| **Obsidian** | reading/editing the memory | <https://obsidian.md> |
| Playwright MCP | web exploration | `claude mcp add -s user playwright -- npx -y @playwright/mcp@0.0.83` |
| playwright-cli | web exploration (CLI) | `npx -y @playwright/cli --version` (downloads on first use) |
| Java 17+ & Android SDK (`adb`, emulator) | Android | Android Studio |
| Appium 3 + drivers | mobile automation/exploration | `npm i -g appium` · `appium driver install uiautomator2` · `appium driver install xcuitest` |
| Appium MCP | mobile exploration | `claude mcp add -s user appium -- npx -y appium-mcp@1.95.8` |
| qa-mobile MCP (this repo) | locator ranking, screen notes, page objects | built and registered by `install.sh` (JDK 17+) — see [mcp/qa-mobile](mcp/qa-mobile/README.md) |
| Xcode (+ iOS simulator runtime) | iOS | App Store, then `sudo xcodebuild -license accept` · `xcodebuild -downloadPlatform iOS` |
| ffmpeg + whisper.cpp + model | local meeting transcription | `brew install ffmpeg whisper-cpp` + model below |
| Hermes Agent | Telegram: digest, notes, live board | <https://hermes-agent.nousresearch.com> |
| Ollama + `qwen3:14b` | local model for Hermes (nothing leaves the Mac) | `brew install ollama && brew services start ollama && ollama pull qwen3:14b` |
| OmniRoute | optional model fallback for non-client projects | <https://github.com/diegosouzapw/OmniRoute> |

Whisper model (~1.6 GB, multilingual):

```bash
mkdir -p ~/.local/share/whisper && curl -L -o ~/.local/share/whisper/ggml-large-v3-turbo.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo.bin
```

---

## Install

```bash
git clone <this repo> ~/qa-agent
cd ~/qa-agent
./install.sh
```

`install.sh` is idempotent (safe to re-run after every change). It:

- links the roles into `~/.claude/agents`, the skills into `~/.claude/skills` and the `/qa-*` commands into `~/.claude/commands` (global, every project);
- builds the `qa-mobile` MCP server (if Java is installed) and registers it with `claude mcp add -s user`;
- links the CLIs (`qa`, `qa-transcribe`, `qa-digest`, `qa-board-hook`) into `~/.local/bin`;
- adds Claude Code hooks (live board) and "ask before push/MR" permission rules to `~/.claude/settings.json`;
- creates the vault at `~/QA-Vaults/work` (or `$QA_VAULT`), refreshing templates on later runs — project data is never touched;
- links the Hermes skill/plugin (if Hermes is installed) and schedules the weekday 08:00 digest (launchd).

Then add to `~/.zshrc` and open a new terminal:

```bash
export QA_VAULT="$HOME/QA-Vaults/work"
export PATH="$HOME/.local/bin:$PATH"
```

Optional personal rules (name to use in documents, things never to mention) go in `~/.qa-agent/user.md` — local only, read by every role.

### Hermes (optional — Telegram)

```bash
hermes config set model.provider ollama
hermes config set model.base_url http://127.0.0.1:11434/v1
hermes config set model.default qwen3:14b
hermes config set model.ollama_num_ctx 65536
hermes config set kanban.dispatch_profiles '[]'      # the board only DISPLAYS work; Hermes never executes cards
hermes config set quick_commands.digest.type exec
hermes config set quick_commands.digest.command "$HOME/.local/bin/qa-digest --dry-run"
hermes config set quick_commands.qastatus.type exec
hermes config set quick_commands.qastatus.command "$HOME/.local/bin/qa status"
hermes plugins enable qa-notes
hermes gateway install                                # run as a background service
```

Connect Telegram in the Hermes desktop app (Messaging → Telegram → *Create with QR*), then send `/sethome` to your bot.
Use a local model or an API key with a spending limit for Hermes — not your Claude subscription login.

---

## Use

Full walkthrough: **[GETTING-STARTED.md](GETTING-STARTED.md)**.

### Connect a project (once)

```bash
qa new myproject
qa
```

Then tell the QA Lead:

```
Onboard this project.
Repos: ~/code/app-tests (Kotlin + Appium), ~/code/web-tests (Playwright TS)
Product: <one paragraph>. Environments: dev, qa — never prod. Devices: local emulator, Sauce Labs.
Test management: Jira + Xray, key PROJ, no API access — prepare files, I import.
Test accounts: in <local config file> — don't copy secrets. Documents in German.
Start with app-tests, then run ONE small existing test to verify the setup.
```

It reads the repo rules, detects the stack, asks only what it can't discover, fills `PROJECT.md` and runs one test.

### Everyday prompts

| Goal | Say |
|---|---|
| New story end to end | `PROJ-123: <story + AC>. Business analyst → rules + questions; test designer → test cases + Xray CSV; automator → automate the P1 cases, run 3x.` |
| Meeting | `Business analyst: process ~/Downloads/meeting.mp4 (German)` |
| Map the app | `Explorer: map login, main navigation and the 5 most used screens on the qa build` |
| Failed run | `Triage: analyse <path to test results / Allure>` |
| Plan / strategy | `Test plan for release 2.4 with these stories: …` · `Create the test strategy` |
| Phone notes | Telegram `/note …` → later `Process the inbox` |

### Slash commands (inside a `qa` / Claude Code session)

| Command | Does |
|---|---|
| `/qa-story <KEY> <AC or file>` | business rules + questions → test cases + Xray → stops for your OK → automation |
| `/qa-meeting <file> [de\|en\|ru]` | local transcription → decisions, rules, action items, questions |
| `/qa-explore <area> [web\|android\|ios]` | map screens, flows, locators into the app map |
| `/qa-automate <TC ids>` | automate following repo conventions, run 3×, update coverage |
| `/qa-triage [results path]` | group, classify, bug drafts, flaky list, phone-sized summary |
| `/qa-bug <observation>` | reproduce + evidence → bug draft (filed only after approval) |
| `/qa-plan <scope>` · `/qa-release <version>` | test plan · release readiness verdict |
| `/qa-inbox` · `/qa-status` | process the inbox · project status and next steps |

### Commands

| Command | |
|---|---|
| `qa` | Open the QA Lead (re-attaches if already running) |
| `qa "<task>"` | Open with a first task |
| `qa attach` · `qa resume` · `qa stop` | Back into the running session · continue the last conversation · end it |
| `qa new <name>` · `qa use <name>` · `qa status` | Create · switch · show project |
| `qa here` · `qa -p "<task>"` | Without tmux · headless single task |
| `qa omni` | Through OmniRoute (only projects with `llm_policy: any`) |
| `qa-transcribe <file> de [out]` | Local transcription → `.txt` + `.srt` |
| `qa-digest [--dry-run]` | Vault status digest → Telegram |

Sessions run in **tmux** — closing the terminal does not stop the work (`qa attach` brings it back). **Remote Control** is on, so each session also appears in the Claude app on your phone. The Mac stays awake while a session runs.

### Telegram (Hermes) — no AI involved, instant

`/digest` · `/questions` · `/inbox` · `/bugs` · `/runs` · `/note <text>` · `/projects` · `/use <name>` · `/qastatus`

Digest arrives automatically on weekdays at 08:00. Free text to the bot goes to the local model (slow; for personal use, not project work).

### Live agent board

Hermes dashboard → **Kanban → board `QA · <project>`** (`hermes dashboard`, `localhost:9119`, or the Hermes desktop app). Every request to the QA Lead and every delegation to a role appears as a card (*running* → *done*, with the role's result). Display only: Hermes' dispatcher is locked (`kanban.dispatch_profiles: []`) so it never executes these cards.

---

## Repository layout

```
agents/                 7 roles (Claude Code subagents)
commands/               /qa-* slash commands
skills/
  qa-core/              constitution: memory, approval gates, evidence, confidence, handoff
  qa-onboard/           project onboarding → PROJECT.md, stack detection, adapter generation
  test-design/          EP, BVA, decision tables, state transition, pairwise, AC review
  xray-export/          Xray CSV + Gherkin
  api-testing/          contract-first API testing
  mobile-network/       offline / network conditions for mobile
  accessibility/        WCAG 2.2 web + mobile a11y
  test-data/            data sources, isolation, synthetic data
  flaky-quarantine/     flaky detection, classification, quarantine
  stack-appium-kotlin/  adapter: Kotlin + Appium + JUnit5 (+ Allure, Sauce Labs)
  stack-playwright-ts/  adapter: Playwright + TypeScript
mcp/
  qa-mobile/            Kotlin MCP server: capture, rank locators, screen notes, page objects, diff
bin/
  qa                    launcher (tmux, Remote Control, projects, OmniRoute guard)
  qa-transcribe         whisper.cpp wrapper
  qa-digest             vault → Telegram digest (read-only, one-way)
  qa-board-hook         Claude Code hook → Hermes kanban (display only)
hermes/
  plugins/qa-notes/     /note /questions /bugs /runs /inbox /projects /use (no LLM, no shell)
  skills/qa-inbox/      best-effort note filing for free-text/voice messages
launchd/                weekday digest schedule (template)
vault-template/         Obsidian vault skeleton + note templates
install.sh              idempotent installer
GETTING-STARTED.md      day-to-day guide
```

---

## Extending

- **New stack** — let onboarding generate `skills/stack-<id>/SKILL.md` from a real repo, or write one by hand following the two existing adapters (discover → page objects → locators → tests → run → debug). Re-run `./install.sh`.
- **New role** — add `agents/<role>.md` with `skills: [qa-core]`, describe inputs/outputs in vault paths, add it to the team table in `agents/qa-lead.md`. Re-run `./install.sh`.
- **Project-specific behaviour** — put it in `PROJECT.md` → *Conventions*, not in the roles.

---

## Troubleshooting

| Symptom | Check |
|---|---|
| `qa: command not found` | new terminal; `~/.local/bin` in `PATH` |
| Agent says "no active project" | `qa status`, then `qa use <name>` or `qa new <name>` |
| Live board stays empty | `~/.qa-agent/board/hook.log`; session must be started with `qa` (sets `QA_SESSION=1`); select board `QA · <project>` in the dashboard; company-managed Claude settings may disable user hooks |
| Telegram commands don't answer | `hermes status`, `hermes gateway status`, `~/.hermes/logs/gateway.log` |
| No morning digest | `/sethome` sent to the bot? `~/.qa-agent/logs/digest.log` |
| Appium MCP "failed to connect" in `claude mcp list` | first start is slow — `claude mcp get appium` |
| `git`/`python3` refuse to run after installing Xcode | `sudo xcodebuild -license accept` |
| Hermes: context window below minimum | `hermes config set model.ollama_num_ctx 65536` |

---

## Credits

- Playwright adapter patterns (explore-before-generate, locator priority, confidence gate) adapted from [agentic-playwright](https://github.com/idavidov13/agentic-playwright) (MIT).
- Memory-bank idea inspired by RooFlow; Telegram/kanban via [Hermes Agent](https://github.com/NousResearch/hermes-agent) (Nous Research).
