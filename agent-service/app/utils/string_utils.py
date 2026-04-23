import re
from app.config.constants import BONDHUB_AI_ID, BONDHUB_AI_NAME


def sanitize_ai_query(text: str) -> str:
    """
    Làm sạch query của user trước khi nạp vào LangGraph:
    1. Xóa dấu @ và tag <mention>Bondhub AI</mention>
    2. Dọn dẹp khoảng trắng dư thừa (đầu/cuối và giữa các từ)

    Ví dụ:
      "@<mention>Bondhub AI</mention>    giải thích về RAG"
      → "giải thích về RAG"
    """
    if not text:
        return ""

    # @? để bắt cả trường hợp có hoặc không có @ phía trước
    pattern = rf"@?<mention>\s*{re.escape(BONDHUB_AI_NAME)}\s*</mention>"
    cleaned = re.sub(pattern, "", text, flags=re.IGNORECASE)

    # ' '.join(split()) chuẩn hóa mọi loại khoảng trắng
    return " ".join(cleaned.split()).strip()
