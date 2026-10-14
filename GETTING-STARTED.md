# QA agent team — how to use it

## The system in one picture

| Part | What it is | Where |
|---|---|---|
| **QA Lead + 6 roles** | Claude Code agents: business-analyst, test-strategist, test-designer, explorer, automator, triage | `~/qa-agent/agents` |
| **Memory** | Obsidian vault, one folder per project | `~/QA-Vaults/work/projects/<name>` |
| **`qa`** | terminal launcher (runs in tmux, Remote Control on) | `~/qa-agent/bin/qa` |
| **Hermes** | Telegram bot: reports, notes, read-only status (local Qwen, no client data to the cloud) | background service |
| **Tools** | Playwright MCP, Appium MCP, playwright-cli, adb, whisper.cpp | global |

**Rule of thumb:** *work* happens in Claude Code (terminal / Claude app). *Hermes* only informs you and collects notes.

## 1. On the Mac — the `qa` command

| Command | What it does |
|---|---|
| `qa` | Open the QA Lead. If a session is already running, re-attach to it. |
| `qa "task"` | Open with a first task. |
| `qa attach` | Back into the running session (after you closed the terminal). |
| `qa resume` | Continue the last conversation (after `qa stop` or a reboot). |
| `qa stop` | End the running session. |
| `qa status` | Active project, policy, session running or not. |
| `qa new <name>` / `qa use <name>` | Create / switch project. |
| `qa here` | Run in this terminal without tmux. |
| `qa-transcribe <file> de` | Local transcription (the business-analyst uses it automatically). |
| `qa-digest --dry-run` | Preview the Telegram digest. |

**Closing the terminal window does not stop the work** — the session lives in tmux. `qa attach` brings it back.
Detach on purpose without closing: `Ctrl-b` then `d`. The Mac stays awake while a session runs (caffeinate) — but a closed lid on battery still sleeps.

## 2. On the phone

**Claude app (control + live view):** every `qa` session has Remote Control on and appears as `qa-<project>` in the Claude app (Code). You can read along, answer questions, approve permission prompts and give new tasks.

**Telegram bot (Hermes) — commands, no AI involved, instant:**

| Command | Shows / does |
|---|---|
| `/digest` | Status summary (last run, bugs, questions, changes) |
| `/questions` | Open questions the agents collected |
| `/inbox` | Unprocessed notes |
| `/bugs` | Bug drafts waiting for your review |
| `/runs` | Latest test run summaries |
| `/note <text>` | Save a note to the project inbox (meeting info, AC change, idea) |
| `/projects`, `/use <name>` | List / switch project |
| `/qastatus` | Project + session state |

Automatic: digest every weekday 08:00.
Free text to the bot = chat with the local Qwen (slow, ~1 min; for personal things, not for project work).

## 3. A normal working day

1. **Morning** — digest arrives in Telegram. `qa attach` (or `qa`) on the Mac.
2. **New story** → paste it:
   `PROJ-123: <story + AC>. Business analyst → rules + questions; test designer → test cases + Xray CSV; automator → automate the P1 cases, run 3x.`
3. **Meeting** → `Business analyst: process ~/Downloads/<recording>.mp4 (German)`.
4. **During the day on the phone** → `/note …` for anything you hear; check `/questions`.
5. **Red pipeline** → `Triage: analyse <path to test-results / Allure>`.
6. **Before release** → `Test strategist: release readiness for <version>`.
7. **Evening** → `Process the inbox`. Review branches / bug drafts. Approve push / MR when asked.

## 4. Rules the agents follow (so you know what to expect)

- They **do on their own**: read, explore dev/qa, write vault notes, write test code, run tests, local commits on `qa-agent/*` branches.
- They **ask you first**: push, merge requests, anything in Jira/Xray/Confluence, messages, prod, deleting outside the vault.
- They **never invent** locators, business rules or endpoints — unknowns become `[!question]` (→ `/questions`).
- The repo's own `CLAUDE.md` / conventions always win over the generic rules.

## 5. New project / new job

```bash
qa new <name>
qa
```
→ `Onboard this project — repos: <paths>. What I know: <product, envs, devices, test management, language>.`
Different stack → onboarding writes a new `stack-<id>` adapter. Roles never change.

## 6. Tips

- Pro plan: one big task per session → `qa stop`, then `qa` fresh. Memory is in the vault, not the chat.
- Watch the vault live in Obsidian while agents work.
- Something broken: Hermes log `~/.hermes/logs/gateway.log`, digest log `~/.qa-agent/logs/digest.log`, `hermes status`, `qa status`.
