import os
from collections import deque
from pathlib import Path
import sys

from flask import Flask, redirect, render_template, request

if str(Path(__file__).resolve().parents[3]) not in sys.path:
    sys.path.append(str(Path(__file__).resolve().parents[3]))

from weeks.week02.task1.agent import YandexLLMAgent, load_local_env


DEFAULT_SYSTEM_PROMPT = (
    "Ты senior Product Manager AI-стартапа. "
    "Давай практичные и измеримые рекомендации, учитывай ограничения по срокам "
    "и ресурсам, выделяй риски и компромиссы. "
    "Структурируй ответ: 1) гипотеза/идея, 2) ожидаемый эффект, "
    "3) риск, 4) метрика проверки. "
    "Пиши кратко, по делу, без воды."
)
MAX_HISTORY_MESSAGES = 5

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

history: deque[dict[str, str]] = deque(maxlen=MAX_HISTORY_MESSAGES)
api_key_broken = False
BROKEN_API_KEY = "invalid_key_for_demo"

app = Flask(__name__)


@app.get("/")
def index() -> str:
    return render_template(
        "index.html",
        history=list(history),
        system_prompt=DEFAULT_SYSTEM_PROMPT,
        model=model,
        max_history_messages=MAX_HISTORY_MESSAGES,
        error_message="",
        api_key_broken=api_key_broken,
    )


@app.post("/ask")
def ask() -> str:
    global api_key_broken
    user_message = request.form.get("message", "").strip()
    system_prompt = request.form.get("system_prompt", DEFAULT_SYSTEM_PROMPT).strip()
    if not system_prompt:
        system_prompt = DEFAULT_SYSTEM_PROMPT

    error_message = ""
    if not user_message:
        error_message = "Введите сообщение перед отправкой."
    else:
        history.append({"role": "user", "content": user_message})
        api_key_override = BROKEN_API_KEY if api_key_broken else None
        answer = agent.ask(
            user_message,
            system_prompt=system_prompt,
            api_key_override=api_key_override,
        )
        history.append({"role": "assistant", "content": answer})

    return render_template(
        "index.html",
        history=list(history),
        system_prompt=system_prompt,
        model=model,
        max_history_messages=MAX_HISTORY_MESSAGES,
        error_message=error_message,
        api_key_broken=api_key_broken,
    )


@app.post("/toggle-api-key")
def toggle_api_key() -> str:
    global api_key_broken
    api_key_broken = not api_key_broken
    return redirect("/")


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5000, debug=True)

