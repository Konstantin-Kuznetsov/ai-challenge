import json
from pathlib import Path
from typing import Any


def default_state() -> dict[str, Any]:
    return {
        "sliding": {"history": []},
        "facts": {"history": [], "facts": {}},
        "branching": {
            "branches": {"main": []},
            "active_branch": "main",
            "checkpoint": None,
            "branch_counter": 0,
        },
    }


def load_state(path: Path) -> dict[str, Any]:
    if not path.exists():
        return default_state()
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return default_state()
    if not isinstance(raw, dict):
        return default_state()

    state = default_state()
    for key in ("sliding", "facts", "branching"):
        if isinstance(raw.get(key), dict):
            state[key].update(raw[key])

    if not isinstance(state["sliding"].get("history"), list):
        state["sliding"]["history"] = []
    if not isinstance(state["facts"].get("history"), list):
        state["facts"]["history"] = []
    if not isinstance(state["facts"].get("facts"), dict):
        state["facts"]["facts"] = {}
    branching = state["branching"]
    if not isinstance(branching.get("branches"), dict):
        branching["branches"] = {"main": []}
    if not branching["branches"]:
        branching["branches"] = {"main": []}
    for name, branch in list(branching["branches"].items()):
        if not isinstance(branch, list):
            branching["branches"][name] = []
    if not isinstance(branching.get("active_branch"), str):
        branching["active_branch"] = "main"
    if branching["active_branch"] not in branching["branches"]:
        branching["active_branch"] = next(iter(branching["branches"]), "main")
    if not isinstance(branching.get("branch_counter"), int):
        branching["branch_counter"] = 0
    checkpoint = branching.get("checkpoint")
    if checkpoint is not None and not isinstance(checkpoint, int):
        branching["checkpoint"] = None

    return state


def save_state(path: Path, state: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")


def load_metrics(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return []
    if not isinstance(raw, list):
        return []
    return [item for item in raw if isinstance(item, dict)]


def save_metrics(path: Path, metrics: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")
