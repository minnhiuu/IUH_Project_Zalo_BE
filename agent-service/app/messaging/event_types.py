from enum import Enum

class KafkaEventType(Enum):
    AI_MESSAGE_SAVE = "AiMessageSaveEvent"
    SOCKET_NOTIFICATION = "SocketEvent"
    USER_LOG = "AuditLogEvent"
    # Thêm các sự kiện mới vào đây cực kỳ dễ dàng
