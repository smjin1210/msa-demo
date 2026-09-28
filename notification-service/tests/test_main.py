import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_health_check():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["service"] == "notification-service"

def test_healthz_check():
    response = client.get("/healthz")
    assert response.status_code == 200

def test_send_notification_and_get():
    payload = {
        "recipient": "customer@example.com",
        "title": "주문 접수 완료",
        "message": "고객님의 주문이 정상 접수되었습니다.",
        "channel": "EMAIL"
    }
    response = client.post("/notifications", json=payload)
    assert response.status_code == 201
    notif = response.json()
    assert notif["recipient"] == "customer@example.com"
    assert notif["title"] == "주문 접수 완료"
    assert notif["status"] == "SENT"

    # 상세 조회
    notif_id = notif["id"]
    get_res = client.get(f"/notifications/{notif_id}")
    assert get_res.status_code == 200
    assert get_res.json()["channel"] == "EMAIL"

def test_get_notification_not_found():
    response = client.get("/notifications/99999")
    assert response.status_code == 404

def test_list_notifications():
    response = client.get("/notifications")
    assert response.status_code == 200
    assert isinstance(response.json(), list)
