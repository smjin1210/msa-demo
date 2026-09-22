from fastapi import FastAPI, Depends, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
from sqlalchemy import text, desc
from contextlib import asynccontextmanager
from typing import List
import logging

from app.config import settings
from app.database import get_db, init_db, engine
from app.models import Order
from app.schemas import OrderCreateRequest, OrderResponse, HealthResponse
from app.client import fetch_product
from app import __version__

logger = logging.getLogger("order-service")

@asynccontextmanager
async def lifespan(app: FastAPI):
    # 스타트업 로직: 데이터베이스 초기화
    init_db()
    yield

app = FastAPI(
    title="Order Service",
    description="MSA 주문 시스템 - 주문 관리 및 처리 마이크로서비스",
    version=__version__,
    lifespan=lifespan
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
def health_check(db: Session = Depends(get_db)):
    """헬스 체크 엔드포인트 - DB 연결 상태 확인"""
    db_status = "connected"
    try:
        db.execute(text("SELECT 1"))
    except Exception as e:
        logger.error(f"Health check DB ping failed: {e}")
        db_status = f"error: {str(e)}"
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"데이터베이스 연결 실패: {e}"
        )

    return HealthResponse(
        status="healthy",
        service="order-service",
        database=db_status,
        version=__version__
    )

@app.get("/orders", response_model=List[OrderResponse], tags=["Orders"])
@app.get("/api/orders", response_model=List[OrderResponse], tags=["Orders"], include_in_schema=False)
def list_orders(db: Session = Depends(get_db)):
    """주문 목록 조회 (최신순 정렬)"""
    orders = db.query(Order).order_by(desc(Order.created_at), desc(Order.id)).all()
    return orders

@app.get("/orders/{order_id}", response_model=OrderResponse, tags=["Orders"])
@app.get("/api/orders/{order_id}", response_model=OrderResponse, tags=["Orders"], include_in_schema=False)
def get_order(order_id: int, db: Session = Depends(get_db)):
    """주문 단건 상세 조회"""
    order = db.query(Order).filter(Order.id == order_id).first()
    if not order:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"주문 ID {order_id}를 찾을 수 없습니다."
        )
    return order

@app.post("/orders", response_model=OrderResponse, status_code=status.HTTP_201_CREATED, tags=["Orders"])
@app.post("/api/orders", response_model=OrderResponse, status_code=status.HTTP_201_CREATED, tags=["Orders"], include_in_schema=False)
async def create_order(request: OrderCreateRequest, db: Session = Depends(get_db)):
    """
    주문 생성:
    1. 상품 서비스로부터 상품 정보 및 가격 확인
    2. 재고 확인
    3. 총 결제 금액 계산 (단가 * 수량)
    4. 주문 DB 저장
    """
    if request.quantity <= 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="주문 수량은 1개 이상이어야 합니다."
        )

    # 상품 서비스 호출하여 상품 정보 및 가격 확인
    product = await fetch_product(request.product_id)
    unit_price = product.get("price", 0)
    product_name = product.get("name", "알 수 없는 상품")
    stock = product.get("stock", 0)

    if request.quantity > stock:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"재고가 부족합니다. (현재 재고: {stock}개, 요청 수량: {request.quantity}개)"
        )

    total_price = unit_price * request.quantity

    # 주문 레코드 생성
    new_order = Order(
        product_id=request.product_id,
        product_name=product_name,
        unit_price=unit_price,
        quantity=request.quantity,
        total_price=total_price,
        customer_name=request.customer_name,
        status="COMPLETED",
        notes=request.notes
    )

    try:
        db.add(new_order)
        db.commit()
        db.refresh(new_order)
    except Exception as e:
        db.rollback()
        logger.error(f"주문 저장 실패: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"주문 처리 중 오류가 발생했습니다: {str(e)}"
        )

    return new_order

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
