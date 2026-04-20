from typing import Literal

from pydantic import BaseModel


class ChatStatusEventResponse(BaseModel):
    type: Literal["STATUS"]
    content: str


class ChatAnswerChunkEventResponse(BaseModel):
    type: Literal["ANSWER_CHUNK"]
    content: str


class SummaryStreamEventResponse(BaseModel):
    content: str
