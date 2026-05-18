#!/usr/bin/env python3
"""Universal spec-self-eval Stop hook for Codex and Claude Code.

Shared logic, two thin entrypoints selected by ``--agent``:

1. Detect feature specs that are **new or modified in git** (cheap first-pass
   filter — unchanged docs are never read).
2. Among those, keep only the ones whose spec content has **drifted** from the
   ``spec_hashes`` recorded in the latest review report (SHA-256), or that have
   no review report yet.
3. For each, run the project's ``spec-self-eval`` skill via the host agent and
   write a dated review report.
4. If any report carries a blocking FAIL, emit ``decision: block`` so the turn
   does not finish until the spec is fixed.

Codex and Claude Code differ only in the subprocess command; both read the
hook payload as JSON on stdin and accept the same JSON decision on stdout.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shlex
import subprocess
import sys
import time
from pathlib import Path


TRACKED_SPEC_FILES = ("requirements.md", "design.md", "tasks.md")
RECURSION_GUARD_ENV = "SPEC_SELF_EVAL_RUNNING"
SUBPROCESS_TIMEOUT = 1800

FAIL_HEADING_RE = re.compile(r"^##\s+(.+?)\s+(?:-|—)\s+FAIL\s*$", re.MULTILINE)
EVIDENCE_RE = re.compile(r"^Evidence:\s*(.+)$", re.MULTILINE)
FRONTMATTER_RE = re.compile(r"\A---[ \t]*\n(?P<body>.*?)\n---[ \t]*\n", re.DOTALL)
SPEC_HASHES_KEY_RE = re.compile(r"^spec_hashes[ \t]*:[ \t]*$")
SPEC_HASH_ENTRY_RE = re.compile(
    r"^[ \t]+(?P<file>[\w.-]+)[ \t]*:[ \t]*[\"']?(?P<hash>[a-fA-F0-9]{64}|absent)[\"']?[ \t]*$"
)


# --------------------------------------------------------------------------
# hook I/O
# --------------------------------------------------------------------------
def emit(payload: dict[str, object]) -> None:
    sys.stdout.write(json.dumps(payload, ensure_ascii=False))
    sys.stdout.write("\n")


def emit_continue() -> None:
    emit({"continue": True, "suppressOutput": True})


def load_hook_input() -> dict[str, object]:
    raw = sys.stdin.read()
    if not raw.strip():
        return {}
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise SystemExit(f"invalid Stop hook JSON on stdin: {exc}") from exc
    if not isinstance(value, dict):
        raise SystemExit("invalid Stop hook JSON: expected object")
    return value


# --------------------------------------------------------------------------
# git first-pass filter
# --------------------------------------------------------------------------
def feature_from_spec_path(path: str) -> str | None:
    """Return the feature name if ``path`` is a tracked spec file or an
    untracked whole-feature directory under ``.specs/``."""
    normalized = path.strip().strip('"')
    is_dir_entry = normalized.endswith("/")
    parts = Path(normalized.rstrip("/")).parts
    if (
        len(parts) >= 3
        and parts[0] == ".specs"
        and parts[2] in TRACKED_SPEC_FILES
    ):
        return parts[1]
    # git collapses an untracked new feature folder to ".specs/<feature>/"
    if is_dir_entry and len(parts) == 2 and parts[0] == ".specs":
        return parts[1]
    return None


def git_changed_features(repo: Path) -> set[str]:
    """Features whose spec docs are new/modified/untracked in the working tree.

    ``--untracked-files=all`` is required: without it git collapses a fully
    untracked tree to a single ``?? .specs/`` entry and the feature name is
    lost. The pathspec keeps the scan bounded to ``.specs``, so it stays cheap.
    """
    try:
        result = subprocess.run(
            ["git", "-C", str(repo), "status", "--porcelain",
             "--untracked-files=all", "--", ".specs"],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            timeout=30,
        )
    except (OSError, subprocess.SubprocessError):
        return set()
    if result.returncode != 0:
        return set()

    features: set[str] = set()
    for line in result.stdout.splitlines():
        if len(line) < 4:
            continue
        path = line[3:]
        if " -> " in path:  # rename/copy: keep the destination path
            path = path.split(" -> ", 1)[1]
        feature = feature_from_spec_path(path)
        if feature:
            features.add(feature)
    return features


# --------------------------------------------------------------------------
# SHA-256 drift check
# --------------------------------------------------------------------------
def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def current_spec_hashes(feature_dir: Path) -> dict[str, str]:
    hashes: dict[str, str] = {}
    for filename in TRACKED_SPEC_FILES:
        path = feature_dir / filename
        hashes[filename] = sha256_file(path) if path.is_file() else "absent"
    return hashes


def newest_report(feature_dir: Path) -> Path | None:
    review_dir = feature_dir / "reviews"
    reports = list(review_dir.glob("review-*.md"))
    if not reports:
        return None
    # Sort by mtime, then filename as a deterministic tiebreaker — dated
    # ``review-YYYY-MM-DD.md`` names sort lexically into chronological order,
    # so two reports written in the same mtime tick still resolve stably.
    return max(reports, key=lambda candidate: (candidate.stat().st_mtime_ns, candidate.name))


def split_frontmatter(content: str) -> tuple[str | None, str]:
    """Return ``(frontmatter_body, rest)``; frontmatter_body is None if absent."""
    match = FRONTMATTER_RE.match(content)
    if not match:
        return None, content
    return match.group("body"), content[match.end():]


def report_spec_hashes(report: Path) -> dict[str, str]:
    """Read the ``spec_hashes`` mapping from the report's YAML frontmatter."""
    body, _ = split_frontmatter(report.read_text(encoding="utf-8"))
    if body is None:
        return {}
    hashes: dict[str, str] = {}
    in_block = False
    for line in body.splitlines():
        if SPEC_HASHES_KEY_RE.match(line):
            in_block = True
            continue
        if not in_block:
            continue
        entry = SPEC_HASH_ENTRY_RE.match(line)
        if entry and entry.group("file") in TRACKED_SPEC_FILES:
            hashes[entry.group("file")] = entry.group("hash").lower()
            continue
        if line.strip() and not line[0].isspace():
            break  # left the indented block
    return hashes


