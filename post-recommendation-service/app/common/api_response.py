from typing import Generic, TypeVar

from pydantic import BaseModel

T = TypeVar("T")


class ApiResponse(BaseModel, Generic[T]):
    code: int
    message: str
    data: T | None = None
    errors: dict[str, str] | None = None

    @staticmethod
    def success(data: T) -> "ApiResponse[T]":
        return ApiResponse(code=1000, message="Successful", data=data, errors=None)

    @staticmethod
    def success_without_data() -> "ApiResponse[None]":
        return ApiResponse(code=1000, message="Successful", data=None, errors=None)

    @staticmethod
    def error(
        code: int,
        message: str,
        errors: dict[str, str] | None = None,
    ) -> "ApiResponse[None]":
        return ApiResponse(code=code, message=message, data=None, errors=errors)
