import os
from pathlib import Path
import re
import sys
from typing import Any, Literal

from flask import Flask, jsonify, redirect, render_template, request

if str(Path(__file__).resolve().parents[3]) not in sys.path:
    sys.path.append(str(Path(__file__).resolve().parents[3]))

from weeks.week02.task5.agent import YandexLLMAgent, load_local_env
from weeks.week02.task5.storage import load_metrics, load_state, save_metrics, save_state


Strategy = Literal["sliding", "facts", "branching"]
StrategyChoice = Literal["sliding", "facts", "branching", "compare_all"]
STRATEGIES: tuple[Strategy, ...] = ("sliding", "facts", "branching")
WINDOW_MESSAGES_N = 6

DEFAULT_SYSTEM_PROMPT = (
    "Ты senior Product Manager AI-стартапа. "
    "Давай практичные и измеримые рекомендации, учитывай ограничения по срокам "
    "и ресурсам, выделяй риски и компромиссы. "
    "Структурируй ответ: 1) гипотеза/идея, 2) ожидаемый эффект, "
    "3) риск, 4) метрика проверки. "
    "Пиши кратко, по делу, без воды."
)
STATE_FILE = Path(__file__).resolve().parent / "context_state.json"
METRICS_FILE = Path(__file__).resolve().parent / "metrics_history.json"
INPUT_PRICE_PER_1K = 0.002
OUTPUT_PRICE_PER_1K = 0.006
MAX_FACTS = 20

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
state = load_state(STATE_FILE)
metrics_history = load_metrics(METRICS_FILE)
api_key_broken = False
BROKEN_API_KEY = "invalid_key_for_demo"
app = Flask(__name__)


def estimate_turn_cost(input_tokens: int, output_tokens: int) -> float:
    return (input_tokens / 1000.0) * INPUT_PRICE_PER_1K + (
        output_tokens / 1000.0
    ) * OUTPUT_PRICE_PER_1K


def update_facts_from_user_message(facts: dict[str, str], user_message: str) -> None:
    # explicit "key: value" pairs from user are treated as facts
    for line in user_message.splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        key = key.strip().lower()
        value = value.strip()
        if not key or not value or len(key) > 40:
            continue
        facts[key] = value

    patterns = {
        "goal": r"(?:цель|goal)\s*[:\-]\s*(.+)",
        "constraints": r"(?:ограничения|constraints?)\s*[:\-]\s*(.+)",
        "preferences": r"(?:предпочтения|preferences?)\s*[:\-]\s*(.+)",
        "decisions": r"(?:решение|решения|decisions?)\s*[:\-]\s*(.+)",
        "agreements": r"(?:договоренности|agreement|agreements)\s*[:\-]\s*(.+)",
    }
    for key, pattern in patterns.items():
        match = re.search(pattern, user_message, flags=re.IGNORECASE)
        if match:
            facts[key] = match.group(1).strip()

    # keep last MAX_FACTS entries
    while len(facts) > MAX_FACTS:
        oldest_key = next(iter(facts.keys()))
        del facts[oldest_key]


def facts_to_summary(facts: dict[str, str]) -> str:
    if not facts:
        return ""
    lines = ["Sticky facts (key-value memory):"]
    for key, value in facts.items():
        lines.append(f"- {key}: {value}")
    return "\n".join(lines)


def get_context_for_strategy(strategy: Strategy) -> tuple[list[dict[str, str]], str, str]:
    if strategy == "sliding":
        history = state["sliding"]["history"][-WINDOW_MESSAGES_N:]
        return history, "", ""
    if strategy == "facts":
        history = state["facts"]["history"][-WINDOW_MESSAGES_N:]
        summary = facts_to_summary(state["facts"]["facts"])
        return history, summary, ""
    branching = state["branching"]
    active_branch = branching["active_branch"]
    history = branching["branches"].get(active_branch, [])
    return history, "", active_branch


def append_messages(strategy: Strategy, user_message: str, answer: str) -> None:
    if strategy == "sliding":
        state["sliding"]["history"].append({"role": "user", "content": user_message})
        state["sliding"]["history"].append({"role": "assistant", "content": answer})
        return
    if strategy == "facts":
        state["facts"]["history"].append({"role": "user", "content": user_message})
        state["facts"]["history"].append({"role": "assistant", "content": answer})
        return
    branching = state["branching"]
    active_branch = branching["active_branch"]
    branch_history = branching["branches"].setdefault(active_branch, [])
    branch_history.append({"role": "user", "content": user_message})
    branch_history.append({"role": "assistant", "content": answer})