REQUIRED_SPEC_FILES = ("requirements.md", "design.md")


def feature_has_reviewable_spec(feature_dir: Path) -> bool:
    """The skill needs requirements.md + design.md to produce a meaningful review."""
    return all((feature_dir / name).is_file() for name in REQUIRED_SPEC_FILES)


def missing_required_specs(feature_dir: Path) -> list[str]:
    """Required spec files that are absent from a git-changed feature dir."""
    return [name for name in REQUIRED_SPEC_FILES if not (feature_dir / name).is_file()]


def reviewable_features(repo: Path) -> set[str]:
    """git-changed feature dirs that currently carry a reviewable spec."""
    specs_dir = repo / ".specs"
    if not specs_dir.is_dir():
        return set()
    result: set[str] = set()
    for feature in git_changed_features(repo):
        feature_dir = specs_dir / feature
        if feature_dir.is_dir() and feature_has_reviewable_spec(feature_dir):
            result.add(feature)
    return result


def classify_feature(feature_dir: Path) -> tuple[str, list[str]]:
    """Decide what a git-changed feature needs:

    - ("eval", []): spec is new or has drifted from its latest review.
    - ("blocked", failures): spec is unchanged since a review that FAILed.
      Re-block with the recorded findings without re-running the skill — a
      failed gate must not clear itself just because the turn stopped again.
    - ("ok", []): spec is unchanged since a clean review.
    """
    report = newest_report(feature_dir)
    if report is None:
        return ("eval", [])
    stored = report_spec_hashes(report)
    if set(stored) != set(TRACKED_SPEC_FILES):
        return ("eval", [])
    if current_spec_hashes(feature_dir) != stored:
        return ("eval", [])
    failures = extract_failures(report)
    if failures:
        return ("blocked", failures)
    return ("ok", [])


def deletion_resolved_features(repo: Path, reviewable: set[str]) -> dict[str, Path]:
    """git-changed features that can no longer be reviewed because a required
    spec file was deleted, yet still carry a prior review report.

    These must not silently fall off the gate: the resolution is recorded in
    the report (see ``append_resolution_note``) and the gate then releases.
    Features whose whole directory is gone have no report to annotate and are
    left alone, matching the existing missing-dir behaviour.
    """
    specs_dir = repo / ".specs"
    if not specs_dir.is_dir():
        return {}
    resolved: dict[str, Path] = {}
    for feature in git_changed_features(repo):
        if feature in reviewable:
            continue
        feature_dir = specs_dir / feature
        if not feature_dir.is_dir():
            continue
        report = newest_report(feature_dir)
        if report is not None:
            resolved[feature] = report
    return resolved


