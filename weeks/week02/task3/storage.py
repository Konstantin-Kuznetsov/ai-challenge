import json
from pathlib import Path


def load_history(path: Path, max_messages: int) -> list[dict[str, str]]:
    if not path.exists():
        return []
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return []

    if not isinstance(raw, list):
        return []

    normalized: list[dict[str, str]] = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        role = item.get("role", "")
        content = item.get("content", "")
        if role in {"user", "assistant"} and isinstance(content, str):
            normalized.append({"role": role, "content": content})
    if len(normalized) > max_messages:
        normalized = normalized[-max_messages:]
    return normalized


def save_history(path: Path, history: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(history, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def load_metrics(path: Path) -> list[dict[str, float]]:
    if not path.exists():
        return []
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return []
    if not isinstance(raw, list):
        return []

    normalized: list[dict[str, float]] = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        try:
            normalized.append(
                {
                    "turn_index": int(item.get("turn_index", 0)),
                    "input_tokens": int(item.get("input_tokens", 0)),
                    "output_tokens": int(item.get("output_tokens", 0)),
                    "total_tokens": int(item.get("total_tokens", 0)),
                    "dialog_total_tokens": int(item.get("dialog_total_tokens", 0)),
                    "turn_cost_usd": float(item.get("turn_cost_usd", 0.0)),
                    "dialog_total_cost_usd": float(item.get("dialog_total_cost_usd", 0.0)),
                }
            )
        except (TypeError, ValueError):
            continue
    return normalized


def save_metrics(path: Path, metrics: list[dict[str, float]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(metrics, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

