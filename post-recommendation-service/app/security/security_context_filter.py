import logging

from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware

from app.security.principal import UserPrincipal
from app.security.security_context import security_principal_ctx

logger = logging.getLogger(__name__)


class SecurityContextFilter(BaseHTTPMiddleware):
    HEADER_ACCOUNT_ID = "X-Account-Id"
    HEADER_USER_ID = "X-User-Id"
    HEADER_USER_EMAIL = "X-User-Email"
    HEADER_USER_ROLES = "X-User-Roles"
    HEADER_USER_JTI = "X-JWT-Id"
    HEADER_REMAINING_TTL = "X-Remaining-TTL"

    @staticmethod
    def _parse_roles(raw_roles: str | None) -> list[str]:
        if not raw_roles or not raw_roles.strip():
            return ["ROLE_USER"]

        roles: list[str] = []
        for role in raw_roles.split(","):
            normalized = role.strip()
            if not normalized:
                continue
            roles.append(normalized if normalized.startswith("ROLE_") else f"ROLE_{normalized}")
        return roles or ["ROLE_USER"]

    async def dispatch(self, request: Request, call_next):
        token = None
        request.state.user_principal = None

        account_id = request.headers.get(self.HEADER_ACCOUNT_ID)
        email = request.headers.get(self.HEADER_USER_EMAIL)
        if account_id and email:
            try:
                remaining_ttl_raw = request.headers.get(self.HEADER_REMAINING_TTL)
                remaining_ttl = int(remaining_ttl_raw) if remaining_ttl_raw else None

                principal = UserPrincipal(
                    account_id=account_id,
                    user_id=request.headers.get(self.HEADER_USER_ID),
                    email=email,
                    jti=request.headers.get(self.HEADER_USER_JTI),
                    remaining_ttl=remaining_ttl,
                    roles=self._parse_roles(request.headers.get(self.HEADER_USER_ROLES)),
                )
                request.state.user_principal = principal
                token = security_principal_ctx.set(principal)
                logger.debug(
                    "Security context set for email=%s user_id=%s account_id=%s",
                    principal.email,
                    principal.user_id,
                    principal.account_id,
                )
            except Exception:
                logger.exception("Error setting security context")

        try:
            return await call_next(request)
        finally:
            if token is not None:
                security_principal_ctx.reset(token)
