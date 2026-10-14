#!/usr/bin/env bash
# Links the QA agent team into Claude Code (global, user level) and creates the vault.
# Safe to re-run: it only (re)creates symlinks and never overwrites vault content.
set -euo pipefail

QA_HOME="$(cd "$(dirname "$0")" && pwd)"
QA_VAULT="${QA_VAULT:-$HOME/QA-Vaults/work}"

mkdir -p "$HOME/.claude/agents" "$HOME/.claude/skills" "$HOME/.claude/commands" "$HOME/.local/bin"

for f in "$QA_HOME"/commands/*.md; do
  ln -sfn "$f" "$HOME/.claude/commands/$(basename "$f")"
  echo "cmd    → /$(basename "$f" .md)"
done

for f in "$QA_HOME"/agents/*.md; do
  ln -sfn "$f" "$HOME/.claude/agents/$(basename "$f")"
  echo "agent  → $(basename "$f" .md)"
done

for d in "$QA_HOME"/skills/*/; do
  name="$(basename "$d")"
  target="$HOME/.claude/skills/$name"
  if [[ -e "$target" && ! -L "$target" ]]; then
    echo "skill  ! $name exists and is not ours — skipped"
    continue
  fi
  ln -sfn "${d%/}" "$target"
  echo "skill  → $name"
done

for b in "$QA_HOME"/bin/*; do
  chmod +x "$b"
  ln -sfn "$b" "$HOME/.local/bin/$(basename "$b")"
  echo "cli    → ~/.local/bin/$(basename "$b")"
done

if [[ -d "$HOME/.hermes/skills" ]]; then
  for d in "$QA_HOME"/hermes/skills/*/; do
    ln -sfn "${d%/}" "$HOME/.hermes/skills/$(basename "$d")"
    echo "hermes → skill $(basename "$d")"
  done
  mkdir -p "$HOME/.hermes/plugins"
  for d in "$QA_HOME"/hermes/plugins/*/; do
    ln -sfn "${d%/}" "$HOME/.hermes/plugins/$(basename "$d")"
    echo "hermes → plugin $(basename "$d") (enable once: hermes plugins enable $(basename "$d"))"
  done
fi

# Claude Code hooks for the Hermes live board (only active in `qa` sessions, QA_SESSION=1).
python3 - "$HOME/.local/bin/qa-board-hook" <<'PY'
import json, os, sys
cmd = sys.argv[1]
p = os.path.expanduser("~/.claude/settings.json")
s = json.load(open(p)) if os.path.exists(p) else {}
s.setdefault("env", {}).setdefault("QA_VAULT", os.environ.get("QA_VAULT", os.path.expanduser("~/QA-Vaults/work")))
hooks = s.setdefault("hooks", {})
for event, matcher in (("UserPromptSubmit", None), ("Stop", None), ("PreToolUse", "Agent|Task"), ("PostToolUse", "Agent|Task")):
    lst = hooks.setdefault(event, [])
    if not any(cmd in json.dumps(e) for e in lst):
        entry = {"hooks": [{"type": "command", "command": cmd, "timeout": 10}]}
        if matcher:
            entry["matcher"] = matcher
        lst.append(entry)
ask = s.setdefault("permissions", {}).setdefault("ask", [])
for r in ("Bash(git push:*)", "Bash(glab mr create:*)", "Bash(glab mr merge:*)", "Bash(gh pr create:*)", "Bash(gh pr merge:*)"):
    if r not in ask:
        ask.append(r)
json.dump(s, open(p, "w"), indent=2)
PY
echo "claude → hooks + publish approval rules in ~/.claude/settings.json"

# Weekday 08:00 Telegram digest (launchd). Rendered from the template with this machine's paths.
plist="$HOME/Library/LaunchAgents/com.qa-agent.digest.plist"
mkdir -p "$HOME/Library/LaunchAgents" "$HOME/.qa-agent/logs"
launchctl bootout "gui/$(id -u)/com.qa-agent.digest" 2>/dev/null || true
rm -f "$plist"
sed "s|__HOME__|$HOME|g" "$QA_HOME/launchd/com.qa-agent.digest.plist.template" > "$plist"
launchctl bootstrap "gui/$(id -u)" "$plist" 2>/dev/null && echo "launchd → digest weekdays 08:00" || echo "launchd → could not load $plist"

# qa-mobile MCP (Kotlin): build once if Java is available, register globally in Claude Code.
mcp_bin="$QA_HOME/mcp/qa-mobile/build/install/qa-mobile-mcp/bin/qa-mobile-mcp"
if [[ ! -x "$mcp_bin" ]] && command -v java >/dev/null; then
  echo "mcp    → building qa-mobile (first time takes a few minutes)…"
  (cd "$QA_HOME/mcp/qa-mobile" && ./gradlew -q installDist) || echo "mcp    ! build failed — run: cd mcp/qa-mobile && ./gradlew installDist"
fi
if [[ -x "$mcp_bin" ]] && command -v claude >/dev/null; then
  claude mcp get qa-mobile >/dev/null 2>&1 || claude mcp add -s user qa-mobile -- "$mcp_bin" >/dev/null
  echo "mcp    → qa-mobile registered (claude mcp get qa-mobile)"
fi

if [[ ! -d "$QA_VAULT" ]]; then
  mkdir -p "$QA_VAULT"
  cp -R "$QA_HOME/vault-template/." "$QA_VAULT/"
  echo "vault  → created $QA_VAULT"
else
  # Refresh templates only; project data is never touched.
  mkdir -p "$QA_VAULT/_system/templates"
  cp -R "$QA_HOME/vault-template/_system/templates/." "$QA_VAULT/_system/templates/"
  echo "vault  → templates refreshed in $QA_VAULT"
fi

case ":$PATH:" in *":$HOME/.local/bin:"*) ;; *)
  echo
  echo "Add to ~/.zshrc:  export PATH=\"\$HOME/.local/bin:\$PATH\"" ;;
esac
grep -q "QA_VAULT" "$HOME/.zshrc" 2>/dev/null || echo "Add to ~/.zshrc:  export QA_VAULT=\"$QA_VAULT\""
