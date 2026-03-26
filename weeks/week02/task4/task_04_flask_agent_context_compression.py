import os
from pathlib import Path
import sys
from typing import Any

from flask import Flask, jsonify, redirect, render_template, request

if str(Path(__file__).resolve().parents[3]) not in sys.path:
    sys.path.append(str(Path(__file__).resolve().parents[3]))

from weeks.week02.task4.agent import YandexLLMAgent, load_local_env
from weeks.week02.task4.storage import (
    load_history,
    load_metrics,
    load_summary_state,
    save_history,
    save_metrics,
    save_summary_state,
)

DEFAULT_SYSTEM_PROMPT = (
    "Ты senior Product Manager AI-стартапа. "
    "Давай практичные и измеримые рекомендации, учитывай ограничения по срокам "
    "и ресурсам, выделяй риски и компромиссы. "
    "Структурируй ответ: 1) гипотеза/идея, 2) ожидаемый эффект, "
    "3) риск, 4) метрика проверки. "
    "Пиши кратко, по делу, без воды."
)
RECENT_MESSAGES_N = 3
SUMMARY_BATCH_SIZE = 10
SUMMARY_MAX_CHARS = 2500
HISTORY_FULL_FILE = Path(__file__).resolve().parent / "chat_history_full.json"
HISTORY_COMPRESSED_FILE = Path(__file__).resolve().parent / "chat_history_compressed.json"
METRICS_FILE = Path(__file__).resolve().parent / "metrics_history.json"
SUMMARY_STATE_FILE = Path(__file__).resolve().parent / "summary_state.json"
INPUT_PRICE_PER_1K = 0.002
OUTPUT_PRICE_PER_1K = 0.006

load_local_env()

api_key = os.getenv("YANDEX_API_KEY", "")
folder_id = os.getenv("YANDEX_FOLDER_ID", "")
model = os.getenv("YANDEX_MODEL", "yandexgpt-lite")

if not api_key or not folder_id:
    raise SystemExit(
        "Missing YANDEX_API_KEY or YANDEX_FOLDER_ID. "
        "Create .env from .env.example and fill real values."
    )

agent = YandexLLMAgent(
    api_key=api_key,
    folder_id=folder_id,
    model=model,
    default_system_prompt=DEFAULT_SYSTEM_PROMPT,
)

history_full = load_history(HISTORY_FULL_FILE, max_messages=None)
history_compressed = load_history(HISTORY_COMPRESSED_FILE, max_messages=None)
metrics_history = load_metrics(METRICS_FILE)
summary_state = load_summary_state(SUMMARY_STATE_FILE)
if summary_state["summarized_count"] > len(history_compressed):
    summary_state["summarized_count"] = 0
    summary_state["summary_text"] = ""
    save_summary_state(SUMMARY_STATE_FILE, summary_state)

api_key_broken = False
BROKEN_API_KEY = "invalid_key_for_demo"
app = Flask(__name__)


def estimate_turn_cost(input_tokens: int, output_tokens: int) -> float:
    return (input_tokens / 1000.0) * INPUT_PRICE_PER_1K + (
        output_tokens / 1000.0
    ) * OUTPUT_PRICE_PER_1K


def compact_message(item: dict[str, str]) -> str:
    role = "U" if item.get("role") == "user" else "A"
    content = item.get("content", "").strip().replace("\n", " ")
    if len(content) > 140:
        content = content[:137] + "..."
    return f"{role}: {content}"


def append_summary_chunk(existing_summary: str, chunk: list[dict[str, str]]) -> str:
    lines = [compact_message(item) for item in chunk]
    new_part = "\n".join(lines)
    if not existing_summary:
        combined = new_part
    else:
        combined = existing_summary + "\n" + new_part
    if len(combined) > SUMMARY_MAX_CHARS:
        combined = combined[-SUMMARY_MAX_CHARS:]
    return combined


def maybe_rollup_summary() -> None:
    changed = False
    while len(history_compressed) - int(summary_state["summarized_count"]) > (
        RECENT_MESSAGES_N + SUMMARY_BATCH_SIZE
    ):
        start = int(summary_state["summarized_count"])
        end = start + SUMMARY_BATCH_SIZE
        chunk = history_compressed[start:end]
        summary_state["summary_text"] = append_summary_chunk(
            str(summary_state["summary_text"]), chunk
        )
        summary_state["summarized_count"] = end
        changed = True
    if changed:
        save_summary_state(SUMMARY_STATE_FILE, summary_state)


