from __future__ import annotations

from typing import Generic, TypeVar

from pydantic import BaseModel

from app.i18n import translate

T = TypeVar("T")


class ApiResponse(BaseModel, Generic[T]):
    code: int
    message: str
    data: T | None = None
    errors: dict[str, str] | None = None

    @classmethod
    def success(cls, data: T | None = None, message_key: str = "response.success") -> "ApiResponse[T]":
        return cls(code=1000, message=translate(message_key), data=data, errors=None)

    @classmethod
    def success_without_data(cls, message_key: str = "response.success") -> "ApiResponse[None]":
        return cls(code=1000, message=translate(message_key), data=None, errors=None)

    @classmethod
    def error(
        cls,
        code: int,
        message: str,
        errors: dict[str, str] | None = None,
    ) -> "ApiResponse[None]":
        return cls(code=code, message=message, data=None, errors=errors)
