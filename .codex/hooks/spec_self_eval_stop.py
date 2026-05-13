#!/usr/bin/env python3
"""Codex Stop hook: run spec-self-eval for specs touched in this turn."""

from __future__ import annotations

import json
import os
import re
import shlex
import subprocess
import sys
import tempfile
import time
from pathlib import Path


SPEC_PATH_RE = re.compile(
    r"(?P<path>(?:[A-Za-z]:)?/?(?:[\w.-]+/)*\.specs/(?P<feature>[^/\s\"'`<>]+)(?:/[^\s\"'`<>]+)?)"
)
FAIL_HEADING_RE = re.compile(r"^##\s+(.+?)\s+(?:-|\u2014)\s+FAIL\s*$", re.MULTILINE)
EVIDENCE_RE = re.compile(r"^Evidence:\s*(.+)$", re.MULTILINE)
WRITE_COMMAND_RE = re.compile(
    r"(^|\s)(apply_patch|tee|touch|mkdir|mv|cp|rm|install|sed\s+-i|perl\s+-[^\s]*i|python3?\s+-c|node\s+-e)\b|[<>]"
)


def emit(payload: dict[str, object]) -> None:
    sys.stdout.write(json.dumps(payload, ensure_ascii=False))
    sys.stdout.write("\n")


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


def features_from_text(text: str, repo: Path) -> set[str]:
    features: set[str] = set()
    for match in SPEC_PATH_RE.finditer(text):
        path = match.group("path")
        feature = match.group("feature")
        normalized = path
        if normalized.startswith(str(repo)):
            normalized = str(Path(normalized).relative_to(repo))
        if normalized.startswith(".specs/") and feature:
            features.add(feature)
    return features


def command_may_write_spec(command: str) -> bool:
    return bool(WRITE_COMMAND_RE.search(command))


def current_turn_features(transcript_path: str | None, turn_id: str | None, repo: Path) -> set[str]:
    if not transcript_path:
        return set()

    path = Path(transcript_path)
    if not path.is_file():
        return set()

    features: set[str] = set()
    active = turn_id is None
    seen_any_turn = False

    with path.open("r", encoding="utf-8") as transcript:
        for line in transcript:
            try:
                item = json.loads(line)
            except json.JSONDecodeError:
                continue

            item_type = item.get("type")
            payload = item.get("payload") if isinstance(item.get("payload"), dict) else {}

            if item_type == "turn_context":
                seen_any_turn = True
                active = payload.get("turn_id") == turn_id if turn_id else True
                continue

            if not active:
                continue

            if item_type != "response_item" or payload.get("type") != "function_call":
                continue

            name = str(payload.get("name") or "")
            arguments = payload.get("arguments")
            arg_text = arguments if isinstance(arguments, str) else json.dumps(arguments)

            if name == "apply_patch":
                features.update(features_from_text(arg_text, repo))
                continue

            if name == "exec_command":
                command = ""
                try:
                    parsed_args = json.loads(arg_text)
                    if isinstance(parsed_args, dict):
                        command = str(parsed_args.get("cmd") or "")
                except json.JSONDecodeError:
                    command = arg_text
                if command_may_write_spec(command):
                    features.update(features_from_text(command, repo))

    if turn_id and not seen_any_turn:
        return set()
    return features


def state_path(session_id: str, repo: Path) -> Path:
    state_dir = Path(
        os.environ.get(
            "CODEX_SPEC_SELF_EVAL_STATE_DIR",
            str(Path(tempfile.gettempdir()) / "codex-spec-self-eval-stop"),
        )
    )
    repo_key = re.sub(r"[^A-Za-z0-9_.-]+", "_", str(repo.resolve()))
    session_key = re.sub(r"[^A-Za-z0-9_.-]+", "_", session_id or "unknown-session")
    state_dir.mkdir(parents=True, exist_ok=True)
    return state_dir / f"{repo_key}-{session_key}.json"


def load_pending(path: Path) -> set[str]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        return set()
    values = data.get("pending_features") if isinstance(data, dict) else None
    if not isinstance(values, list):
        return set()
    return {str(value) for value in values if value}


def save_pending(path: Path, features: set[str]) -> None:
    if not features:
        try:
            path.unlink()
        except FileNotFoundError:
            pass
        return
    path.write_text(
        json.dumps({"pending_features": sorted(features)}, indent=2) + "\n",
        encoding="utf-8",
    )


def newest_report(feature_dir: Path) -> Path | None:
    review_dir = feature_dir / "reviews"
    reports = list(review_dir.glob("review-*.md"))
    if not reports:
        return None
    return max(reports, key=lambda candidate: candidate.stat().st_mtime_ns)


def run_spec_self_eval(repo: Path, feature: str) -> tuple[int, str]:
    feature_dir = repo / ".specs" / feature
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
            timeout=1800,
        )
        return completed.returncode, completed.stdout

    prompt = f"""Use the project-local spec-self-eval skill for this repository.

Evaluate exactly this feature spec folder: .specs/{feature}

Follow .codex-skills/spec-self-eval/SKILL.md and write the dated review report into .specs/{feature}/reviews/.
Do not modify requirements.md, design.md, tasks.md, glossary.md, _delta.md, or implementation files.
Return a concise summary naming the report file and verdict."""

    command = [
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
    completed = subprocess.run(
        command,
        cwd=repo,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=1800,
    )
    return completed.returncode, completed.stdout


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

    if not failures and re.search(r"^Verdict:\s*blocked\s*$", content, re.MULTILINE):
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


def main() -> int:
    hook_input = load_hook_input()
    repo = Path(str(hook_input.get("cwd") or os.getcwd())).resolve()
    session_id = str(hook_input.get("session_id") or "unknown-session")
    turn_id = hook_input.get("turn_id")
    transcript_path = hook_input.get("transcript_path")

    state = state_path(session_id, repo)
    touched = current_turn_features(
        str(transcript_path) if transcript_path else None,
        str(turn_id) if turn_id else None,
        repo,
    )
    pending = load_pending(state)
    features = touched | pending

    if not features:
        emit({"continue": True, "suppressOutput": True})
        return 0

    still_failing: dict[str, tuple[Path | None, list[str]]] = {}
    passed: set[str] = set()

    for feature in sorted(features):
        feature_dir = repo / ".specs" / feature
        if not feature_dir.is_dir():
            still_failing[feature] = (None, [f".specs/{feature}/ is missing"])
            continue

        before = newest_report(feature_dir)
        started_at = time.time()
        returncode, output = run_spec_self_eval(repo, feature)
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

        failures = extract_failures(after)
        if failures:
            still_failing[feature] = (after, failures)
        else:
            passed.add(feature)

    save_pending(state, set(still_failing))

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

    emit({"continue": True, "suppressOutput": True})
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
