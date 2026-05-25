from __future__ import annotations

from app.exception.error_code import ErrorCode


class AppException(Exception):
    def __init__(
        self,
        error_code: ErrorCode,
        *,
        errors: dict[str, str] | None = None,
        message_params: dict[str, str] | None = None,
    ):
        super().__init__(error_code.message_key)
        self.error_code = error_code
        self.errors = errors
        self.message_params = message_params or {}
