from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.common.api_response import ApiResponse


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(_: Request, exc: RequestValidationError):
        field_errors: dict[str, str] = {}
        for err in exc.errors():
            field = ".".join([str(item) for item in err.get("loc", []) if item != "body"]) or "request"
            field_errors[field] = err.get("msg", "Invalid value")

        return JSONResponse(
            status_code=400,
            content=ApiResponse.error(2300, "Validation error", field_errors).model_dump(exclude_none=True),
        )

    @app.exception_handler(Exception)
    async def generic_exception_handler(_: Request, __: Exception):
        return JSONResponse(
            status_code=500,
            content=ApiResponse.error(9999, "Internal server error", None).model_dump(exclude_none=True),
        )
