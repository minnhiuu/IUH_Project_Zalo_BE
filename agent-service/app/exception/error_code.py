from __future__ import annotations

from enum import Enum


class ErrorCode(Enum):
    SYS_UNCATEGORIZED = (500, 9999, "error.sys.uncategorized")
    VALIDATION_ERROR = (400, 2300, "error.validation.error")
    INVALID_REQUEST = (400, 1400, "error.request.invalid")

    CHAT_QUERY_OR_CONTENT_REQUIRED = (400, 4100, "error.chat.query_or_content.required")

    INGEST_CHUNKS_REQUIRED = (400, 4200, "error.ingest.chunks.required")
    INGEST_EXCEL_STRATEGY_DISABLED = (400, 4201, "error.ingest.excel.strategy.disabled")
    INGEST_EXCEL_PARAMS_REQUIRED = (400, 4202, "error.ingest.excel.params.required")
    INGEST_EXCEL_FILETYPE_INVALID = (400, 4203, "error.ingest.excel.filetype.invalid")

    @property
    def http_status(self) -> int:
        return self.value[0]

    @property
    def code(self) -> int:
        return self.value[1]

    @property
    def message_key(self) -> str:
        return self.value[2]
