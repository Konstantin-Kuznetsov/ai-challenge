import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Optional
from urllib import error, request


def load_local_env() -> None:
    """Load variables from project .env if they are missing in os.environ."""
    project_root = Path(__file__).resolve().parents[3]
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


@dataclass
class YandexLLMAgent:
    api_key: str
    folder_id: str
    model: str = "yandexgpt-lite"
    default_system_prompt: str = (
        "Ты senior Product Manager AI-стартапа. "
        "Давай практичные и измеримые рекомендации, учитывай ограничения по срокам "
        "и ресурсам, выделяй риски и компромиссы. "
        "Структурируй ответ: 1) гипотеза/идея, 2) ожидаемый эффект, "
        "3) риск, 4) метрика проверки. "
        "Пиши кратко, по делу, без воды."
    )
    temperature: float = 0.4
    max_tokens: int = 350

    def ask(
        self,
        user_text: str,
        history_messages: Optional[list[dict[str, str]]] = None,
        system_prompt: Optional[str] = None,
        api_key_override: Optional[str] = None,
    ) -> dict[str, Any]:
        if not user_text.strip():
            return {
                "ok": False,
                "text": "Пустой запрос. Введите сообщение.",
                "usage": {"input_tokens": 0, "output_tokens": 0, "total_tokens": 0},
            }

        chosen_system_prompt = system_prompt or self.default_system_prompt
        payload = self._build_payload(
            system_prompt=chosen_system_prompt,
            history_messages=history_messages or [],
            user_prompt=user_text,
        )

        try:
            response = self._call_api(payload, api_key_override=api_key_override)
        except error.HTTPError as exc:
            details = exc.read().decode("utf-8", errors="replace")
            return {
                "ok": False,
                "text": f"HTTP {exc.code}: {details}",
                "usage": {"input_tokens": 0, "output_tokens": 0, "total_tokens": 0},
            }
        except error.URLError as exc:
            return {
                "ok": False,
                "text": f"Network error: {exc.reason}",
                "usage": {"input_tokens": 0, "output_tokens": 0, "total_tokens": 0},
            }

        return {
            "ok": True,
            "text": self._extract_text(response),
            "usage": self._extract_usage(response),
        }

    def _build_payload(
        self,
        system_prompt: str,
        history_messages: list[dict[str, str]],
        user_prompt: str,
    ) -> dict[str, Any]:
        messages: list[dict[str, str]] = [{"role": "system", "text": system_prompt}]
        for item in history_messages:
            role = item.get("role", "")
            content = item.get("content", "")
            if role in {"user", "assistant"} and content:
                messages.append({"role": role, "text": content})
        messages.append({"role": "user", "text": user_prompt})

        return {
            "modelUri": f"gpt://{self.folder_id}/{self.model}/latest",
            "completionOptions": {
                "stream": False,
                "temperature": self.temperature,
                "maxTokens": str(self.max_tokens),
            },
            "messages": messages,
        }

    def _call_api(
        self, payload: dict[str, Any], api_key_override: Optional[str] = None
    ) -> dict[str, Any]:
        url = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
        data = json.dumps(payload).encode("utf-8")
        auth_api_key = api_key_override or self.api_key
        req = request.Request(
            url=url,
            data=data,
            method="POST",
            headers={
                "Authorization": f"Api-Key {auth_api_key}",
                "Content-Type": "application/json",
            },
        )
        with request.urlopen(req, timeout=60) as response:
            body = response.read().decode("utf-8")
        return json.loads(body)

    @staticmethod
    def _extract_text(response: dict[str, Any]) -> str:
        alternatives = response.get("result", {}).get("alternatives", [])
        if not alternatives:
            return json.dumps(response, ensure_ascii=False)
        return alternatives[0].get("message", {}).get("text", "").strip()

    @staticmethod
    def _extract_usage(response: dict[str, Any]) -> dict[str, int]:
        usage = response.get("result", {}).get("usage", {})
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

