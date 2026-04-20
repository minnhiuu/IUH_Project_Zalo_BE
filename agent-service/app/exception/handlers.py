from __future__ import annotations

import logging

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.dto.response.api_response import ApiResponse
from app.exception.app_exception import AppException
from app.exception.error_code import ErrorCode
from app.i18n import translate

logger = logging.getLogger(__name__)


def _build_validation_errors(exc: RequestValidationError) -> dict[str, str]:
    errors: dict[str, str] = {}
    for err in exc.errors():
        loc = err.get("loc", ())
        field = str(loc[-1]) if loc else "body"
        errors[field] = str(err.get("msg", "Invalid value"))
    return errors


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppException)
    async def handle_app_exception(_: Request, exc: AppException) -> JSONResponse:
        message = translate(exc.error_code.message_key, **exc.message_params)
        payload = ApiResponse.error(exc.error_code.code, message, exc.errors)
        return JSONResponse(status_code=exc.error_code.http_status, content=payload.model_dump())

    @app.exception_handler(RequestValidationError)
    async def handle_validation_exception(_: Request, exc: RequestValidationError) -> JSONResponse:
        payload = ApiResponse.error(
            ErrorCode.VALIDATION_ERROR.code,
            translate(ErrorCode.VALIDATION_ERROR.message_key),
            _build_validation_errors(exc),
        )
        return JSONResponse(status_code=ErrorCode.VALIDATION_ERROR.http_status, content=payload.model_dump())

    @app.exception_handler(HTTPException)
    async def handle_http_exception(_: Request, exc: HTTPException) -> JSONResponse:
        if isinstance(exc.detail, str):
            message = exc.detail
            errors = None
        elif isinstance(exc.detail, dict):
            message = str(exc.detail.get("message") or translate(ErrorCode.INVALID_REQUEST.message_key))
            errors_raw = exc.detail.get("errors")
            errors = errors_raw if isinstance(errors_raw, dict) else None
        else:
            message = translate(ErrorCode.INVALID_REQUEST.message_key)
            errors = None

        payload = ApiResponse.error(exc.status_code, message, errors)
        return JSONResponse(status_code=exc.status_code, content=payload.model_dump())

    @app.exception_handler(Exception)
    async def handle_uncategorized_exception(_: Request, exc: Exception) -> JSONResponse:
        logger.exception("Unhandled exception", exc_info=exc)
        payload = ApiResponse.error(
            ErrorCode.SYS_UNCATEGORIZED.code,
            translate(ErrorCode.SYS_UNCATEGORIZED.message_key),
            None,
        )
        return JSONResponse(status_code=ErrorCode.SYS_UNCATEGORIZED.http_status, content=payload.model_dump())
