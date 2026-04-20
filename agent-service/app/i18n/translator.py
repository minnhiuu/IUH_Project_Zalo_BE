from __future__ import annotations

import json
from contextvars import ContextVar, Token
from functools import lru_cache
from pathlib import Path

from app.config.app_config import settings

_locale_context: ContextVar[str] = ContextVar("request_locale", default="vi")


def _supported_locales() -> set[str]:
    values = [item.strip().lower() for item in settings.supported_locales.split(",") if item.strip()]
    return set(values or [settings.default_locale.lower()])


@lru_cache(maxsize=8)
def _load_messages(locale: str) -> dict[str, str]:
    messages_dir = Path(__file__).resolve().parent / "messages"
    file_path = messages_dir / f"{locale}.json"
    if not file_path.exists():
        return {}

    with file_path.open("r", encoding="utf-8") as file:
        data = json.load(file)
        if isinstance(data, dict):
            return {str(key): str(value) for key, value in data.items()}
    return {}


def resolve_locale(accept_language: str | None) -> str:
    supported = _supported_locales()
    default_locale = settings.default_locale.lower()

    if not accept_language:
        return default_locale

    for part in accept_language.split(","):
        lang = part.split(";")[0].strip().lower()
        if not lang:
            continue
        base_lang = lang.split("-")[0]
        if base_lang in supported:
            return base_lang

    return default_locale


def set_request_locale(locale: str) -> Token[str]:
    return _locale_context.set(locale)


def reset_request_locale(token: Token[str]) -> None:
    _locale_context.reset(token)


def get_request_locale() -> str:
    return _locale_context.get()


def translate(message_key: str, locale: str | None = None, **kwargs: str | int) -> str:
    locale = (locale or get_request_locale()).lower()
    default_locale = settings.default_locale.lower()

    message = _load_messages(locale).get(message_key)
    if message is None:
        message = _load_messages(default_locale).get(message_key, message_key)

    if not kwargs:
        return message

    try:
        return message.format(**kwargs)
    except (KeyError, ValueError):
        return message
