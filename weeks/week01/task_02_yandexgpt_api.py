import argparse
import json
import os
from pathlib import Path
from typing import Any, Optional
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
        description="Call YandexGPT API and experiment with generation parameters."
    )
    parser.add_argument(
        "--prompt",
        type=str,
        default="Привет! Представься и объясни, что ты умеешь.",
        help="User prompt for model.",
    )
    parser.add_argument(
        "--system",
        type=str,
        default="Отвечай кратко и по делу.",
        help="System instruction for model.",
    )
    parser.add_argument(
        "--model",
        type=str,
        default="",
        help="Model name from Yandex, for example yandexgpt-lite or yandexgpt.",
    )
    parser.add_argument(
        "--temperature",
        type=float,
        default=0.5,
        help="Sampling temperature. Typical values: 0.0..1.0.",
    )
    parser.add_argument(
        "--top-p",
        dest="top_p",
        type=float,
        default=None,
        help="Optional nucleus sampling parameter (if supported by API/model).",
    )
    parser.add_argument(
        "--top-k",
        dest="top_k",
        type=int,
        default=None,
        help="Optional top-k sampling parameter (if supported by API/model).",
    )
    parser.add_argument(
        "--max-tokens",
        dest="max_tokens",
        type=int,
        default=300,
        help="Maximum number of output tokens.",
    )
    parser.add_argument(
        "--show-payload",
        action="store_true",
        help="Print request payload for debugging (without API key).",
    )
    return parser.parse_args()


def build_payload(
    folder_id: str,
    model: str,
    system_prompt: str,
    user_prompt: str,
    temperature: float,
    max_tokens: int,
    top_p: Optional[float],
    top_k: Optional[int],
) -> dict[str, Any]:
    completion_options: dict[str, Any] = {
        "stream": False,
        "temperature": temperature,
        "maxTokens": str(max_tokens),
    }
    # Keep extra parameters optional because support may vary by model/API version.
    if top_p is not None:
        completion_options["topP"] = top_p
    if top_k is not None:
        completion_options["topK"] = top_k

    return {
        "modelUri": f"gpt://{folder_id}/{model}/latest",
        "completionOptions": completion_options,
        "messages": [
            {"role": "system", "text": system_prompt},
            {"role": "user", "text": user_prompt},
        ],
    }


def call_yandex_gpt(api_key: str, payload: dict[str, Any]) -> dict[str, Any]:
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

    payload = build_payload(
        folder_id=folder_id,
        model=model,
        system_prompt=args.system,
        user_prompt=args.prompt,
        temperature=args.temperature,
        max_tokens=args.max_tokens,
        top_p=args.top_p,
        top_k=args.top_k,
    )

    if args.show_payload:
        print("Payload:")
        print(json.dumps(payload, ensure_ascii=False, indent=2))

    try:
        response = call_yandex_gpt(api_key=api_key, payload=payload)
    except error.HTTPError as exc:
        details = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {exc.code}: {details}") from exc
    except error.URLError as exc:
        raise SystemExit(f"Network error: {exc.reason}") from exc

    result = response.get("result", {})
    alternatives = result.get("alternatives", [])
    if not alternatives:
        print(json.dumps(response, ensure_ascii=False, indent=2))
        return

    text = alternatives[0].get("message", {}).get("text", "").strip()
    usage = result.get("usage", {})
    model_version = result.get("modelVersion", "unknown")

    print("\n=== Model response ===")
    print(text or "<empty>")
    print("\n=== Meta ===")
    print(f"model={model} version={model_version}")
    print(
        f"temperature={args.temperature} top_p={args.top_p} "
        f"top_k={args.top_k} max_tokens={args.max_tokens}"
    )
    if usage:
        print(f"usage={json.dumps(usage, ensure_ascii=False)}")


if __name__ == "__main__":
    main()
