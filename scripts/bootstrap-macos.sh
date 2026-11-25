#!/usr/bin/env bash
# bootstrap-macos.sh — install everything qa-agent needs on a fresh Mac. Safe to re-run.
#
#   ./scripts/bootstrap-macos.sh            interactive (asks for optional parts)
#   ./scripts/bootstrap-macos.sh --all      install all optional parts without asking
#
# Installs only what is missing. On macOS versions Homebrew no longer ships prebuilt packages for,
# it does not start long source builds silently — it prints a prebuilt alternative instead.
# Things that need your password, accounts or a GUI are printed as next steps.
set -euo pipefail

ALL=0; [[ "${1:-}" == "--all" ]] && ALL=1
QA_HOME="$(cd "$(dirname "$0")/.." && pwd)"

say()  { printf '\n\033[1m▶ %s\033[0m\n' "$*"; }
ok()   { printf '  ✓ %s\n' "$*"; }
skip() { printf '  – %s\n' "$*"; }
todo() { TODO+=("$*"); }
ask()  { (( ALL )) && return 0; read -r -p "  $1 [y/N] " a; [[ "$a" =~ ^[Yy] ]]; }
has()  { command -v "$1" >/dev/null 2>&1; }
TODO=()

[[ "$(uname)" == "Darwin" ]] || { echo "This script is for macOS. On Windows use WSL2 (see docs/NEW-MACHINE.md)."; exit 1; }

MACOS="$(sw_vers -productVersion)"
MACOS_MAJOR="${MACOS%%.*}"
# Homebrew only ships prebuilt bottles for recent macOS versions; older ones compile from source.
OLD_MACOS=0; (( MACOS_MAJOR < 15 )) && OLD_MACOS=1

say "macOS $MACOS · Homebrew"
if ! has brew; then
  echo "  Homebrew is missing. Install it first (needs your password), then re-run this script:"
  echo '  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"'
  exit 1
fi
ok "$(brew --version | head -1)"
(( OLD_MACOS )) && echo "  ⚠ macOS $MACOS_MAJOR: Homebrew builds formulae from source here (slow). Missing tools get prebuilt alternatives instead."

# formula <command> <brew formula> <prebuilt alternative> [small]
# Installs a missing tool with brew on supported macOS; on old macOS only small builds, after asking.
formula() {
  local cmd=$1 f=$2 alt=$3 small=${4:-}
  if has "$cmd"; then ok "$cmd already installed"; return; fi
  if (( OLD_MACOS )); then
    if [[ -n "$small" ]] && ask "$cmd is missing — build it from source with Homebrew (a few minutes)?"; then
      brew install "$f"
    else
      todo "$cmd is missing — install: $alt"; skip "$cmd missing (see next steps)"
    fi
  else
    brew install "$f"
  fi
}

say "Core tools"
formula git     git     "xcode-select --install   (Apple Command Line Tools include git)"
formula python3 python  "xcode-select --install   (Apple Command Line Tools include python3)"
formula node    node    "the LTS macOS installer from https://nodejs.org"
formula tmux    tmux    "brew install tmux — or skip it and start sessions with 'qa here'" small
if has java && java -version 2>&1 | grep -qE '"(1[7-9]|2[0-9])'; then
  ok "java $(java -version 2>&1 | head -1 | awk -F'"' '{print $2}')"
else
  brew list --cask temurin@21 >/dev/null 2>&1 || brew install --cask temurin@21 || todo "JDK 21: installer from https://adoptium.net (Temurin 21, macOS .pkg)"
fi

say "Claude Code"
if has claude; then ok "claude $(claude --version 2>/dev/null | head -1)"
else
  todo "Install Claude Code:  curl -fsSL https://claude.ai/install.sh | bash   — then in a new terminal run 'claude', /login, /exit, and re-run this script"
fi

