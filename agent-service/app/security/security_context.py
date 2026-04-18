from fastapi import Request, HTTPException, Depends
from contextvars import ContextVar
from typing import Dict, Any, Optional

# Global context variable for user information
user_context: ContextVar[Dict[str, Any]] = ContextVar("user_context", default={})

async def security_context_dependency(request: Request):
    account_id = request.headers.get("X-Account-Id")
    user_id = request.headers.get("X-User-Id")
    
    if not account_id or not user_id:
        # For development/testing, you might want to allow missing headers or log a warning
        # raise HTTPException(status_code=401, detail="Missing Security Context Headers")
        pass
        
    auth_header = request.headers.get("Authorization", "")
    token = auth_header.replace("Bearer ", "") if auth_header.startswith("Bearer ") else auth_header

    user_info = {
        "account_id": account_id,
        "user_id": user_id,
        "email": request.headers.get("X-User-Email"),
        "roles": request.headers.get("X-User-Roles", "").split(",") if request.headers.get("X-User-Roles") else [],
        "raw_token": token
    }
    
    token_root = user_context.set(user_info)
    try:
        yield user_info
    finally:
        user_context.reset(token_root)

def get_user_context() -> Dict[str, Any]:
    return user_context.get()
