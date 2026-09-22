import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_health_check():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["service"] == "product-service"

def test_healthz_check():
    response = client.get("/healthz")
    assert response.status_code == 200

def test_get_products():
    response = client.get("/products")
    assert response.status_code == 200
    products = response.json()
    assert isinstance(products, list)
    assert len(products) >= 5
    assert products[0]["id"] == 1
    assert "name" in products[0]
    assert "price" in products[0]

def test_get_products_with_filter():
    response = client.get("/products?category=도서")
    assert response.status_code == 200
    products = response.json()
    assert all(p["category"] == "도서" for p in products)

def test_get_product_detail_success():
    response = client.get("/products/1")
    assert response.status_code == 200
    product = response.json()
    assert product["id"] == 1
    assert product["name"] == "클라우드 네이티브 쿠버네티스 실전 가이드"
    assert product["price"] == 35000

def test_get_product_detail_not_found():
    response = client.get("/products/9999")
    assert response.status_code == 404
    data = response.json()
    assert "detail" in data