# --------------------------------------------------------------------------
# fallback hash injection (frontmatter)
# --------------------------------------------------------------------------
def render_spec_hashes_block(hashes: dict[str, str]) -> str:
    lines = ["spec_hashes:"]
    for filename in TRACKED_SPEC_FILES:
        lines.append(f"  {filename}: {hashes[filename]}")
    return "\n".join(lines)


def ensure_report_hashes(report: Path, hashes: dict[str, str]) -> None:
    """Guarantee the report frontmatter carries a correct ``spec_hashes`` block,
    even if the skill forgot to write it or wrote a stale one."""
    content = report.read_text(encoding="utf-8")
    block = render_spec_hashes_block(hashes)
    body, rest = split_frontmatter(content)

    if body is None:
        updated = f"---\n{block}\n---\n{content}"
    else:
        kept: list[str] = []
        skipping = False
        for line in body.splitlines():
            if SPEC_HASHES_KEY_RE.match(line):
                skipping = True
                continue
            if skipping:
                if line.strip() and line[0].isspace():
                    continue  # drop old indented entries
                skipping = False
            kept.append(line)
        prefix = "\n".join(kept).rstrip()
        new_body = f"{prefix}\n{block}" if prefix else block
        updated = f"---\n{new_body}\n---\n{rest}"

    if updated != content:
        report.write_text(updated, encoding="utf-8")


RESOLUTION_HEADING = "## Resolution"


def append_resolution_note(report: Path, missing: list[str]) -> bool:
    """Record in ``report`` that the gate was released because required spec
    files were deleted. Idempotent: a report that already carries a
    ``## Resolution`` section is left untouched (the note persists across the
    repeated Stop events the still-uncommitted deletion would otherwise cause).
    Returns True when a note was written."""
    content = report.read_text(encoding="utf-8")
    if RESOLUTION_HEADING in content:
        return False
    files = ", ".join(missing)
    verb = "was deleted" if len(missing) == 1 else "were deleted"
    today = time.strftime("%Y-%m-%d", time.gmtime())
    note = (
        f"\n\n{RESOLUTION_HEADING}\n"
        f"RESOLVED {today}: gate released — {files} {verb}; "
        f"feature spec no longer present for review.\n"
    )
    report.write_text(content.rstrip() + note, encoding="utf-8")
    return True


# --------------------------------------------------------------------------
# eval runner (agent-specific)
# --------------------------------------------------------------------------
def build_prompt(agent: str, feature: str) -> str:
    if agent == "codex":
        skill_ref = "Follow .codex-skills/spec-self-eval/SKILL.md."
    else:
        skill_ref = "Use the project-local spec-self-eval skill."
    return f"""{skill_ref}

Evaluate exactly this feature spec folder: .specs/{feature}

Write the dated review report into .specs/{feature}/reviews/ as the skill's
SKILL.md describes. The report must begin with a YAML frontmatter block whose
spec_hashes key lists requirements.md, design.md, and tasks.md exactly as the
skill template requires.
Do not modify requirements.md, design.md, tasks.md, glossary.md, _delta.md, or
implementation files.
Return a concise summary naming the report file and verdict."""


def build_command(agent: str, repo: Path, prompt: str) -> list[str]:
    if agent == "codex":
        return [
            "codex",
            "--disable",
            "codex_hooks",
            "--disable",
            "hooks",
            "-C",
            str(repo),
            "-s",
            "workspace-write",
            "-a",
            "never",
            "exec",
            prompt,
        ]
    return [
        "claude",
        "-p",
        prompt,
        "--permission-mode",
        "bypassPermissions",
    ]


def run_spec_self_eval(agent: str, repo: Path, feature: str) -> tuple[int, str]:
    feature_dir = repo / ".specs" / feature
    env = dict(os.environ)
    env[RECURSION_GUARD_ENV] = "1"

    override = os.environ.get("SPEC_SELF_EVAL_COMMAND")
    if override:
        command = override.format(
            repo=shlex.quote(str(repo)),
            feature=shlex.quote(feature),
            feature_dir=shlex.quote(str(feature_dir)),
        )
        completed = subprocess.run(
            command,
            cwd=repo,
            shell=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=SUBPROCESS_TIMEOUT,
            env=env,
        )
        return completed.returncode, completed.stdout

    completed = subprocess.run(
        build_command(agent, repo, build_prompt(agent, feature)),
        cwd=repo,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=SUBPROCESS_TIMEOUT,
        env=env,
    )
    return completed.returncode, completed.stdout