def build_mode_totals() -> dict[str, dict[str, float]]:
    if not metrics_history:
        zero = {"input_tokens": 0, "output_tokens": 0, "total_tokens": 0, "total_cost_usd": 0.0}
        return {"full": dict(zero), "compressed": dict(zero)}
    last = metrics_history[-1]
    return {
        "full": {
            "input_tokens": int(last.get("full_input_total_tokens", 0)),
            "output_tokens": int(last.get("full_output_total_tokens", 0)),
            "total_tokens": int(last.get("full_total_tokens", 0)),
            "total_cost_usd": float(last.get("full_total_cost_usd", 0.0)),
        },
        "compressed": {
            "input_tokens": int(last.get("compressed_input_total_tokens", 0)),
            "output_tokens": int(last.get("compressed_output_total_tokens", 0)),
            "total_tokens": int(last.get("compressed_total_tokens", 0)),
            "total_cost_usd": float(last.get("compressed_total_cost_usd", 0.0)),
        },
    }


def render_page(*, system_prompt: str, error_message: str = "") -> str:
    mode_totals = build_mode_totals()
    return render_template(
        "index.html",
        history_full=history_full[-40:],
        history_compressed=history_compressed[-40:],
        metrics_history=metrics_history,
        mode_totals=mode_totals,
        system_prompt=system_prompt,
        model=model,
        error_message=error_message,
        api_key_broken=api_key_broken,
        history_full_file=str(HISTORY_FULL_FILE),
        history_compressed_file=str(HISTORY_COMPRESSED_FILE),
        metrics_file=str(METRICS_FILE),
        summary_state_file=str(SUMMARY_STATE_FILE),
        summary_chars=len(str(summary_state["summary_text"])),
        summarized_count=int(summary_state["summarized_count"]),
        recent_messages_n=RECENT_MESSAGES_N,
        summary_batch_size=SUMMARY_BATCH_SIZE,
        input_price_per_1k=INPUT_PRICE_PER_1K,
        output_price_per_1k=OUTPUT_PRICE_PER_1K,
    )


