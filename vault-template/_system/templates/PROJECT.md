---
type: project-profile
project: <name>
updated: <YYYY-MM-DD>
---
# <Project name>

## Product
<One paragraph: what it is, who uses it, what money/risk flows through it.>

## Policies
- doc_language: de
- llm_policy: claude-only        # claude-only | any
- environments_allowed: [dev, qa] # prod is never touched

## Platforms & environments
| Platform | Env | URL / build source | Notes |
|---|---|---|---|
| Android | qa | <apk path / app-center / Sauce storage> | |
| Web | qa | <url> | |

## Devices
- Local emulator: <AVD name>
- Real devices: <model / OS>
- Cloud: Sauce Labs (<region>, credentials in <env file>)

## Test accounts
Stored in: <path to .env / password-manager entry name>. **Never copy secrets here.**

## Repositories
| Repo | Path on disk | Stack adapter |
|---|---|---|
| mobile tests | <path> | stack-appium-kotlin |
| web tests | <path> | stack-playwright-ts |

## How to run
- Single test: `<command>`
- Smoke: `<command>`
- Last verified: <date> — <result>

## Conventions (project-specific, override adapter defaults)
- <page object style, base classes, naming, tagging, test data, reporting>
- Reference files to imitate: <paths>

## Test management
- Tool: Jira + Xray, project key: <KEY>
- Test types used: Manual / Cucumber
- xray.priority_names: [Highest, High, Medium, Low]
- API access for agent: no   # yes → MCP/API configured

## CI
<GitLab CI / GitHub Actions — pipeline names, where reports land>

## Open questions
> [!question] …
