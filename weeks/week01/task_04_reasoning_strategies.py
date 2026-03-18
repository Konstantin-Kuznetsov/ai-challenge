import argparse
import json
import os
import re
from pathlib import Path
from typing import Any, Dict
from urllib import error, request


DEFAULT_TASK = (
    "Петя дал младшему брату половину запаса яблок и еще одно яблоко, "
    "и у него не осталось ни одного яблока. Сколько яблок было у Пети?"
)


def load_local_env() -> None:
    """Load variables from project .env if they are missing in os.environ."""
    project_root = Path(__file__).resolve().parents[2]
    env_path = project_root / ".env"
    if not env_path.exists():
        return

    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Solve one task with four prompting strategies and compare answers."
    )
    parser.add_argument(
        "--task",
        type=str,
        default=DEFAULT_TASK,
        help="Task statement to solve in all strategies.",
    )
    parser.add_argument(
        "--system",
        type=str,
        default="Ты полезный AI-ассистент по логическим задачам.",
        help="System prompt.",
    )
    parser.add_argument(
        "--model",
        type=str,
        default="",
        help="Model name. Example: yandexgpt-lite or yandexgpt.",
    )
    parser.add_argument(
        "--temperature",
        type=float,
        default=0.2,
        help="Sampling temperature for all calls.",
    )
    parser.add_argument(
        "--max-tokens",
        dest="max_tokens",
        type=int,
        default=500,
        help="Token limit for responses.",
    )
    parser.add_argument(
        "--expected",
        type=str,
        default="2",
        help="Expected numeric answer for simple automatic validation.",
    )
    return parser.parse_args()


def build_payload(
    folder_id: str,
    model: str,
    system_prompt: str,
    user_prompt: str,
    temperature: float,
    max_tokens: int,
) -> Dict[str, Any]:
    return {
        "modelUri": f"gpt://{folder_id}/{model}/latest",
        "completionOptions": {
            "stream": False,
            "temperature": temperature,
            "maxTokens": str(max_tokens),
        },
        "messages": [
            {"role": "system", "text": system_prompt},
            {"role": "user", "text": user_prompt},
        ],
    }


def call_yandex_gpt(api_key: str, payload: Dict[str, Any]) -> Dict[str, Any]:
    url = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
    data = json.dumps(payload).encode("utf-8")
    req = request.Request(
        url=url,
        data=data,
        method="POST",
        headers={
            "Authorization": f"Api-Key {api_key}",
            "Content-Type": "application/json",
        },
    )
    with request.urlopen(req, timeout=60) as response:
        body = response.read().decode("utf-8")
    return json.loads(body)


def extract_text(response: Dict[str, Any]) -> str:
    result = response.get("result", {})
    alternatives = result.get("alternatives", [])
    if not alternatives:
        return json.dumps(response, ensure_ascii=False)
    return alternatives[0].get("message", {}).get("text", "").strip()


def call_strategy(
    api_key: str,
    folder_id: str,
    model: str,
    system_prompt: str,
    user_prompt: str,
    temperature: float,
    max_tokens: int,
) -> str:
    payload = build_payload(
        folder_id=folder_id,
        model=model,
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        temperature=temperature,
        max_tokens=max_tokens,
    )
    response = call_yandex_gpt(api_key=api_key, payload=payload)
    return extract_text(response)


def likely_contains_answer(text: str, expected: str) -> bool:
    expected_number = expected.strip()
    if not expected_number:
        return False
    if re.search(rf"\b{re.escape(expected_number)}\b", text):
        return True
    if expected_number == "2" and re.search(r"\bдва\b", text, flags=re.IGNORECASE):
        return True
    if expected_number == "3" and re.search(r"\bтри\b", text, flags=re.IGNORECASE):
        return True
    return False


def print_block(title: str, text: str) -> None:
    print(f"\n=== {title} ===")
    print(text or "<empty>")
    print(f"\n[chars={len(text)}]")


def first_non_empty_line(text: str) -> str:
    for line in text.splitlines():
        cleaned = line.strip()
        if cleaned:
            return cleaned
    return "<empty>"


def shorten(text: str, limit: int = 72) -> str:
    if len(text) <= limit:
        return text
    return text[: limit - 3] + "..."


