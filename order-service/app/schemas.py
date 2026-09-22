from pydantic import BaseModel, Field
from typing import Optional, List
from datetime import datetime

class OrderCreateRequest(BaseModel):
    product_id: int = Field(..., gt=0, description="상품 ID")
    quantity: int = Field(..., gt=0, description="주문 수량")
    customer_name: str = Field(..., min_length=1, max_length=100, description="주문자 이름")
    notes: Optional[str] = Field(None, max_length=500, description="주문 메모")

class OrderResponse(BaseModel):
    id: int
    product_id: int
    product_name: str
    unit_price: int
    quantity: int
    total_price: int
    customer_name: str
    status: str
    notes: Optional[str] = None
    created_at: datetime

    class Config:
        from_attributes = True

class HealthResponse(BaseModel):
    status: str
    service: str
    database: str
    version: str
