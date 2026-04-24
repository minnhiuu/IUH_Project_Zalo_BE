from contextvars import ContextVar

from app.security.principal import UserPrincipal

security_principal_ctx: ContextVar[UserPrincipal | None] = ContextVar(
    "security_principal_ctx",
    default=None,
)
