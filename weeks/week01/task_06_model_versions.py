import argparse
import json
import os
import time
from pathlib import Path
from typing import Any, Dict, Optional
from urllib import error, request


DEFAULT_PROMPT = (
    "Ты продуктовый аналитик. Предложи план из 5 шагов, как за 2 недели "
    "увеличить retention в мобильном приложении. Для каждого шага добавь: "
    "ожидаемый эффект, риск и метрику."
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
        description="Compare response quality, speed, token usage and cost across model versions."
    )
    parser.add_argument(
        "--prompt",
        type=str,
        default=DEFAULT_PROMPT,
        help="Same prompt sent to all models.",
    )
    parser.add_argument(
        "--system",
        type=str,
        default="Ты полезный AI-ассистент. Отвечай структурированно и по делу.",
        help="System prompt for all calls.",
    )
    parser.add_argument(
        "--model-weak",
        type=str,
        default="yandexgpt-lite",
        help="Model name for weak tier.",
    )
    parser.add_argument(
        "--model-medium",
        type=str,
        default="yandexgpt",
        help="Model name for non-lite tier.",
    )
    parser.add_argument(
        "--temperature",
        type=float,
        default=0.3,
        help="Sampling temperature for all models.",
    )
    parser.add_argument(
        "--max-tokens",
        dest="max_tokens",
        type=int,
        default=500,
        help="Token limit for each model call.",
    )
    parser.add_argument(
        "--price-input-per-1k",
        dest="price_input_per_1k",
        type=float,
        default=0.0,
        help="Optional input price per 1K tokens (same for all models).",
    )
    parser.add_argument(
        "--price-output-per-1k",
        dest="price_output_per_1k",
        type=float,
        default=0.0,
        help="Optional output price per 1K tokens (same for all models).",
    )
    parser.add_argument(
        "--expected",
        type=str,
        default="",
        help="Optional expected fragment for quick quality check.",
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


def extract_usage(response: Dict[str, Any]) -> Dict[str, int]:
    usage = response.get("result", {}).get("usage", {})
    # Field names can differ between API versions, so we normalize softly.
    input_tokens = int(
        usage.get("inputTextTokens")
        or usage.get("input_tokens")
        or usage.get("promptTokens")
        or 0
    )
    output_tokens = int(
        usage.get("completionTokens")
        or usage.get("output_tokens")
        or usage.get("completion_tokens")
        or 0
    )
    total_tokens = int(usage.get("totalTokens") or usage.get("total_tokens") or 0)
    if total_tokens == 0:
        total_tokens = input_tokens + output_tokens
    return {
        "input_tokens": input_tokens,
        "output_tokens": output_tokens,
        "total_tokens": total_tokens,
    }


def contains_expected(text: str, expected: str) -> Optional[bool]:
    value = expected.strip()
    if not value:
        return None
    return value.lower() in text.lower()


def estimate_cost(
    input_tokens: int,
    output_tokens: int,
    input_price_per_1k: float,
    output_price_per_1k: float,
) -> float:
    return (input_tokens / 1000.0) * input_price_per_1k + (
        output_tokens / 1000.0
    ) * output_price_per_1k


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


def print_block(title: str, text: str) -> None:
    print(f"\n=== {title} ===")
    print(text or "<empty>")
    print(f"\n[chars={len(text)}]")


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


def summarize_findings(rows: list[dict[str, Any]]) -> str:
    fastest = min(rows, key=lambda item: item["latency_ms"])
    cheapest = min(rows, key=lambda item: item["estimated_cost"])
    richest = max(rows, key=lambda item: item["output_tokens"])
    return (
        f"Самая быстрая: {fastest['label']} ({fastest['latency_ms']} ms); "
        f"самая экономная: {cheapest['label']} "
        f"(${cheapest['estimated_cost']:.6f}); "
        f"самый подробный ответ: {richest['label']} "
        f"({richest['output_tokens']} output tokens)."
    )


def main() -> None:
    load_local_env()
    args = parse_args()

    api_key = os.getenv("YANDEX_API_KEY", "")
    folder_id = os.getenv("YANDEX_FOLDER_ID", "")
    if not api_key or not folder_id:
        raise SystemExit(
            "Missing YANDEX_API_KEY or YANDEX_FOLDER_ID. "
            "Create .env from .env.example and fill real values."
        )

    models = [
        ("lite", args.model_weak),
        ("non_lite", args.model_medium),
    ]

    rows: list[dict[str, Any]] = []
    for label, model in models:
        payload = build_payload(
            folder_id=folder_id,
            model=model,
            system_prompt=args.system,
            user_prompt=args.prompt,
            temperature=args.temperature,
            max_tokens=args.max_tokens,
        )
        started = time.perf_counter()
        try:
            response = call_yandex_gpt(api_key=api_key, payload=payload)
        except error.HTTPError as exc:
            details = exc.read().decode("utf-8", errors="replace")
            raise SystemExit(f"HTTP {exc.code} for model={model}: {details}") from exc
        except error.URLError as exc:
            raise SystemExit(f"Network error for model={model}: {exc.reason}") from exc
        elapsed_ms = int((time.perf_counter() - started) * 1000)

        text = extract_text(response)
        usage = extract_usage(response)
        estimated_cost = estimate_cost(
            input_tokens=usage["input_tokens"],
            output_tokens=usage["output_tokens"],
            input_price_per_1k=args.price_input_per_1k,
            output_price_per_1k=args.price_output_per_1k,
        )
        quality_hit = contains_expected(text, args.expected)
        rows.append(
            {
                "label": label,
                "model": model,
                "latency_ms": elapsed_ms,
                "input_tokens": usage["input_tokens"],
                "output_tokens": usage["output_tokens"],
                "total_tokens": usage["total_tokens"],
                "estimated_cost": estimated_cost,
                "quality_hit": quality_hit,
                "preview": shorten(first_non_empty_line(text)),
                "text": text,
            }
        )

    print(f"prompt={args.prompt}")
    print(f"temperature={args.temperature} max_tokens={args.max_tokens}")
    print(f"expected={args.expected or '<не задан>'}")
    if args.price_input_per_1k == 0.0 and args.price_output_per_1k == 0.0:
        print("prices=<не заданы, cost будет 0>")
    else:
        print(
            "prices="
            f"in:${args.price_input_per_1k}/1k "
            f"out:${args.price_output_per_1k}/1k"
        )
    for row in rows:
        print_block(f"{row['label']} ({row['model']})", row["text"])

    table_rows: list[list[str]] = []
    for row in rows:
        quality = row["quality_hit"]
        quality_display = "n/a" if quality is None else ("yes" if quality else "no")
        table_rows.append(
            [
                row["label"],
                row["model"],
                str(row["latency_ms"]),
                str(row["input_tokens"]),
                str(row["output_tokens"]),
                str(row["total_tokens"]),
                f"{row['estimated_cost']:.6f}",
                quality_display,
                row["preview"],
            ]
        )

    print_ascii_table(
        title="Сравнение моделей",
        headers=[
            "tier",
            "model",
            "latency_ms",
            "input_tok",
            "output_tok",
            "total_tok",
            "cost_usd",
            "quality",
            "preview",
        ],
        rows=table_rows,
    )
    print("\n=== Короткий вывод ===")
    print(summarize_findings(rows))


if __name__ == "__main__":
    main()
