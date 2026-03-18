from pydantic import BaseModel


class UserPrincipal(BaseModel):
    account_id: str
    user_id: str | None = None
    email: str
    jti: str | None = None
    remaining_ttl: int | None = None
    roles: list[str]
