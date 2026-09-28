from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from typing import List, Dict
from datetime import datetime
import uuid

from app.models import HealthResponse, PaymentRequest, PaymentResponse
from app import __version__

app = FastAPI(
    title="Payment Service",
    description="MSA 주문 시스템 - 결제 처리 마이크로서비스",
    version=__version__,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 인메모리 결제 저장소
_payments_db: Dict[int, PaymentResponse] = {}
_payment_counter = 1

@app.get("/health", response_model=HealthResponse, tags=["Health"])
@app.get("/healthz", response_model=HealthResponse, tags=["Health"])
def health_check():
    """헬스 체크 엔드포인트"""
    return HealthResponse(
        status="healthy",
        service="payment-service",
        version=__version__
    )

@app.get("/payments", response_model=List[PaymentResponse], tags=["Payments"])
@app.get("/api/payments", response_model=List[PaymentResponse], tags=["Payments"], include_in_schema=False)
def list_payments():
    """결제 내역 전체 조회"""
    return list(_payments_db.values())

@app.post("/payments", response_model=PaymentResponse, status_code=status.HTTP_201_CREATED, tags=["Payments"])
@app.post("/api/payments", response_model=PaymentResponse, status_code=status.HTTP_201_CREATED, tags=["Payments"], include_in_schema=False)
def process_payment(req: PaymentRequest):
    """결제 승인 처리"""
    global _payment_counter
    payment_id = _payment_counter
    _payment_counter += 1

    payment = PaymentResponse(
        id=payment_id,
        order_id=req.order_id,
        amount=req.amount,
        payment_method=req.payment_method,
        customer_name=req.customer_name,
        status="COMPLETED",
        transaction_id=f"tx_{uuid.uuid4().hex[:12]}",
        created_at=datetime.utcnow(),
    )
    _payments_db[payment_id] = payment
    return payment

@app.get("/payments/{payment_id}", response_model=PaymentResponse, tags=["Payments"])
@app.get("/api/payments/{payment_id}", response_model=PaymentResponse, tags=["Payments"], include_in_schema=False)
def get_payment(payment_id: int):
    """결제 상세 조회"""
    payment = _payments_db.get(payment_id)
    if not payment:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"결제 ID {payment_id}를 찾을 수 없습니다."
        )
    return payment

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8003, reload=True)
