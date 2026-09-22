from pydantic import BaseModel, Field
from typing import Optional, List

class Product(BaseModel):
    id: int = Field(..., description="상품 ID")
    name: str = Field(..., description="상품명")
    description: str = Field(..., description="상품 설명")
    price: int = Field(..., ge=0, description="상품 가격 (원)")
    stock: int = Field(..., ge=0, description="재고 수량")
    category: str = Field(..., description="상품 카테고리")

class ProductListResponse(BaseModel):
    total: int
    items: List[Product]

class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