def get_visible_history(strategy: Strategy) -> list[dict[str, str]]:
    if strategy == "sliding":
        return state["sliding"]["history"][-40:]
    if strategy == "facts":
        return state["facts"]["history"][-40:]
    branching = state["branching"]
    active_branch = branching["active_branch"]
    return branching["branches"].get(active_branch, [])[-40:]


def cumulative_by_strategy(strategy: Strategy) -> dict[str, float]:
    input_tokens = 0
    output_tokens = 0
    total_tokens = 0
    total_cost = 0.0
    for item in metrics_history:
        if item.get("strategy") != strategy:
            continue
        input_tokens += int(item.get("input_tokens", 0))
        output_tokens += int(item.get("output_tokens", 0))
        total_tokens += int(item.get("total_tokens", 0))
        total_cost += float(item.get("turn_cost_usd", 0.0))
    return {
        "input_tokens": input_tokens,
        "output_tokens": output_tokens,
        "total_tokens": total_tokens,
        "total_cost_usd": total_cost,
    }


def render_page(selected_strategy: Strategy, system_prompt: str, error_message: str = "") -> str:
    branching = state["branching"]
    mode_totals = {name: cumulative_by_strategy(name) for name in STRATEGIES}
    return render_template(
        "index.html",
        selected_strategy=selected_strategy,
        history=get_visible_history(selected_strategy),
        mode_totals=mode_totals,
        metrics_history=metrics_history,
        system_prompt=system_prompt,
        model=model,
        error_message=error_message,
        api_key_broken=api_key_broken,
        state_file=str(STATE_FILE),
        metrics_file=str(METRICS_FILE),
        window_messages_n=WINDOW_MESSAGES_N,
        facts_count=len(state["facts"]["facts"]),
        active_branch=branching["active_branch"],
        branches=sorted(branching["branches"].keys()),
        checkpoint=branching["checkpoint"],
        input_price_per_1k=INPUT_PRICE_PER_1K,
        output_price_per_1k=OUTPUT_PRICE_PER_1K,
    )


def process_turn(
    strategy: Strategy, user_message: str, system_prompt: str
) -> tuple[str, str, dict[str, Any]]:
    if not user_message:
        return "", "Введите сообщение перед отправкой.", {}

    if strategy == "facts":
        update_facts_from_user_message(state["facts"]["facts"], user_message)

    context_messages, summary_text, branch_name = get_context_for_strategy(strategy)
    api_key_override = BROKEN_API_KEY if api_key_broken else None
    result = agent.ask(
        user_message,
        history_messages=context_messages,
        summary_text=summary_text,
        system_prompt=system_prompt,
        api_key_override=api_key_override,
    )
    answer = result["text"]
    usage = result.get("usage", {})

    append_messages(strategy, user_message, answer)
    save_state(STATE_FILE, state)

    input_tokens = int(usage.get("input_tokens", 0))
    output_tokens = int(usage.get("output_tokens", 0))
    total_tokens = int(usage.get("total_tokens", 0))
    turn_cost = estimate_turn_cost(input_tokens, output_tokens)
    metrics_point = {
        "turn_index": len(metrics_history) + 1,
        "strategy": strategy,
        "branch": branch_name,
        "input_tokens": input_tokens,
        "output_tokens": output_tokens,
        "total_tokens": total_tokens,
        "turn_cost_usd": turn_cost,
        "context_messages_used": len(context_messages),
        "facts_used": len(state["facts"]["facts"]) if strategy == "facts" else 0,
    }
    metrics_history.append(metrics_point)
    save_metrics(METRICS_FILE, metrics_history)
    return answer, "", metrics_point


def parse_strategy_choice(raw: str) -> StrategyChoice:
    value = raw.strip().lower()
    if value in STRATEGIES or value == "compare_all":
        return value  # type: ignore[return-value]
    return "sliding"


@app.get("/")
def index() -> str:
    strategy_raw = request.args.get("strategy", "sliding")
    selected = parse_strategy_choice(strategy_raw)
    selected_strategy: Strategy = "sliding" if selected == "compare_all" else selected
    return render_page(selected_strategy, DEFAULT_SYSTEM_PROMPT)


@app.post("/ask")
def ask() -> str:
    selected = parse_strategy_choice(request.form.get("strategy", "sliding"))
    selected_strategy: Strategy = "sliding" if selected == "compare_all" else selected
    user_message = request.form.get("message", "").strip()
    system_prompt = request.form.get("system_prompt", DEFAULT_SYSTEM_PROMPT).strip()
    if not system_prompt:
        system_prompt = DEFAULT_SYSTEM_PROMPT
    if selected == "compare_all":
        error_message = ""
        for strategy in STRATEGIES:
            _, err, _ = process_turn(strategy, user_message, system_prompt)
            if err:
                error_message = err
                break
    else:
        _, error_message, _ = process_turn(selected_strategy, user_message, system_prompt)
    return render_page(selected_strategy, system_prompt, error_message)


