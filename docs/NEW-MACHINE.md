# New machine / new project — from zero

The repo is the **brain** (roles, skills, commands, MCP) and is the only thing you carry over.
Everything project-specific — vault, PROJECT.md, accounts, tokens — is created fresh on the new machine.

## 0. Rules for a second job (read first)

| Keep separate | Why / how |
|---|---|
| **Vault** | New vault on the new machine. Never copy notes, test cases or recordings from another employer. |
| **Claude account** | Use the account this employer provides or allows. Check their AI policy: may code/requirements go to Claude? Set `llm_policy` in PROJECT.md accordingly. |
| **Git identity** | Work repos: the employer's identity (`git config --global` on a work machine). The `qa-agent` repo: your personal identity (`git config user.email` inside `~/qa-agent` only). |
| **Telegram bot** | Create a **new bot** for this machine — one bot token can't be polled by two Hermes instances. |
| **Secrets** | Test accounts, API keys, Sauce Labs keys stay in the project's env/config files — never in the vault or in `qa-agent`. |
| **Adapters you improve** | A new `stack-<id>` adapter may go back into `qa-agent` only if it is generic — no client names, URLs, keys, domain terms. Review the diff before every push. |

## 1. Install (macOS)

```bash
# 1. Homebrew (asks for your password)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 2. The brain
git clone https://github.com/<you>/qa-web-mobile-ai-agent.git ~/qa-agent

# 3. Everything else — asks before each optional part (mobile, whisper, Obsidian)
~/qa-agent/scripts/bootstrap-macos.sh
```

The script installs git, Node, tmux, Python, JDK 21, Playwright tooling, optionally Android Studio + Appium + drivers, ffmpeg + whisper.cpp + model, Obsidian; registers the MCP servers; runs `install.sh` (roles, skills, commands, hooks, vault, qa-mobile MCP build) and prints the remaining manual steps.

Manual steps it will list:

1. **Claude Code** — `curl -fsSL https://claude.ai/install.sh | bash`, then `claude` → `/login` with the right account. Re-run `bootstrap-macos.sh` afterwards so the MCP servers get registered.
2. **Android Studio** — open once, install SDK platform-tools + emulator + a system image, create an emulator; add `ANDROID_HOME` to `~/.zshrc`.
3. **iOS** (if needed) — Xcode from the App Store, `sudo xcodebuild -license accept`, `xcodebuild -downloadPlatform iOS`, `appium driver install xcuitest`.
4. **Personal rules** (optional) — `~/.qa-agent/user.md`: the name to use in documents, what never to mention.
5. **Hermes / Telegram** (optional) — install Hermes, new bot via *Create with QR*, `/sethome`, then the `hermes config set …` block from the README. No local model needed: `/note`, `/digest`, `/questions` etc. work without one.

Open a **new terminal** and check:

```bash
qa status                 # vault + no project yet
claude mcp list           # playwright, appium, qa-mobile
adb devices               # emulator/device (mobile only)
```

## 2. Start the project

```bash
qa new <project>          # e.g. qa new shop
qa
```

```
Onboard this project.
Repos: <paths on this machine>
Product: <one paragraph>. Environments: <dev/qa URLs or builds> — never prod.
Devices: <emulator / real devices / cloud>. Test management: <Jira+Xray / TestRail / Zephyr / …>, API access: <yes/no>.
Test accounts: in <file / password manager entry> — don't copy secrets. Documents in <language>.
llm_policy: <claude-only | any> (per company AI policy).
Read the repo rules first, detect the stack, run ONE small existing test to verify the setup. Ask me what you can't find out.
```

New stack (e.g. Java + Selenide, WebdriverIO, Cypress, pytest)? Onboarding writes `skills/stack-<id>/` from the existing repo's conventions. Review it; if it's generic, commit and push it to `qa-agent` so it's available everywhere.

After onboarding: `/qa-explore …` to map the app, `/qa-story …` for the first ticket, `/qa-status` any time.

## Windows

The launcher relies on macOS/Unix tools (tmux, launchd, caffeinate). On Windows use **WSL2 (Ubuntu)** with Claude Code inside WSL: roles, skills, commands, vault and `qa here` work there; the weekday digest needs cron instead of launchd, and Android/iOS device tooling is easier on the Windows side or a Mac. This path is not tested yet — expect adjustments.
