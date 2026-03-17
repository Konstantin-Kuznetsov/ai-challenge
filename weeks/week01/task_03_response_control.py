import argparse
import json
import os
from pathlib import Path
from typing import Any, Dict, Optional
from urllib import error, request


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
        description="Compare unrestricted and controlled responses from YandexGPT."
    )
    parser.add_argument(
        "--prompt",
        type=str,
        default="Объясни принцип работы трансформеров простыми словами.",
        help="The same prompt will be sent in both modes.",
    )
    parser.add_argument(
        "--system",
        type=str,
        default="Ты полезный AI-ассистент. Отвечай понятно.",
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
        default=0.5,
        help="Sampling temperature.",
    )
    parser.add_argument(
        "--max-tokens",
        dest="max_tokens",
        type=int,
        default=400,
        help="Token limit used for both requests.",
    )
    parser.add_argument(
        "--format-instruction",
        type=str,
        default='JSON с ключами "summary" и "steps".',
        help="Explicit output format instruction for controlled response.",
    )
    parser.add_argument(
        "--max-chars",
        type=int,
        default=500,
        help="Character limit instruction for controlled response.",
    )
    parser.add_argument(
        "--end-marker",
        type=str,
        default="<END>",
        help="End marker instruction for controlled response.",
    )
    parser.add_argument(
        "--use-stop-sequence",
        action="store_true",
        help="Try stop sequence API field with end marker for controlled call.",
    )
    return parser.parse_args()


def build_payload(
    folder_id: str,
    model: str,
    system_prompt: str,
    user_prompt: str,
    temperature: float,
    max_tokens: int,
    stop_sequence: Optional[str] = None,
) -> Dict[str, Any]:
    completion_options: Dict[str, Any] = {
        "stream": False,
        "temperature": temperature,
        "maxTokens": str(max_tokens),
    }
    if stop_sequence:
        completion_options["stopSequences"] = [stop_sequence]

    return {
        "modelUri": f"gpt://{folder_id}/{model}/latest",
        "completionOptions": completion_options,
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


def build_controlled_prompt(
    base_prompt: str,
    format_instruction: str,
    max_chars: int,
    end_marker: str,
) -> str:
    return (
        f"{base_prompt}\n\n"
        "Требования к ответу:\n"
        f"1) Формат ответа: {format_instruction}\n"
        f"2) Длина ответа: не более {max_chars} символов.\n"
        f"3) Условие завершения: закончи ответ отдельной строкой {end_marker}\n"
        "Если не помещается, сократи ответ, но соблюдай формат."
    )


def print_section(title: str, text: str) -> None:
    print(f"\n=== {title} ===")
    print(text or "<empty>")
    print(f"\n[chars={len(text)}]")


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

    base_payload = build_payload(
        folder_id=folder_id,
        model=model,
        system_prompt=args.system,
        user_prompt=args.prompt,
        temperature=args.temperature,
        max_tokens=args.max_tokens,
    )

    controlled_prompt = build_controlled_prompt(
        base_prompt=args.prompt,
        format_instruction=args.format_instruction,
        max_chars=args.max_chars,
        end_marker=args.end_marker,
    )
    controlled_payload = build_payload(
        folder_id=folder_id,
        model=model,
        system_prompt=args.system,
        user_prompt=controlled_prompt,
        temperature=args.temperature,
        max_tokens=args.max_tokens,
        stop_sequence=args.end_marker if args.use_stop_sequence else None,
    )

    try:
        response_base = call_yandex_gpt(api_key=api_key, payload=base_payload)
        response_controlled = call_yandex_gpt(
            api_key=api_key, payload=controlled_payload
        )
    except error.HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")
        if args.use_stop_sequence and "stopSequences" in details:
            raise SystemExit(
                "API rejected stop sequence field. "
                "Retry without --use-stop-sequence.\n"
                f"Details: {details}"
            ) from exc
        raise SystemExit(f"HTTP {exc.code}: {details}") from exc
    except error.URLError as exc:
        raise SystemExit(f"Network error: {exc.reason}") from exc

    base_text = extract_text(response_base)
    controlled_text = extract_text(response_controlled)

    print(f"model={model} temperature={args.temperature} max_tokens={args.max_tokens}")
    print(f"prompt={args.prompt}")
    print_section("Без ограничений", base_text)
    print_section("С ограничениями", controlled_text)

    marker_ok = args.end_marker in controlled_text
    print("\n=== Проверка контроля ===")
    print(
        "controlled_has_end_marker="
        f"{marker_ok} marker={args.end_marker!r} "
        f"max_chars_rule={args.max_chars}"
    )


if __name__ == "__main__":
    main()
