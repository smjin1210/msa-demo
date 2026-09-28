import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_health_check():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["service"] == "payment-service"

def test_healthz_check():
    response = client.get("/healthz")
    assert response.status_code == 200

def test_process_payment_and_get():
    payload = {
        "order_id": 1001,
        "amount": 25000,
        "payment_method": "credit_card",
        "customer_name": "홍길동"
    }
    response = client.post("/payments", json=payload)
    assert response.status_code == 201
    payment = response.json()
    assert payment["order_id"] == 1001
    assert payment["amount"] == 25000
    assert payment["status"] == "COMPLETED"
    assert "transaction_id" in payment

    # 상세 조회
    payment_id = payment["id"]
    get_res = client.get(f"/payments/{payment_id}")
    assert get_res.status_code == 200
    assert get_res.json()["customer_name"] == "홍길동"

def test_get_payment_not_found():
    response = client.get("/payments/99999")
    assert response.status_code == 404

def test_list_payments():
    response = client.get("/payments")
    assert response.status_code == 200
    assert isinstance(response.json(), list)
