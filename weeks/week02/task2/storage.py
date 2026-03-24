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

