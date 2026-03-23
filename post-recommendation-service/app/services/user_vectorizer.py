from dataclasses import dataclass, field
from datetime import date
from typing import Optional

USER_PROFILE_INSTRUCTION = "Represent this user's agricultural interests for profile matching: "


@dataclass(slots=True)
class UserProfile:
    id: str
    fullName: str = ""
    bio: Optional[str] = ""
    initialInterests: list[str] = field(default_factory=list)
    dob: Optional[date] = None
    gender: Optional[str] = None


def build_baseline_user_persona_text(user: UserProfile) -> str:
    interests_str = ", ".join(user.initialInterests)
    return f"Interests: {interests_str}. Professional Bio: {user.bio or ''}".strip()


def build_baseline_user_document(user: UserProfile) -> str:
    persona_text = build_baseline_user_persona_text(user)
    return f"{USER_PROFILE_INSTRUCTION}{persona_text}".strip()


def generate_baseline_user_vector(user: UserProfile, model) -> list[float]:
    persona_text = build_baseline_user_persona_text(user)
    return model.encode(f"{USER_PROFILE_INSTRUCTION}{persona_text}").tolist()


def _parse_dob(value: object) -> Optional[date]:
    if isinstance(value, date):
        return value
    if isinstance(value, str) and value:
        try:
            return date.fromisoformat(value)
        except ValueError:
            return None
    return None


def user_profile_from_event(event: dict) -> UserProfile:
    return UserProfile(
        id=str(event.get("userId") or event.get("id") or ""),
        fullName=str(event.get("fullName") or ""),
        bio=str(event.get("bio") or ""),
        initialInterests=[str(item) for item in (event.get("initialInterests") or [])],
        dob=_parse_dob(event.get("dob")),
        gender=event.get("gender"),
    )