# --------------------------------------------------------------------------
# report verdict parsing
# --------------------------------------------------------------------------
def extract_failures(report: Path) -> list[str]:
    content = report.read_text(encoding="utf-8")
    failures: list[str] = []

    for match in FAIL_HEADING_RE.finditer(content):
        section = match.group(1).strip()
        evidence_match = EVIDENCE_RE.search(content, match.end())
        if evidence_match:
            failures.append(f"{section}: {evidence_match.group(1).strip()}")
        else:
            failures.append(section)

    for line in content.splitlines():
        stripped = line.strip()
        if "[FAIL]" in stripped or "**FAIL**" in stripped:
            failures.append(stripped)

    if not failures and re.search(r"^#*\s*Verdict:\s*blocked\s*$", content, re.MULTILINE):
        failures.append("Verdict: blocked")

    deduped: list[str] = []
    seen: set[str] = set()
    for failure in failures:
        if failure not in seen:
            deduped.append(failure)
            seen.add(failure)
    return deduped


def block(reason: str) -> None:
    emit({"decision": "block", "reason": reason})


# --------------------------------------------------------------------------
# entrypoint
# --------------------------------------------------------------------------
def main() -> int:
    parser = argparse.ArgumentParser(description="spec-self-eval Stop hook")
    parser.add_argument("--agent", choices=("codex", "claude"), required=True)
    args = parser.parse_args()

    # Recursion guard: the eval subprocess runs the host agent, whose own Stop
    # hook would re-enter this script. The eval sets this env var; bail early.
    if os.environ.get(RECURSION_GUARD_ENV):
        emit_continue()
        return 0

    hook_input = load_hook_input()
    repo = Path(str(hook_input.get("cwd") or os.getcwd())).resolve()

    features = reviewable_features(repo)

    # A previously-reviewed feature whose required spec files were deleted can
    # no longer be evaluated. Record the resolution in its report so the gate
    # release is auditable, then let the turn finish.
    for feature, report in sorted(deletion_resolved_features(repo, features).items()):
        append_resolution_note(report, missing_required_specs(repo / ".specs" / feature))

    if not features:
        emit_continue()
        return 0

    still_failing: dict[str, tuple[Path | None, list[str]]] = {}

    for feature in sorted(features):
        feature_dir = repo / ".specs" / feature
        kind, recorded = classify_feature(feature_dir)

        if kind == "ok":
            # Spec unchanged since a clean review — nothing to do.
            continue

        if kind == "blocked":
            # Spec unchanged since a FAILed review. Re-block with the recorded
            # findings without re-running the skill: a failed gate must not
            # clear itself just because the turn stopped again.
            still_failing[feature] = (newest_report(feature_dir), recorded)
            continue

        # kind == "eval": new or drifted spec — run the skill.
        before = newest_report(feature_dir)
        started_at = time.time()
        returncode, output = run_spec_self_eval(args.agent, repo, feature)
        after = newest_report(feature_dir)

        if returncode != 0:
            still_failing[feature] = (
                after,
                [f"spec-self-eval command exited {returncode}: {output.strip()[-1200:]}"],
            )
            continue

        if after is None:
            still_failing[feature] = (None, ["spec-self-eval did not create a review report"])
            continue

        if before == after and after.stat().st_mtime < started_at - 1:
            still_failing[feature] = (after, ["spec-self-eval did not create a fresh review report"])
            continue

        ensure_report_hashes(after, current_spec_hashes(feature_dir))
        failures = extract_failures(after)
        if failures:
            still_failing[feature] = (after, failures)

    if still_failing:
        lines = ["spec-self-eval found blocking FAIL items. Fix the spec, then stop again."]
        for feature, (report, failures) in still_failing.items():
            target = f".specs/{feature}/"
            if report:
                target = f"{target} ({report.relative_to(repo)})"
            lines.append(f"- {target}")
            for failure in failures:
                lines.append(f"  - {failure}")
        block("\n".join(lines))
        return 0

    emit_continue()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