@app.post("/ask-json")
def ask_json() -> tuple[Any, int]:
    selected = parse_strategy_choice(request.form.get("strategy", "sliding"))
    selected_strategy: Strategy = "sliding" if selected == "compare_all" else selected
    user_message = request.form.get("message", "").strip()
    system_prompt = request.form.get("system_prompt", DEFAULT_SYSTEM_PROMPT).strip()
    if not system_prompt:
        system_prompt = DEFAULT_SYSTEM_PROMPT

    compare_answers: dict[str, str] = {}
    if selected == "compare_all":
        metrics_points: list[dict[str, Any]] = []
        for strategy in STRATEGIES:
            answer, error_message, metrics_point = process_turn(
                strategy, user_message, system_prompt
            )
            if error_message:
                return jsonify({"ok": False, "error_message": error_message}), 400
            compare_answers[strategy] = answer
            metrics_points.append(metrics_point)
        latest_metrics = metrics_points[-1] if metrics_points else {}
    else:
        answer, error_message, latest_metrics = process_turn(
            selected_strategy, user_message, system_prompt
        )
        if error_message:
            return jsonify({"ok": False, "error_message": error_message}), 400

    branching = state["branching"]
    mode_totals = {name: cumulative_by_strategy(name) for name in STRATEGIES}
    return (
        jsonify(
            {
                "ok": True,
                "assistant_message": compare_answers.get(selected_strategy, ""),
                "compare_answers": compare_answers,
                "history": get_visible_history(selected_strategy),
                "metrics_history": metrics_history,
                "latest_metrics": latest_metrics,
                "mode_totals": mode_totals,
                "selected_strategy": selected,
                "facts_count": len(state["facts"]["facts"]),
                "active_branch": branching["active_branch"],
                "branches": sorted(branching["branches"].keys()),
                "checkpoint": branching["checkpoint"],
                "system_prompt": system_prompt,
            }
        ),
        200,
    )


@app.post("/toggle-api-key")
def toggle_api_key() -> str:
    global api_key_broken
    api_key_broken = not api_key_broken
    strategy = request.form.get("strategy", "sliding").strip().lower()
    return redirect(f"/?strategy={strategy}")


@app.post("/clear-all")
def clear_all() -> str:
    global state, metrics_history
    state = {
        "sliding": {"history": []},
        "facts": {"history": [], "facts": {}},
        "branching": {
            "branches": {"main": []},
            "active_branch": "main",
            "checkpoint": None,
            "branch_counter": 0,
        },
    }
    metrics_history = []
    save_state(STATE_FILE, state)
    save_metrics(METRICS_FILE, metrics_history)
    strategy = request.form.get("strategy", "sliding").strip().lower()
    return redirect(f"/?strategy={strategy}")


@app.post("/branch/save-checkpoint")
def save_checkpoint() -> str:
    branching = state["branching"]
    active = branching["active_branch"]
    branch_history = branching["branches"].get(active, [])
    branching["checkpoint"] = len(branch_history)
    save_state(STATE_FILE, state)
    return redirect("/?strategy=branching")


@app.post("/branch/create-two")
def create_two_branches() -> str:
    branching = state["branching"]
    active = branching["active_branch"]
    branch_history = branching["branches"].get(active, [])
    checkpoint = branching["checkpoint"]
    if checkpoint is None:
        checkpoint = len(branch_history)
    checkpoint = max(0, min(int(checkpoint), len(branch_history)))
    base = branch_history[:checkpoint]
    counter = int(branching.get("branch_counter", 0)) + 1
    branching["branch_counter"] = counter
    name_a = f"branch_{counter}_a"
    name_b = f"branch_{counter}_b"
    branching["branches"][name_a] = [dict(item) for item in base]
    branching["branches"][name_b] = [dict(item) for item in base]
    branching["active_branch"] = name_a
    save_state(STATE_FILE, state)
    return redirect("/?strategy=branching")


@app.post("/branch/switch")
def switch_branch() -> str:
    branch_name = request.form.get("branch_name", "").strip()
    branching = state["branching"]
    if branch_name in branching["branches"]:
        branching["active_branch"] = branch_name
        save_state(STATE_FILE, state)
    return redirect("/?strategy=branching")


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5004, debug=True)
