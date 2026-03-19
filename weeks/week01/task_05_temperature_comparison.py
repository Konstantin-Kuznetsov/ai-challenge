import argparse
import json
import os
import re
from pathlib import Path
from typing import Any, Dict, Optional
from urllib import error, request


DEFAULT_PROMPT = (
    "Ты продакт-менеджер AI-стартапа. "
    "За 2 недели и с бюджетом 100 000 рублей нужно повысить удержание пользователей. "
    "Предложи 3 разные гипотезы улучшения продукта, для каждой укажи: "
    "ожидаемый эффект, риск и простую метрику проверки."
)
TEMPERATURES = [0.0, 0.7, 1.0]


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
        description="Compare responses on temperatures 0.0, 0.7 and 1.0."
    )
    parser.add_argument(
        "--prompt",
        type=str,
        default=DEFAULT_PROMPT,
        help="Same prompt will be sent with each temperature.",
    )
    parser.add_argument(
        "--system",
        type=str,
        default="Ты полезный AI-ассистент. Отвечай по делу.",
        help="System prompt.",
    )
    parser.add_argument(
        "--model",
        type=str,
        default="",
        help="Model name. Example: yandexgpt-lite or yandexgpt.",
    )
    parser.add_argument(
        "--max-tokens",
        dest="max_tokens",
        type=int,
        default=450,
        help="Token limit used for each call.",
    )
    parser.add_argument(
        "--max-chars",
        dest="max_chars",
        type=int,
        default=300,
        help="Character limit instruction added to each request.",
    )
    parser.add_argument(
        "--expected",
        type=str,
        default="",
        help="Optional expected fragment for quick accuracy check (e.g. 'Юпитер').",
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


def tokenize_words(text: str) -> list[str]:
    return re.findall(r"[A-Za-zА-Яа-яЁё0-9]+", text.lower())


def lexical_diversity(text: str) -> float:
    words = tokenize_words(text)
    if not words:
        return 0.0
    return len(set(words)) / len(words)


def creativity_score(text: str) -> float:
    """
    Heuristic creativity score in [0, 1]:
    combines lexical diversity + style markers.
    """
    base = lexical_diversity(text)
    markers = 0
    marker_patterns = [
        r"\bнапример\b",
        r"\bпредставь\b",
        r"\bкак будто\b",
        r"\bаналогия\b",
        r"\bидея\b",
        r"!",
    ]
    for pattern in marker_patterns:
        if re.search(pattern, text, flags=re.IGNORECASE):
            markers += 1
    bonus = min(markers * 0.05, 0.25)
    return min(base + bonus, 1.0)


def jaccard_similarity(text_a: str, text_b: str) -> float:
    words_a = set(tokenize_words(text_a))
    words_b = set(tokenize_words(text_b))
    if not words_a and not words_b:
        return 1.0
    union = words_a | words_b
    if not union:
        return 0.0
    return len(words_a & words_b) / len(union)


def contains_expected(text: str, expected: str) -> Optional[bool]:
    expected_value = expected.strip()
    if not expected_value:
        return None
    if expected_value.lower() in text.lower():
        return True
    return False


def first_non_empty_line(text: str) -> str:
    for line in text.splitlines():
        cleaned = line.strip()
        if cleaned:
            return cleaned
    return "<empty>"


def shorten(text: str, limit: int = 70) -> str:
    if len(text) <= limit:
        return text
    return text[: limit - 3] + "..."


def build_limited_prompt(prompt: str, max_chars: int) -> str:
    return (
        f"{prompt}\n\n"
        "Требования к формату ответа:\n"
        f"- Максимум {max_chars} символов.\n"
        "- Если не помещается, сократи ответ, сохранив смысл."
    )


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


def usage_recommendations() -> None:
    print("\n=== Выводы по использованию temperature ===")
    print("- temperature=0.0: фактология, инструкции, повторяемые ответы, минимум случайности.")
    print("- temperature=0.7: универсальный режим, баланс точности и вариативности.")
    print("- temperature=1.0: брейншторм, креативные черновики, больше разнообразия.")


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

    prompt_with_limit = build_limited_prompt(args.prompt, args.max_chars)
    results: Dict[float, str] = {}
    try:
        for temperature in TEMPERATURES:
            payload = build_payload(
                folder_id=folder_id,
                model=model,
                system_prompt=args.system,
                user_prompt=prompt_with_limit,
                temperature=temperature,
                max_tokens=args.max_tokens,
            )
            response = call_yandex_gpt(api_key=api_key, payload=payload)
            results[temperature] = extract_text(response)
    except error.HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {exc.code}: {details}") from exc
    except error.URLError as exc:
        raise SystemExit(f"Network error: {exc.reason}") from exc

    print(f"model={model} max_tokens={args.max_tokens}")
    print(f"prompt={args.prompt}")
    print(f"expected={args.expected or '<не задан>'}")
    print(f"max_chars={args.max_chars}")
    for temperature in TEMPERATURES:
        print_block(f"temperature={temperature}", results[temperature])

    diversity_map: Dict[float, float] = {}
    for temperature in TEMPERATURES:
        similarities = []
        for other in TEMPERATURES:
            if other == temperature:
                continue
            similarities.append(jaccard_similarity(results[temperature], results[other]))
        avg_similarity = sum(similarities) / len(similarities)
        diversity_map[temperature] = 1.0 - avg_similarity

    rows: list[list[str]] = []
    for temperature in TEMPERATURES:
        text = results[temperature]
        accuracy_value = contains_expected(text, args.expected)
        if accuracy_value is None:
            accuracy_display = "n/a"
        else:
            accuracy_display = "yes" if accuracy_value else "no"

        rows.append(
            [
                str(temperature),
                accuracy_display,
                f"{creativity_score(text):.2f}",
                f"{diversity_map[temperature]:.2f}",
                str(len(tokenize_words(text))),
                shorten(first_non_empty_line(text)),
            ]
        )

    print("\n=== Как читать метрики ===")
    print("- creativity: оценка выразительности и вариативности формулировок.")
    print("- diversity: насколько ответ этой температуры отличается от остальных.")
    print_ascii_table(
        title="Сравнение ответов по temperature",
        headers=[
            "temperature",
            "accuracy",
            "creativity",
            "diversity",
            "words",
            "preview",
        ],
        rows=rows,
    )
    usage_recommendations()


if __name__ == "__main__":
    main()
