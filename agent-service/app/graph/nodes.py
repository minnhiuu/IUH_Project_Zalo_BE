from langchain_openai import ChatOpenAI
from langchain_core.messages import SystemMessage, HumanMessage, get_buffer_string
from app.graph.state import AgentState
from app.graph.prompts import ANALYZER_PROMPT, REWRITER_PROMPT, GRADER_PROMPT, GENERATOR_PROMPT
from app.graph.tools import tools
from app.core.config import settings
from app.integration.qdrant import qdrant_client
from langchain_community.tools.tavily_search import TavilySearchResults
import datetime
import logging

logger = logging.getLogger(__name__)

# Basic model for routing and simple logic
basic_model = ChatOpenAI(model="gpt-4o-mini", api_key=settings.openai_api_key, temperature=0)

# Premium model for generation and tool calling
premium_model = ChatOpenAI(model="gpt-4o", api_key=settings.openai_api_key, temperature=0.7)
premium_with_tools = premium_model.bind_tools(tools)

# Search tool
tavily_tool = TavilySearchResults(max_results=3, tavily_api_key=settings.tavily_api_key)

async def rewrite_node(state: AgentState):
    messages = state.get("messages", [])
    # Convert message history to simple string for the rewriter
    # Exclude the latest message which we will pass as the current task
    history_str = get_buffer_string(messages[:-1]) if len(messages) > 1 else "No history."
    
    sys_msg = SystemMessage(content=REWRITER_PROMPT.format(conversation_history=history_str))
    
    # current_msg is either the original query or the supplementary info from user
    current_msg = state.get("user_query") or state.get("original_query", "")
    
    # If it's a follow-up, give additional hint to the model
    if state.get("missing_field_info"):
        current_msg = f"Tôi đang trả lời cho câu hỏi '{state['missing_field_info']}'. Câu trả lời của tôi: {current_msg}"
    
    logger.info("--- STARTING QUERY REWRITE ---")
    logger.info(f"Input Context: {current_msg}")
    
    # Upgrade to premium_model for better accuracy in context merging
    result = await premium_model.ainvoke([sys_msg, HumanMessage(content=current_msg)])
    rewritten = result.content.strip()
    
    logger.info(f"Final Rewritten Query: {rewritten}")
    logger.info("--- QUERY REWRITE COMPLETED ---")
    
    return {"rewritten_query": rewritten}

async def analyze_node(state: AgentState):
    curr_time = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    # ƯU TIÊN sử dụng rewritten_query (đã được context-aware ở bước trước)
    user_msg = state.get("rewritten_query") or state.get("user_query") or state.get("original_query", "")
    missing_info = state.get("missing_field_info")

    # --- NHIỆM VỤ 2: checkIntentSwitch (Nếu đang trong luồng làm rõ) ---
    if missing_info:
        from app.graph.prompts import CHECK_INTENT_SWITCH_PROMPT
        switch_msg = SystemMessage(content=CHECK_INTENT_SWITCH_PROMPT.format(
            last_clarification=missing_info,
            current_time=curr_time,
            user_message=state.get("user_query", "") # Ở đây dùng tin nhắn gốc để check đổi ý
        ))
        switch_result = await premium_model.ainvoke([switch_msg])
        decision = switch_result.content.strip().upper()
        logger.info(f"Intent switch decision: {decision}")

        if "NEW_INTENT" in decision:
            logger.info("User switched intent. Resetting slot-filling context.")
            # Nếu đổi ý, ta dùng tin nhắn gốc để phân tích lại từ đầu
            user_msg = state.get("user_query") or state.get("original_query", "")
            missing_info = None 

    # --- NHIỆM VỤ 1: analyzeAndRoute (Phân loại chính) ---
    sys_msg = SystemMessage(content=ANALYZER_PROMPT.format(current_time=curr_time))
    
    # Gọi premium_model để phân loại token chính xác hơn (Dùng user_msg đã được xử lý)
    result = await premium_model.ainvoke([sys_msg, HumanMessage(content=user_msg)])
    route = result.content.strip()
    logger.info(f"Router input: {user_msg} | Decision: {route}")
    
    # Cập nhật state: xóa missing_field_info nếu route là COMPLETE hoặc DIRECT
    updates = {"grade": route} # 'grade' ở đây đóng vai trò là 'route' như code cũ của Huy
    if not route.startswith("MISSING:"):
        updates["missing_field_info"] = ""
    else:
        # Lưu lại câu hỏi làm rõ nếu route bắt đầu bằng MISSING:
        updates["missing_field_info"] = route.split("MISSING:")[1] if "MISSING:" in route else route
        
    return updates

async def clarify_node(state: AgentState):
    route = state.get("grade", "")
    missing_field = route.split("MISSING:")[1] if "MISSING:" in route else "thông tin"
    return {"answer": f"Vui lòng cung cấp thêm về {missing_field}"}

async def retrieve_node(state: AgentState):
    query = state.get("rewritten_query", "")
    conversation_id = state.get("conversation_id", "")
    
    if not query or not conversation_id:
        return {"context": ""}
        
    logger.info(f"--- RETRIEVING from Qdrant: {query} (chat_id: {conversation_id}) ---")
    from app.integration.qdrant import search_similar
    context = await search_similar(query, conversation_id)
    
    if context:
        logger.info(f"--- RETRIEVAL SUCCESS: Found relevant context ---")
    else:
        logger.info(f"--- RETRIEVAL EMPTY ---")
        
    return {"context": context or ""}

async def grade_node(state: AgentState):
    context = state.get("context", "")
    if not context or context.strip() == "":
        logger.info("--- GRADING: Context is empty. Forcing INCORRECT for Web Search fallback ---")
        return {"grade": "INCORRECT"}
        
    sys_msg = SystemMessage(content=GRADER_PROMPT)
    user_query_context = f"Context: {context} | Query: {state.get('rewritten_query')}"
    
    # Use premium model for grading to be more strict
    result = await premium_model.ainvoke([sys_msg, HumanMessage(content=user_query_context)])
    grade = result.content.strip().upper()
    logger.info(f"--- GRADER DECISION: {grade} ---")
    
    return {"grade": grade}

async def web_search_node(state: AgentState):
    query = state.get("rewritten_query", "")
    search_results = await tavily_tool.ainvoke({"query": query})
    retries = (state.get("retry_count") or 0) + 1
    
    new_context = (state.get("context") or "") + f"\n\n[Dữ liệu từ Internet]:\n{search_results}"
    logger.info(f"--- WEB SEARCHING ---")
    logger.info(f"Context: {new_context}")
    return {"context": new_context, "retry_count": retries}

async def mark_low_confidence_node(state: AgentState):
    new_context = (state.get("context") or "") + "\n\n[Lưu ý]: Dữ liệu có độ tin cậy thấp hoặc không tìm thấy thông tin chính xác."
    return {"context": new_context}

async def generate_node(state: AgentState):
    curr_time = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    context = state.get("context") or ""
    
    sys_msg = SystemMessage(content=GENERATOR_PROMPT.format(current_time=curr_time, context=context))
    
    # We use messages from state to maintain conversation context for tool calling
    messages = state.get("messages", [])
    if not messages:
        messages = [HumanMessage(content=state.get("rewritten_query", ""))]
    
    # Invoke premium model with tools
    # Important: premium_with_tools will decide whether to call tools or generate final answer
    result = await premium_with_tools.ainvoke([sys_msg] + messages)
    
    return {"messages": [result], "answer": result.content}