def process_user_message(user_message: str, system_prompt: str) -> tuple[str, dict[str, Any]]:
    if not user_message:
        return "Введите сообщение перед отправкой.", {}

    summary_text = str(summary_state["summary_text"])
    recent_start = max(
        int(summary_state["summarized_count"]),
        len(history_compressed) - RECENT_MESSAGES_N,
    )
    compressed_context_messages = history_compressed[recent_start:]
    api_key_override = BROKEN_API_KEY if api_key_broken else None
    result_full = agent.ask(
        user_message,
        history_messages=history_full,
        summary_text="",
        system_prompt=system_prompt,
        api_key_override=api_key_override,
    )
    result_compressed = agent.ask(
        user_message,
        history_messages=compressed_context_messages,
        summary_text=summary_text,
        system_prompt=system_prompt,
        api_key_override=api_key_override,
    )

    answer_full = result_full["text"]
    answer_compressed = result_compressed["text"]

    usage_full = result_full.get("usage", {})
    usage_compressed = result_compressed.get("usage", {})

    full_input_tokens = int(usage_full.get("input_tokens", 0))
    full_output_tokens = int(usage_full.get("output_tokens", 0))
    full_total_tokens = int(usage_full.get("total_tokens", 0))
    full_turn_cost = estimate_turn_cost(full_input_tokens, full_output_tokens)

    compressed_input_tokens = int(usage_compressed.get("input_tokens", 0))
    compressed_output_tokens = int(usage_compressed.get("output_tokens", 0))
    compressed_total_tokens = int(usage_compressed.get("total_tokens", 0))
    compressed_turn_cost = estimate_turn_cost(
        compressed_input_tokens, compressed_output_tokens
    )

    history_full.append({"role": "user", "content": user_message})
    history_full.append({"role": "assistant", "content": answer_full})
    save_history(HISTORY_FULL_FILE, history_full)

    history_compressed.append({"role": "user", "content": user_message})
    history_compressed.append({"role": "assistant", "content": answer_compressed})
    save_history(HISTORY_COMPRESSED_FILE, history_compressed)
    maybe_rollup_summary()

    prev = metrics_history[-1] if metrics_history else {}
    metrics_point = {
        "turn_index": len(metrics_history) + 1,
        "full_input_tokens": full_input_tokens,
        "full_output_tokens": full_output_tokens,
        "full_total_tokens_turn": full_total_tokens,
        "full_turn_cost_usd": full_turn_cost,
        "full_input_total_tokens": int(prev.get("full_input_total_tokens", 0))
        + full_input_tokens,
        "full_output_total_tokens": int(prev.get("full_output_total_tokens", 0))
        + full_output_tokens,
        "full_total_tokens": int(prev.get("full_total_tokens", 0)) + full_total_tokens,
        "full_total_cost_usd": float(prev.get("full_total_cost_usd", 0.0))
        + full_turn_cost,
        "compressed_input_tokens": compressed_input_tokens,
        "compressed_output_tokens": compressed_output_tokens,
        "compressed_total_tokens_turn": compressed_total_tokens,
        "compressed_turn_cost_usd": compressed_turn_cost,
        "compressed_input_total_tokens": int(
            prev.get("compressed_input_total_tokens", 0)
        )
        + compressed_input_tokens,
        "compressed_output_total_tokens": int(
            prev.get("compressed_output_total_tokens", 0)
        )
        + compressed_output_tokens,
        "compressed_total_tokens": int(prev.get("compressed_total_tokens", 0))
        + compressed_total_tokens,
        "compressed_total_cost_usd": float(
            prev.get("compressed_total_cost_usd", 0.0)
        )
        + compressed_turn_cost,
        "summary_chars_used": len(summary_text),
        "context_messages_used": len(compressed_context_messages),
    }
    metrics_history.append(metrics_point)
    save_metrics(METRICS_FILE, metrics_history)
    return "", metrics_point


@app.get("/")
def index() -> str:
    return render_page(system_prompt=DEFAULT_SYSTEM_PROMPT)


@app.post("/ask")
def ask() -> str:
    user_message = request.form.get("message", "").strip()
    system_prompt = request.form.get("system_prompt", DEFAULT_SYSTEM_PROMPT).strip()
    if not system_prompt:
        system_prompt = DEFAULT_SYSTEM_PROMPT
    error_message, _ = process_user_message(user_message, system_prompt)
    return render_page(system_prompt=system_prompt, error_message=error_message)


@app.post("/ask-json")
def ask_json() -> tuple[Any, int]:
    user_message = request.form.get("message", "").strip()
    system_prompt = request.form.get("system_prompt", DEFAULT_SYSTEM_PROMPT).strip()
    if not system_prompt:
        system_prompt = DEFAULT_SYSTEM_PROMPT

    error_message, metrics_point = process_user_message(user_message, system_prompt)
    if error_message:
        return jsonify({"ok": False, "error_message": error_message}), 400

    mode_totals = build_mode_totals()
    return (
        jsonify(
            {
                "ok": True,
                "history_full": history_full[-40:],
                "history_compressed": history_compressed[-40:],
                "metrics_history": metrics_history,
                "latest_metrics": metrics_point,
                "mode_totals": mode_totals,
                "summary_chars": len(str(summary_state["summary_text"])),
                "summarized_count": int(summary_state["summarized_count"]),
                "system_prompt": system_prompt,
            }
        ),
        200,
    )


@app.post("/toggle-api-key")
def toggle_api_key() -> str:
    global api_key_broken
    api_key_broken = not api_key_broken
    return redirect("/")


@app.post("/clear-history")
def clear_history() -> str:
    history_full.clear()
    history_compressed.clear()
    metrics_history.clear()
    summary_state["summary_text"] = ""
    summary_state["summarized_count"] = 0
    save_history(HISTORY_FULL_FILE, history_full)
    save_history(HISTORY_COMPRESSED_FILE, history_compressed)
    save_metrics(METRICS_FILE, metrics_history)
    save_summary_state(SUMMARY_STATE_FILE, summary_state)
    return redirect("/")


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5003, debug=True)

