from starlette.testclient import TestClient

from app.main import app


def test_health_endpoint() -> None:
    client = TestClient(app)
    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 1000
    assert body["message"] == "Successful"
    assert body["data"]["status"] == "ok"


def test_protected_route_requires_security_context_headers() -> None:
    client = TestClient(app)
    response = client.get("/not-public")

    assert response.status_code == 401
    body = response.json()
    assert body["code"] == 401
    assert body["message"] == "Unauthorized"
