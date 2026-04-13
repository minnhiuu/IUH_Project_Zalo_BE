"""Pydantic schemas for the recommendation pipeline."""

from pydantic import BaseModel, Field


class FeedItem(BaseModel):
    """A single ranked post in the personalized feed."""

    post_id: str
    author_id: str | None = None
    group_id: str | None = None
    post_type: str | None = None
    caption: str | None = None
    hashtags: list[str] = Field(default_factory=list)
    visibility: str | None = None
    location_name: str | None = None
    media_types: list[str] = Field(default_factory=list)
    music_title: str | None = None
    music_artist: str | None = None
    stats: dict = Field(default_factory=dict)

    # Ranking signals (exposed for transparency / debugging)
    semantic_score: float = 0.0
    social_score: float = 0.0
    popularity_score: float = 0.0
    final_score: float = 0.0


class FeedResponse(BaseModel):
    """Paginated personalized feed response."""

    user_id: str
    items: list[FeedItem]
    total: int
    page: int
    page_size: int
