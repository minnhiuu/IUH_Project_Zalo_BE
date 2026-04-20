from pydantic import BaseModel, Field
from typing import Optional

class SummaryRequest(BaseModel):
    conversationId: str = Field(..., alias="conversationId")
    sinceMessageId: str = Field(..., alias="sinceMessageId")

    class Config:
        populate_by_name = True
