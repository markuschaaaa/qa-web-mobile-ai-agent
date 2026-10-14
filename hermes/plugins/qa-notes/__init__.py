"""qa-notes — Telegram commands for the QA vault (via Hermes).

  /note <text>     file a note into the active project's inbox
  /questions       open questions ([!question]) in the active project
  /bugs            bug drafts awaiting review
  /runs            latest run summaries
  /inbox           unprocessed inbox items
  /projects        list projects (★ = active)
  /use <name>      switch the active project

Deliberately minimal: no LLM call, no subprocess. Reads the vault; the only writes are
new files in <vault>/projects/<active>/inbox/ (/note) and <vault>/.active (/use).
"""
from __future__ import annotations

import os
import re
from datetime import datetime
from pathlib import Path

VAULT = Path(os.environ.get("QA_VAULT", Path.home() / "QA-Vaults" / "work"))
MAX_CHARS = 20_000
NAME_RE = re.compile(r"[A-Za-z0-9._-]+")


def _projects() -> list[str]:
    d = VAULT / "projects"
    return sorted(p.name for p in d.iterdir() if p.is_dir()) if d.is_dir() else []


def _active_project() -> str | None:
    f = VAULT / ".active"
    if not f.is_file() or not f.stat().st_size:
        return None
    name = f.read_text(encoding="utf-8").splitlines()[0].strip()
    return name if NAME_RE.fullmatch(name) and name in _projects() else None


def _project_dir() -> tuple[str | None, Path | None, str | None]:
    p = _active_project()
    if not p:
        return None, None, "No active QA project. Use /projects and /use <name>."
    return p, VAULT / "projects" / p, None


def _title(path: Path) -> str:
    try:
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.startswith("# "):
                return line[2:].strip()
    except OSError:
        pass
    return path.stem


def _newest(folder: Path, limit: int, exclude: tuple[str, ...] = ()) -> list[Path]:
    if not folder.is_dir():
        return []
    files = [f for f in folder.glob("*.md") if f.name not in exclude]
    return sorted(files, key=lambda f: f.stat().st_mtime, reverse=True)[:limit]


def _handle_note(raw_args: str) -> str:
    text = (raw_args or "").strip()
    if not text:
        return "Usage: /note <text> — saved to the active QA project's inbox."
    if len(text) > MAX_CHARS:
        return f"Note too long ({len(text)} chars, max {MAX_CHARS})."
    project, pdir, err = _project_dir()
    if err:
        return err + " Note not saved."
    inbox = pdir / "inbox"
    if not inbox.is_dir():
        return f"Project '{project}' has no inbox folder — note not saved."
    now = datetime.now()
    stem = now.strftime("%Y-%m-%d-%H%M%S") + "-note"
    path = inbox / f"{stem}.md"
    n = 1
    while path.exists():
        path = inbox / f"{stem}-{n}.md"
        n += 1
    body = ("---\ntype: inbox-note\nsource: telegram /note\n"
            f"received: {now.strftime('%Y-%m-%d %H:%M')}\n---\n{text}\n")
    with open(path, "x", encoding="utf-8") as fh:  # "x": never overwrite
        fh.write(body)
    return f"📥 Saved to {project}/inbox/{path.name}"


def _handle_questions(_: str) -> str:
    project, pdir, err = _project_dir()
    if err:
        return err
    hits = []
    for f in sorted(pdir.rglob("*.md")):
        try:
            lines = f.read_text(encoding="utf-8").splitlines()
        except OSError:
            continue
        for line in lines:
            if "[!question]" in line:
                q = line.split("[!question]", 1)[1].strip(" >-")
                hits.append(f"• {q or '(see note)'}  — {f.relative_to(pdir)}")
    if not hits:
        return f"❓ {project}: no open questions."
    more = f"\n…and {len(hits) - 15} more" if len(hits) > 15 else ""
    return f"❓ {project}: {len(hits)} open questions\n" + "\n".join(hits[:15]) + more


def _handle_bugs(_: str) -> str:
    project, pdir, err = _project_dir()
    if err:
        return err
    files = _newest(pdir / "bugs" / "drafts", 10)
    if not files:
        return f"🐞 {project}: no bug drafts."
    return f"🐞 {project}: bug drafts (newest first)\n" + "\n".join(f"• {_title(f)}" for f in files)


def _handle_runs(_: str) -> str:
    project, pdir, err = _project_dir()
    if err:
        return err
    files = _newest(pdir / "runs", 5, exclude=("flaky.md",))
    if not files:
        return f"▶ {project}: no run summaries yet."
    out = [f"▶ {project}: latest runs"]
    for f in files:
        lines = [l for l in f.read_text(encoding="utf-8").splitlines()
                 if l.strip() and not l.startswith(("---", "type:", "#"))]
        out.append(f"• {_title(f)}" + (f"\n  {lines[0][:150]}" if lines else ""))
    return "\n".join(out)


def _handle_inbox(_: str) -> str:
    project, pdir, err = _project_dir()
    if err:
        return err
    files = _newest(pdir / "inbox", 15)
    if not files:
        return f"📥 {project}: inbox is empty."
    return (f"📥 {project}: {len(files)} unprocessed\n" + "\n".join(f"• {f.name}" for f in files)
            + "\n\nOn the Mac: qa → \"Process the inbox\"")


def _handle_projects(_: str) -> str:
    projects = _projects()
    if not projects:
        return "No projects yet. On the Mac: qa new <name>"
    active = _active_project()
    return "📁 Projects\n" + "\n".join(f"{'★' if p == active else '•'} {p}" for p in projects)


def _handle_use(raw_args: str) -> str:
    name = (raw_args or "").strip()
    if not name:
        return "Usage: /use <project>  (see /projects)"
    if not NAME_RE.fullmatch(name) or name not in _projects():
        return f"No project '{name}'. See /projects."
    (VAULT / ".active").write_text(name + "\n", encoding="utf-8")
    return f"★ Active project: {name}"


def register(ctx) -> None:
    ctx.register_command("note", handler=_handle_note, description="Save a note to the QA inbox")
    ctx.register_command("questions", handler=_handle_questions, description="Open questions in the QA project")
    ctx.register_command("bugs", handler=_handle_bugs, description="Bug drafts awaiting review")
    ctx.register_command("runs", handler=_handle_runs, description="Latest test run summaries")
    ctx.register_command("inbox", handler=_handle_inbox, description="Unprocessed QA inbox items")
    ctx.register_command("projects", handler=_handle_projects, description="List QA projects")
    ctx.register_command("use", handler=_handle_use, description="Switch active QA project")
