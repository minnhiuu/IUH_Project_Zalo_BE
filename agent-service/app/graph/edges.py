import logging
from app.graph.state import AgentState
from app.core.config import settings
from langgraph.graph import END

logger = logging.getLogger(__name__)

# Node names
NODE_REWRITE = "rewrite"
NODE_ANALYZE = "analyze"
NODE_CLARIFY = "clarify"
NODE_RETRIEVE = "retrieve"
NODE_GRADE = "grade_context"
NODE_GENERATE = "generate"
NODE_WEB_SEARCH = "web_search"
NODE_MARK_LOW_CONFIDENCE = "mark_low_confidence"
NODE_ACTION = "action"

def next_after_analyze(state: AgentState) -> str:
    route = str(state.get("grade", "")).strip().upper()
    target = NODE_GENERATE
    
    if route.startswith("MISSING:"):
        target = NODE_CLARIFY
    elif route == "COMPLETE":
        target = NODE_RETRIEVE
    
    logger.info(f"--- ROUTING DECISION: {route} ---> Next Node: {target}")
    return target

def next_after_grade(state: AgentState) -> str:
    grade = str(state.get("grade", "")).strip().upper()
    target = NODE_WEB_SEARCH
    
    if grade == "CORRECT":
        target = NODE_GENERATE
    else:
        retry_count = state.get("retry_count", 0)
        retry_limit = max(0, settings.max_web_retries)
        if retry_count >= retry_limit:
            target = NODE_MARK_LOW_CONFIDENCE
    
    logger.info(f"--- GRADING RESULT: {grade} ---> Next Node: {target}")
    return target

def should_continue(state: AgentState):
    """Router for tool calling loop."""
    messages = state.get("messages", [])
    if not messages:
        return END

    last_message = messages[-1]
    if hasattr(last_message, "tool_calls") and last_message.tool_calls:
        return NODE_ACTION
    return END
