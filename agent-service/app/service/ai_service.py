from langchain_openai import ChatOpenAI
from langchain_core.runnables import RunnableConfig
from langchain_core.messages import SystemMessage, HumanMessage, AIMessage, get_buffer_string
from app.model.agent_state import AgentState
from app.graph.prompts import ANALYZER_PROMPT, REWRITER_PROMPT, GRADER_PROMPT, GENERATOR_PROMPT, SUMMARIZER_PROMPT
from app.graph.tools import tools
from app.config.app_config import settings
from app.client.qdrant_client import qdrant_client
from app.client.message_client import get_messages_since
from langchain_community.tools.tavily_search import TavilySearchResults
import datetime
import logging

logger = logging.getLogger(__name__)

# Basic model for routing and simple logic
basic_model = ChatOpenAI(model="gpt-4o-mini", api_key=settings.openai_api_key, temperature=0)

# Premium model for generation and tool calling
premium_model = ChatOpenAI(model="gpt-4o", api_key=settings.openai_api_key, temperature=0.7, streaming=True)
premium_with_tools = premium_model.bind_tools(tools)

# Search tool
tavily_tool = TavilySearchResults(max_results=3, tavily_api_key=settings.tavily_api_key)

async def rewrite_node(state: AgentState):
    messages = state.get("messages", [])
    # Limit message context based on settings
    limit = settings.chat_history_limit
    recent_messages = messages[-(limit+1):-1] if len(messages) > 1 else []
    history_str = get_buffer_string(recent_messages) if recent_messages else "No history."
    
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
    return {"answer": f"<question>{missing_field}</question>"}

async def retrieve_node(state: AgentState):
    query = state.get("rewritten_query", "")
    conversation_id = state.get("conversation_id", "")
    
    if not query or not conversation_id:
        return {"context": ""}
        
    logger.info(f"--- RETRIEVING from Qdrant: {query} (chat_id: {conversation_id}) ---")
    from app.client.qdrant_client import search_similar
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
    logger.info(f"--- [WEB SEARCH] Starting search for: {query} ---")
    
    search_results = await tavily_tool.ainvoke({"query": query})
    logger.info(f"--- [WEB SEARCH] Raw Results Snippet: {repr(str(search_results)[:200])} ---")
    
    retries = (state.get("retry_count") or 0) + 1
    new_context = (state.get("context") or "") + f"\n\n[Dữ liệu từ Internet]:\n{search_results}"
    logger.info(f"--- WEB SEARCHING ---")
    logger.info(f"Context: {new_context}")
    return {"context": new_context, "retry_count": retries}

async def mark_low_confidence_node(state: AgentState):
    new_context = (state.get("context") or "") + "\n\n[Lưu ý]: Dữ liệu có độ tin cậy thấp hoặc không tìm thấy thông tin chính xác."
    return {"context": new_context}

async def generate_node(state: AgentState, config: RunnableConfig):
    curr_time = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    context = state.get("context") or ""
    
    sys_msg = SystemMessage(content=GENERATOR_PROMPT.format(current_time=curr_time, context=context))
    
    # We use dynamic limit to maintain context window and optimize costs
    messages = state.get("messages", [])
    if not messages:
        messages = [HumanMessage(content=state.get("rewritten_query", ""))]
    else:
        messages = messages[-settings.chat_history_limit:]
    
    # Quan trọng: Truyền config vào ainvoke để astream_events(v2) có thể 
    # capturing được các event on_chat_model_stream từ bên trong.
    result = await premium_with_tools.ainvoke([sys_msg] + messages, config=config)
    
    return {"messages": [result], "answer": result.content}

async def summarize_node(state: AgentState):
    logger.info("--- AI INTENT: SUMMARIZING CONVERSATION ---")
    
    # 1. Lấy context đã được Controller nạp sẵn
    history_text = state.get("chat_history_context", "")
    
    if not history_text or history_text.strip() == "":
        return {"answer": "Tôi không tìm thấy lịch sử hội thoại gần đây để tóm tắt cho bạn.", "grade": "COMPLETE"}

    # 2. Sử dụng SUMMARIZER_PROMPT hiện có
    prompt = SUMMARIZER_PROMPT.format(history=history_text)
    
    # 3. Gọi model để sinh tóm tắt
    # Ở đây có thể dùng astream nếu muốn stream kết quả, 
    # nhưng để đơn giản và ổn định cho node tổng hợp ta dùng invoke.
    result = await premium_model.ainvoke([SystemMessage(content=prompt)])
    
    return {"answer": result.content, "grade": "COMPLETE"}

#Legacy
async def summarize_messages(conversation_id: str, since_message_id: str):
    # 1. Fetch raw messages from Java service
    raw_messages = await get_messages_since(conversation_id, since_message_id)
    
    if not raw_messages:
        return "Không tìm thấy nội dung để tóm tắt."

    # 2. Data Cleaning & Mapping
    clean_messages = []
    for m in raw_messages:
        content = m.get("content")
        msg_type = m.get("type")
        
        # Keep CHAT messages
        if msg_type == "CHAT" and content:
            clean_messages.append(m)
        # Convert MEDIA markers so AI understands context
        elif msg_type in ["IMAGE", "VIDEO", "FILE"]:
            m["content"] = f"[{msg_type.capitalize()}]"
            clean_messages.append(m)
    
    if not clean_messages:
        return "Nội dung cuộc hội thoại chỉ bao gồm sticker hoặc tin nhắn hệ thống, không đủ dữ liệu để tóm tắt."

    # 3. Format history for LLM: [HH:mm] **Sender**: Content
    history_text = "\n".join([
        f"[{m['createdAt'][11:16]}] **{m['senderName']}**: {m.get('content', '')}" 
        for m in clean_messages
    ])
    
    # 4. Invoke LLM for summary
    prompt = SUMMARIZER_PROMPT.format(history=history_text)
    logger.info(f"--- GENERATING CONVERSATION SUMMARY for {conversation_id}: {history_text} ---")
    response = await premium_model.ainvoke([SystemMessage(content=prompt)])
    
    return response.content

#TODO: using i18n instead of hardcode string
async def summarize_messages_stream(conversation_id: str, since_message_id: str):
    # 1. Fetch raw messages (reuse logic)
    raw_messages = await get_messages_since(conversation_id, since_message_id)
    if not raw_messages:
        yield "Không tìm thấy nội dung để tóm tắt."
        return

    clean_messages = []
    for m in raw_messages:
        content = m.get("content")
        msg_type = m.get("type")
        if msg_type == "CHAT" and content:
            clean_messages.append(m)
        elif msg_type in ["IMAGE", "VIDEO", "FILE"]:
            m["content"] = f"[{msg_type.capitalize()}]"
            clean_messages.append(m)
    
    if not clean_messages:
        yield "Nội dung không đủ dữ liệu để tóm tắt."
        return

    history_text = "\n".join([
        f"[{m['createdAt'][11:16]}] **{m['senderName']}**: {m.get('content', '')}" 
        for m in clean_messages
    ])
    
    prompt = SUMMARIZER_PROMPT.format(history=history_text)
    logger.info(f"--- STARTING STREAMING SUMMARY for {conversation_id} ---")
    
    # Stream from LLM
    async for chunk in premium_model.astream([SystemMessage(content=prompt)]):
        if chunk.content:
            yield chunk.content
