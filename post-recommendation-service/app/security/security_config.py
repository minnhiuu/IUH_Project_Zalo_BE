import fnmatch

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from app.common.api_response import ApiResponse
from app.core.config import Settings
from app.security.security_context_filter import SecurityContextFilter


class SecurityConfigMiddleware(BaseHTTPMiddleware):
    def __init__(self, app, settings: Settings):
        super().__init__(app)
        self.settings = settings

    @staticmethod
    def _is_internal(path: str) -> bool:
        return "/internal/" in path

    @staticmethod
    def _matches_any(path: str, patterns: list[str]) -> bool:
        for pattern in patterns:
            if fnmatch.fnmatch(path, pattern):
                return True
        return False

    async def dispatch(self, request: Request, call_next):
        path = request.url.path

        if self._is_internal(path):
            return await call_next(request)

        always_public = [
            "/",
            "/error",
            "/actuator",
            "/actuator/*",
            "/docs",
            "/docs/*",
            "/openapi.json",
            "/redoc",
        ]

        if self._matches_any(path, always_public):
            return await call_next(request)

        if self._matches_any(path, self.settings.public_endpoints):
            return await call_next(request)

        if getattr(request.state, "user_principal", None) is not None:
            return await call_next(request)

        return JSONResponse(
            status_code=401,
            content=ApiResponse.error(401, "Unauthorized", None).model_dump(exclude_none=True),
        )


def configure_security(app: FastAPI, settings: Settings) -> None:
    app.add_middleware(SecurityConfigMiddleware, settings=settings)
    app.add_middleware(SecurityContextFilter)
