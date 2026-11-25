#!/usr/bin/env bash
# bootstrap-macos.sh — install everything qa-agent needs on a fresh Mac. Safe to re-run.
#
#   ./scripts/bootstrap-macos.sh            interactive (asks for optional parts)
#   ./scripts/bootstrap-macos.sh --all      install all optional parts without asking
#
# Installs only well-known Homebrew formulae/casks and npm packages. Things that need your
# password, your accounts or a GUI (Homebrew itself, Xcode, Claude login, Android SDK setup,
# Telegram) are printed as next steps instead of being done silently.
set -euo pipefail

ALL=0; [[ "${1:-}" == "--all" ]] && ALL=1
QA_HOME="$(cd "$(dirname "$0")/.." && pwd)"

say()  { printf '\n\033[1m▶ %s\033[0m\n' "$*"; }
ok()   { printf '  ✓ %s\n' "$*"; }
todo() { TODO+=("$*"); }
ask()  { (( ALL )) && return 0; read -r -p "  $1 [y/N] " a; [[ "$a" =~ ^[Yy] ]]; }
TODO=()

[[ "$(uname)" == "Darwin" ]] || { echo "This script is for macOS. On Windows use WSL2 (see docs/NEW-MACHINE.md)."; exit 1; }

say "Homebrew"
if ! command -v brew >/dev/null; then
  echo "  Homebrew is missing. Install it first (needs your password), then re-run this script:"
  echo '  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"'
  exit 1
fi
ok "$(brew --version | head -1)"

say "Core tools (git, node, tmux, python, JDK 21)"
brew install git node tmux python@3.12 >/dev/null
brew list --cask temurin@21 >/dev/null 2>&1 || brew install --cask temurin@21
ok "git $(git --version | awk '{print $3}') · node $(node -v) · tmux $(tmux -V | awk '{print $2}') · java $(java -version 2>&1 | head -1 | awk -F'"' '{print $2}')"

say "Claude Code"
if command -v claude >/dev/null; then ok "claude $(claude --version 2>/dev/null | head -1)"
else
  todo "Install Claude Code:  curl -fsSL https://claude.ai/install.sh | bash   — then run 'claude' once and log in (/login)"
fi

say "Web testing (Playwright MCP + playwright-cli)"
npx -y @playwright/cli --version >/dev/null 2>&1 && ok "playwright-cli available" || todo "playwright-cli: npx -y @playwright/cli --version"
if command -v claude >/dev/null; then
  claude mcp get playwright >/dev/null 2>&1 || claude mcp add -s user playwright -- npx -y @playwright/mcp@0.0.83 >/dev/null
  ok "Playwright MCP registered"
else
  todo "After Claude login: claude mcp add -s user playwright -- npx -y @playwright/mcp@0.0.83"
fi

if ask "Mobile testing (Android Studio, Appium + drivers, Appium MCP)?"; then
  say "Mobile"
  brew list --cask android-studio >/dev/null 2>&1 || brew install --cask android-studio
  command -v appium >/dev/null || npm install -g appium
  appium driver list --installed 2>&1 | grep -q uiautomator2 || appium driver install uiautomator2
  ok "Appium $(appium -v) + uiautomator2"
  if [[ -d /Applications/Xcode.app ]]; then
    appium driver list --installed 2>&1 | grep -q xcuitest || appium driver install xcuitest
    ok "xcuitest driver"
  else
    todo "iOS: install Xcode from the App Store, then: sudo xcodebuild -license accept · xcodebuild -downloadPlatform iOS · appium driver install xcuitest"
  fi
  if command -v claude >/dev/null; then
    claude mcp get appium >/dev/null 2>&1 || claude mcp add -s user appium -- npx -y appium-mcp@1.95.8 >/dev/null
    ok "Appium MCP registered"
  fi
  todo "Android Studio: open it once → SDK Manager (platform-tools, emulator, a system image) → Device Manager: create an emulator. Add to ~/.zshrc: export ANDROID_HOME=\"\$HOME/Library/Android/sdk\"; export PATH=\"\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/emulator:\$PATH\""
fi

if ask "Local meeting transcription (ffmpeg, whisper.cpp + 1.6 GB multilingual model)?"; then
  say "Whisper"
  brew install ffmpeg whisper-cpp >/dev/null
  model="$HOME/.local/share/whisper/ggml-large-v3-turbo.bin"
  if [[ ! -s "$model" ]]; then
    mkdir -p "$(dirname "$model")"
    curl -fL --progress-bar -o "$model" https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo.bin
  fi
  ok "whisper-cli + model"
fi

if ask "Obsidian (to read the vault)?"; then
  brew list --cask obsidian >/dev/null 2>&1 || brew install --cask obsidian
  ok "Obsidian"
fi

say "qa-agent"
"$QA_HOME/install.sh"
grep -q 'QA_VAULT' "$HOME/.zshrc" 2>/dev/null || {
  printf '\n# qa-agent\nexport QA_VAULT="$HOME/QA-Vaults/work"\nexport PATH="$HOME/.local/bin:$PATH"\n' >> "$HOME/.zshrc"
  ok "QA_VAULT + PATH added to ~/.zshrc"
}
[[ -f "$HOME/.qa-agent/user.md" ]] || todo "Personal rules (optional): create ~/.qa-agent/user.md — name to use in documents, things never to mention"

todo "Telegram/Hermes (optional): install Hermes, create a NEW bot for this machine (one bot can't run on two machines), then the 'hermes config set …' block from README → Hermes"
todo "Open a NEW terminal, then: qa new <project>  →  qa  →  'Onboard this project …'"

say "Done. Next steps:"
for t in "${TODO[@]}"; do printf '  • %s\n' "$t"; done
