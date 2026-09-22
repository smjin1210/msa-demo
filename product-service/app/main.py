from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from typing import List, Optional

from app.models import Product, ProductListResponse, HealthResponse
from app.data import get_all_products, get_product_by_id
from app import __version__

app = FastAPI(
    title="Product Service",
    description="MSA 주문 시스템 - 상품 관리 마이크로서비스",
    version=__version__,
)

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health", response_model=HealthResponse, tags=["Health"])
@app.get("/healthz", response_model=HealthResponse, tags=["Health"])
def health_check():
    """헬스 체크 엔드포인트"""
    return HealthResponse(
        status="healthy",
        service="product-service",
        version=__version__
    )

@app.get("/products", response_model=List[Product], tags=["Products"])
@app.get("/api/products", response_model=List[Product], tags=["Products"], include_in_schema=False)
def list_products(category: Optional[str] = None):
    """상품 목록 조회"""
    products = get_all_products()
    if category:
        products = [p for p in products if p.category == category]
    return products

@app.get("/products/{product_id}", response_model=Product, tags=["Products"])
@app.get("/api/products/{product_id}", response_model=Product, tags=["Products"], include_in_schema=False)
def get_product(product_id: int):
    """상품 상세 조회"""
    product = get_product_by_id(product_id)
    if not product:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"상품 ID {product_id}를 찾을 수 없습니다."
        )
    return product

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
