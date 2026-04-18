from typing import TypedDict, Annotated, List, Optional
from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages

class AgentState(TypedDict):
    # messages reducer: adds new messages to the existing list
    messages: Annotated[List[BaseMessage], add_messages]
    conversation_id: Optional[str]
    user_id: Optional[str]
    original_query: Optional[str]
    rewritten_query: Optional[str]
    user_query: Optional[str]
    missing_field_info: Optional[str]
    context: Optional[str]
    grade: Optional[str]
    answer: Optional[str]
    retry_count: Optional[int]
