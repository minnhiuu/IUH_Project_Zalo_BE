from pydantic import BaseModel, Field
from typing import Optional

class ChatRequest(BaseModel):
    query: Optional[str] = None
    content: Optional[str] = None  # Support 'content' fallback for compatibility
    conversationId: str = Field(..., alias="conversationId")
    isMention: bool = False  # True khi user @mention AI trong Group conversation

    class Config:
        populate_by_name = True