say "Web testing (Playwright MCP + playwright-cli)"
if has node; then
  npx -y @playwright/cli --version >/dev/null 2>&1 && ok "playwright-cli available" || todo "playwright-cli: npx -y @playwright/cli --version"
  if has claude; then
    claude mcp get playwright >/dev/null 2>&1 || claude mcp add -s user playwright -- npx -y @playwright/mcp@0.0.83 >/dev/null
    ok "Playwright MCP registered"
  else
    skip "Playwright MCP: after Claude login, re-run this script"
  fi
else
  skip "needs node — re-run this script after installing node"
fi

if ask "Mobile testing (Android Studio, Appium + drivers, Appium MCP)?"; then
  say "Mobile"
  brew list --cask android-studio >/dev/null 2>&1 || [[ -d "/Applications/Android Studio.app" ]] || brew install --cask android-studio \
    || todo "Android Studio: download from https://developer.android.com/studio"
  if has node; then
    has appium || npm install -g appium
    appium driver list --installed 2>&1 | grep -q uiautomator2 || appium driver install uiautomator2
    ok "Appium $(appium -v) + uiautomator2"
    if [[ -d /Applications/Xcode.app ]]; then
      appium driver list --installed 2>&1 | grep -q xcuitest || appium driver install xcuitest
      ok "xcuitest driver"
    else
      todo "iOS: install Xcode from the App Store, then: sudo xcodebuild -license accept · xcodebuild -downloadPlatform iOS · appium driver install xcuitest"
    fi
    if has claude; then
      claude mcp get appium >/dev/null 2>&1 || claude mcp add -s user appium -- npx -y appium-mcp@1.95.8 >/dev/null
      ok "Appium MCP registered"
    fi
  else
    skip "Appium needs node — re-run this script after installing node"
  fi
  todo "Android Studio: open it once → SDK Manager (platform-tools, emulator, a system image) → Device Manager: create an emulator. Add to ~/.zshrc: export ANDROID_HOME=\"\$HOME/Library/Android/sdk\"; export PATH=\"\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/emulator:\$PATH\""
fi

if ask "Local meeting transcription (ffmpeg, whisper.cpp + 1.6 GB multilingual model)?"; then
  say "Whisper"
  formula ffmpeg      ffmpeg      "static build from https://evermeet.cx/ffmpeg/ → put 'ffmpeg' into ~/.local/bin"
  formula whisper-cli whisper-cpp "build whisper.cpp: https://github.com/ggml-org/whisper.cpp (make) → put 'whisper-cli' into ~/.local/bin" small
  model="$HOME/.local/share/whisper/ggml-large-v3-turbo.bin"
  if [[ ! -s "$model" ]]; then
    mkdir -p "$(dirname "$model")"
    curl -fL --progress-bar -o "$model" https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo.bin
  fi
  ok "whisper model"
fi

if ask "Obsidian (to read the vault)?"; then
  [[ -d /Applications/Obsidian.app ]] || brew install --cask obsidian || todo "Obsidian: download from https://obsidian.md"
  ok "Obsidian"
fi

say "qa-agent"
if has python3; then
  "$QA_HOME/install.sh"
else
  todo "Re-run this script after python3 is installed (install.sh needs it)"
fi
grep -q 'QA_VAULT' "$HOME/.zshrc" 2>/dev/null || {
  printf '\n# qa-agent\nexport QA_VAULT="$HOME/QA-Vaults/work"\nexport PATH="$HOME/.local/bin:$PATH"\n' >> "$HOME/.zshrc"
  ok "QA_VAULT + PATH added to ~/.zshrc"
}
[[ -f "$HOME/.qa-agent/user.md" ]] || todo "Personal rules (optional): create ~/.qa-agent/user.md — name to use in documents, things never to mention"

todo "Telegram/Hermes (optional): install Hermes, create a NEW bot for this machine (one bot can't run on two machines), then the 'hermes config set …' block from README → Hermes"
todo "Open a NEW terminal, then: qa new <project>  →  qa  →  'Onboard this project …'"

say "Done. Next steps:"
for t in "${TODO[@]}"; do printf '  • %s\n' "$t"; done
