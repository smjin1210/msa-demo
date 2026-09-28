from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime

class HealthResponse(BaseModel):
    status: str = "healthy"
    service: str = "payment-service"
    version: str

class PaymentRequest(BaseModel):
    order_id: int = Field(..., description="주문 ID")
    amount: int = Field(..., gt=0, description="결제 금액")
    payment_method: str = Field(default="credit_card", description="결제 수단 (credit_card, bank_transfer, point)")
    customer_name: str = Field(..., description="결제자 이름")

class PaymentResponse(BaseModel):
    id: int
    order_id: int
    amount: int
    payment_method: str
    customer_name: str
    status: str = "COMPLETED"
    transaction_id: str
    created_at: datetime
