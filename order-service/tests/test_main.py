import pytest
from unittest.mock import AsyncMock, patch
from fastapi import HTTPException

def test_health_check(client):
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["service"] == "order-service"
    assert data["database"] == "connected"

def test_healthz_check(client):
    response = client.get("/healthz")
    assert response.status_code == 200

@patch("app.main.fetch_product", new_callable=AsyncMock)
def test_create_order_success(mock_fetch_product, client):
    # 모의 상품 정보
    mock_fetch_product.return_value = {
        "id": 1,
        "name": "클라우드 네이티브 쿠버네티스 실전 가이드",
        "price": 35000,
        "stock": 50,
        "category": "도서"
    }

    order_payload = {
        "product_id": 1,
        "quantity": 2,
        "customer_name": "홍길동",
        "notes": "빠른 배송 부탁드립니다."
    }

    response = client.post("/orders", json=order_payload)
    assert response.status_code == 201
    data = response.json()
    assert data["id"] is not None
    assert data["product_id"] == 1
    assert data["product_name"] == "클라우드 네이티브 쿠버네티스 실전 가이드"
    assert data["unit_price"] == 35000
    assert data["quantity"] == 2
    assert data["total_price"] == 70000
    assert data["customer_name"] == "홍길동"
    assert data["status"] == "COMPLETED"
    assert data["notes"] == "빠른 배송 부탁드립니다."
    assert "created_at" in data

@patch("app.main.fetch_product", new_callable=AsyncMock)
def test_create_order_insufficient_stock(mock_fetch_product, client):
    mock_fetch_product.return_value = {
        "id": 1,
        "name": "테스트 상품",
        "price": 10000,
        "stock": 3,
        "category": "기타"
    }

    order_payload = {
        "product_id": 1,
        "quantity": 5,
        "customer_name": "이몽룡"
    }

    response = client.post("/orders", json=order_payload)
    assert response.status_code == 400
    assert "재고가 부족합니다" in response.json()["detail"]

@patch("app.main.fetch_product", new_callable=AsyncMock)
def test_create_order_product_not_found(mock_fetch_product, client):
    mock_fetch_product.side_effect = HTTPException(status_code=404, detail="상품을 찾을 수 없습니다.")

    order_payload = {
        "product_id": 9999,
        "quantity": 1,
        "customer_name": "성춘향"
    }

    response = client.post("/orders", json=order_payload)
    assert response.status_code == 404

def test_create_order_invalid_quantity(client):
    order_payload = {
        "product_id": 1,
        "quantity": 0,
        "customer_name": "홍길동"
    }
    response = client.post("/orders", json=order_payload)
    assert response.status_code == 422 or response.status_code == 400

@patch("app.main.fetch_product", new_callable=AsyncMock)
def test_list_and_get_orders(mock_fetch_product, client):
    mock_fetch_product.return_value = {
        "id": 2,
        "name": "TeamCity CI/CD 가이드",
        "price": 40000,
        "stock": 10
    }

    # 1. 주문 생성
    client.post("/orders", json={"product_id": 2, "quantity": 1, "customer_name": "유저1"})
    client.post("/orders", json={"product_id": 2, "quantity": 2, "customer_name": "유저2"})

    # 2. 주문 목록 조회
    list_res = client.get("/orders")
    assert list_res.status_code == 200
    orders = list_res.json()
    assert len(orders) == 2
    assert orders[0]["customer_name"] == "유저2"  # 최신순
    assert orders[1]["customer_name"] == "유저1"

    order_id = orders[0]["id"]

    # 3. 주문 단건 조회
    get_res = client.get(f"/orders/{order_id}")
    assert get_res.status_code == 200
    assert get_res.json()["id"] == order_id
    assert get_res.json()["customer_name"] == "유저2"

    # 4. 없는 주문 조회 시 404
    not_found_res = client.get("/orders/99999")
    assert not_found_res.status_code == 404
