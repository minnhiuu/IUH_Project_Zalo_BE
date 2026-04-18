import httpx
from typing import Optional

client: Optional[httpx.AsyncClient] = None

async def init_http_client():
    global client
    if client is None:
        client = httpx.AsyncClient(
            timeout=httpx.Timeout(10.0, connect=5.0),
            limits=httpx.Limits(max_connections=100, max_keepalive_connections=20)
        )

async def stop_http_client():
    global client
    if client is not None:
        await client.aclose()
        client = None

def get_client() -> httpx.AsyncClient:
    if client is None:
        raise RuntimeError("HTTP client is not initialized. Call init_http_client first.")
    return client