def print_ascii_table(title: str, headers: list[str], rows: list[list[str]]) -> None:
    widths = [len(h) for h in headers]
    for row in rows:
        for idx, cell in enumerate(row):
            widths[idx] = max(widths[idx], len(cell))

    def row_line(values: list[str]) -> str:
        parts = [f" {value.ljust(widths[idx])} " for idx, value in enumerate(values)]
        return "|" + "|".join(parts) + "|"

    separator = "+" + "+".join("-" * (w + 2) for w in widths) + "+"
    print(f"\n=== {title} ===")
    print(separator)
    print(row_line(headers))
    print(separator)
    for row in rows:
        print(row_line(row))
    print(separator)


def main() -> None:
    load_local_env()
    args = parse_args()

    api_key = os.getenv("YANDEX_API_KEY", "")
    folder_id = os.getenv("YANDEX_FOLDER_ID", "")
    model = args.model or os.getenv("YANDEX_MODEL", "yandexgpt-lite")
    if not api_key or not folder_id:
        raise SystemExit(
            "Missing YANDEX_API_KEY or YANDEX_FOLDER_ID. "
            "Create .env from .env.example and fill real values."
        )

    prompt_direct = f"Реши задачу и дай финальный ответ:\n\n{args.task}"
    prompt_step = (
        "Реши задачу пошагово, затем отдельной строкой напиши только финальный ответ:\n\n"
        f"{args.task}"
    )
    prompt_make_prompt = (
        "Составь лучший короткий промпт, чтобы LLM точно решила задачу. "
        "Верни только текст промпта без пояснений.\n\n"
        f"Задача: {args.task}"
    )
    prompt_experts = (
        "Реши задачу как группа экспертов.\n"
        "1) Аналитик: формализует условие.\n"
        "2) Инженер: решает через уравнение.\n"
        "3) Критик: проверяет решение и возможные ошибки.\n"
        "В конце выдай согласованный финальный ответ отдельной строкой.\n\n"
        f"Задача: {args.task}"
    )

    try:
        direct_answer = call_strategy(
            api_key,
            folder_id,
            model,
            args.system,
            prompt_direct,
            args.temperature,
            args.max_tokens,
        )
        step_answer = call_strategy(
            api_key,
            folder_id,
            model,
            args.system,
            prompt_step,
            args.temperature,
            args.max_tokens,
        )
        generated_prompt = call_strategy(
            api_key,
            folder_id,
            model,
            args.system,
            prompt_make_prompt,
            args.temperature,
            args.max_tokens,
        )
        prompt_based_answer = call_strategy(
            api_key,
            folder_id,
            model,
            args.system,
            generated_prompt,
            args.temperature,
            args.max_tokens,
        )
        experts_answer = call_strategy(
            api_key,
            folder_id,
            model,
            args.system,
            prompt_experts,
            args.temperature,
            args.max_tokens,
        )
    except error.HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {exc.code}: {details}") from exc
    except error.URLError as exc:
        raise SystemExit(f"Network error: {exc.reason}") from exc

    print(f"model={model} temperature={args.temperature} max_tokens={args.max_tokens}")
    print(f"task={args.task}")
    print_block("1) Прямой ответ", direct_answer)
    print_block("2) Инструкция 'решай пошагово'", step_answer)
    print_block("3a) Сгенерированный промпт", generated_prompt)
    print_block("3b) Ответ по сгенерированному промпту", prompt_based_answer)
    print_block("4) Группа экспертов", experts_answer)

    checks = {
        "direct": likely_contains_answer(direct_answer, args.expected),
        "step_by_step": likely_contains_answer(step_answer, args.expected),
        "prompt_generated": likely_contains_answer(prompt_based_answer, args.expected),
        "experts": likely_contains_answer(experts_answer, args.expected),
    }

    print("\n=== Сравнение ===")
    print("Ожидаемый ответ:", args.expected)
    print(json.dumps(checks, ensure_ascii=False, indent=2))

    rows_summary = [
        [
            "direct",
            "yes" if checks["direct"] else "no",
            str(len(direct_answer)),
            shorten(first_non_empty_line(direct_answer)),
        ],
        [
            "step_by_step",
            "yes" if checks["step_by_step"] else "no",
            str(len(step_answer)),
            shorten(first_non_empty_line(step_answer)),
        ],
        [
            "prompt_generated",
            "yes" if checks["prompt_generated"] else "no",
            str(len(prompt_based_answer)),
            shorten(first_non_empty_line(prompt_based_answer)),
        ],
        [
            "experts",
            "yes" if checks["experts"] else "no",
            str(len(experts_answer)),
            shorten(first_non_empty_line(experts_answer)),
        ],
    ]
    print_ascii_table(
        title="Сводная таблица по стратегиям",
        headers=["strategy", "contains_expected", "chars", "preview"],
        rows=rows_summary,
    )


if __name__ == "__main__":
    main()
