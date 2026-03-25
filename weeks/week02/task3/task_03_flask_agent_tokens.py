import os
from collections import deque
from pathlib import Path
import sys
from typing import Any

from flask import Flask, jsonify, redirect, render_template, request

if str(Path(__file__).resolve().parents[3]) not in sys.path:
    sys.path.append(str(Path(__file__).resolve().parents[3]))

from weeks.week02.task3.agent import YandexLLMAgent, load_local_env
from weeks.week02.task3.storage import (
    load_history,
    load_metrics,
    save_history,
    save_metrics,
)


DEFAULT_SYSTEM_PROMPT = (
    "Ты senior Product Manager AI-стартапа. "
    "Давай практичные и измеримые рекомендации, учитывай ограничения по срокам "
    "и ресурсам, выделяй риски и компромиссы. "
    "Структурируй ответ: 1) гипотеза/идея, 2) ожидаемый эффект, "
    "3) риск, 4) метрика проверки. "
    "Пиши кратко, по делу, без воды."
)
MAX_HISTORY_MESSAGES = 5
HISTORY_FILE = Path(__file__).resolve().parent / "chat_history.json"
METRICS_FILE = Path(__file__).resolve().parent / "metrics_history.json"
AGENT_MAX_DIALOG_TOKENS = 1000
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

history = deque(
    load_history(HISTORY_FILE, max_messages=MAX_HISTORY_MESSAGES),
    maxlen=MAX_HISTORY_MESSAGES,
)
metrics_history = load_metrics(METRICS_FILE)
api_key_broken = False
BROKEN_API_KEY = "invalid_key_for_demo"

app = Flask(__name__)


def render_page(
    *,
    system_prompt: str,
    error_message: str = "",
) -> str:
    dialog_total_tokens = (
        int(metrics_history[-1]["dialog_total_tokens"]) if metrics_history else 0
    )
    dialog_total_cost = (
        float(metrics_history[-1]["dialog_total_cost_usd"]) if metrics_history else 0.0
    )
    return render_template(
        "index.html",
        history=list(history),
        metrics_history=metrics_history,
        system_prompt=system_prompt,
        model=model,
        max_history_messages=MAX_HISTORY_MESSAGES,
        error_message=error_message,
        api_key_broken=api_key_broken,
        history_file=str(HISTORY_FILE),
        metrics_file=str(METRICS_FILE),
        agent_max_dialog_tokens=AGENT_MAX_DIALOG_TOKENS,
        dialog_total_tokens=dialog_total_tokens,
        dialog_total_cost_usd=dialog_total_cost,
        input_price_per_1k=INPUT_PRICE_PER_1K,
        output_price_per_1k=OUTPUT_PRICE_PER_1K,
    )


def estimate_turn_cost(input_tokens: int, output_tokens: int) -> float:
    return (input_tokens / 1000.0) * INPUT_PRICE_PER_1K + (
        output_tokens / 1000.0
    ) * OUTPUT_PRICE_PER_1K


def process_user_message(user_message: str, system_prompt: str) -> tuple[str, str, dict[str, Any]]:
    if not user_message:
        return "", "Введите сообщение перед отправкой.", {}

    dialog_total_tokens = (
        int(metrics_history[-1]["dialog_total_tokens"]) if metrics_history else 0
    )
    dialog_total_cost = (
        float(metrics_history[-1]["dialog_total_cost_usd"]) if metrics_history else 0.0
    )
    if dialog_total_tokens >= AGENT_MAX_DIALOG_TOKENS:
        error_text = (
            "Лимит токенов диалога превышен. "
            f"Текущий total={dialog_total_tokens}, лимит={AGENT_MAX_DIALOG_TOKENS}. "
            "Очистите чат для продолжения."
        )
        history.append({"role": "user", "content": user_message})
        history.append({"role": "assistant", "content": error_text})
        save_history(HISTORY_FILE, list(history))
        return error_text, "", {}

    api_key_override = BROKEN_API_KEY if api_key_broken else None
    result = agent.ask(
        user_message,
        history_messages=list(history),
        system_prompt=system_prompt,
        api_key_override=api_key_override,
    )
    answer = result["text"]
    usage = result.get("usage", {})

    turn_input_tokens = int(usage.get("input_tokens", 0))
    turn_output_tokens = int(usage.get("output_tokens", 0))
    turn_total_tokens = int(usage.get("total_tokens", 0))
    turn_cost = estimate_turn_cost(turn_input_tokens, turn_output_tokens)
    next_dialog_total_tokens = dialog_total_tokens + turn_total_tokens
    next_dialog_total_cost = dialog_total_cost + turn_cost

    metrics_point = {
        "turn_index": len(metrics_history) + 1,
        "input_tokens": turn_input_tokens,
        "output_tokens": turn_output_tokens,
        "total_tokens": turn_total_tokens,
        "dialog_total_tokens": next_dialog_total_tokens,
        "turn_cost_usd": turn_cost,
        "dialog_total_cost_usd": next_dialog_total_cost,
    }
    metrics_history.append(metrics_point)
    save_metrics(METRICS_FILE, metrics_history)

    history.append({"role": "user", "content": user_message})
    history.append({"role": "assistant", "content": answer})
    save_history(HISTORY_FILE, list(history))
    return answer, "", metrics_point


@app.get("/")
def index() -> str:
    return render_page(system_prompt=DEFAULT_SYSTEM_PROMPT)


@app.post("/ask")
def ask() -> str:
    user_message = request.form.get("message", "").strip()
    system_prompt = request.form.get("system_prompt", DEFAULT_SYSTEM_PROMPT).strip()
    if not system_prompt:
        system_prompt = DEFAULT_SYSTEM_PROMPT

    _, error_message, _ = process_user_message(user_message, system_prompt)
    return render_page(system_prompt=system_prompt, error_message=error_message)


@app.post("/ask-json")
def ask_json() -> tuple[Any, int]:
    user_message = request.form.get("message", "").strip()
    system_prompt = request.form.get("system_prompt", DEFAULT_SYSTEM_PROMPT).strip()
    if not system_prompt:
        system_prompt = DEFAULT_SYSTEM_PROMPT

    answer, error_message, metrics_point = process_user_message(user_message, system_prompt)
    if error_message:
        return jsonify({"ok": False, "error_message": error_message}), 400

    dialog_total_tokens = (
        int(metrics_history[-1]["dialog_total_tokens"]) if metrics_history else 0
    )
    dialog_total_cost = (
        float(metrics_history[-1]["dialog_total_cost_usd"]) if metrics_history else 0.0
    )

    return (
        jsonify(
            {
                "ok": True,
                "assistant_message": answer,
                "history": list(history),
                "metrics_history": metrics_history,
                "latest_metrics": metrics_point,
                "dialog_total_tokens": dialog_total_tokens,
                "dialog_total_cost_usd": dialog_total_cost,
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
    history.clear()
    save_history(HISTORY_FILE, list(history))
    metrics_history.clear()
    save_metrics(METRICS_FILE, metrics_history)
    return redirect("/")


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5002, debug=True)

