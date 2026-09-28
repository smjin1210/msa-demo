from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from typing import List, Dict
from datetime import datetime

from app.models import HealthResponse, NotificationRequest, NotificationResponse
from app import __version__

app = FastAPI(
    title="Notification Service",
    description="MSA 주문 시스템 - 알림 발송 마이크로서비스",
    version=__version__,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 인메모리 알림 저장소
_notifications_db: Dict[int, NotificationResponse] = {}
_notif_counter = 1

@app.get("/health", response_model=HealthResponse, tags=["Health"])
@app.get("/healthz", response_model=HealthResponse, tags=["Health"])
def health_check():
    """헬스 체크 엔드포인트"""
    return HealthResponse(
        status="healthy",
        service="notification-service",
        version=__version__
    )

@app.get("/notifications", response_model=List[NotificationResponse], tags=["Notifications"])
@app.get("/api/notifications", response_model=List[NotificationResponse], tags=["Notifications"], include_in_schema=False)
def list_notifications():
    """알림 발송 내역 조회"""
    return list(_notifications_db.values())

@app.post("/notifications", response_model=NotificationResponse, status_code=status.HTTP_201_CREATED, tags=["Notifications"])
@app.post("/api/notifications", response_model=NotificationResponse, status_code=status.HTTP_201_CREATED, tags=["Notifications"], include_in_schema=False)
def send_notification(req: NotificationRequest):
    """알림 발송 처리"""
    global _notif_counter
    notif_id = _notif_counter
    _notif_counter += 1

    notif = NotificationResponse(
        id=notif_id,
        recipient=req.recipient,
        title=req.title,
        message=req.message,
        channel=req.channel,
        status="SENT",
        sent_at=datetime.utcnow(),
    )
    _notifications_db[notif_id] = notif
    return notif

@app.get("/notifications/{notification_id}", response_model=NotificationResponse, tags=["Notifications"])
@app.get("/api/notifications/{notification_id}", response_model=NotificationResponse, tags=["Notifications"], include_in_schema=False)
def get_notification(notification_id: int):
    """알림 상세 조회"""
    notif = _notifications_db.get(notification_id)
    if not notif:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"알림 ID {notification_id}를 찾을 수 없습니다."
        )
    return notif

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8004, reload=True)
