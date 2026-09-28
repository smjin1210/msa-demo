from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime

class HealthResponse(BaseModel):
    status: str = "healthy"
    service: str = "notification-service"
    version: str

class NotificationRequest(BaseModel):
    recipient: str = Field(..., description="수신자 (이메일 또는 전화번호)")
    title: str = Field(..., description="알림 제목")
    message: str = Field(..., description="알림 내용")
    channel: str = Field(default="EMAIL", description="전송 채널 (EMAIL, SMS, PUSH)")

class NotificationResponse(BaseModel):
    id: int
    recipient: str
    title: str
    message: str
    channel: str
    status: str = "SENT"
    sent_at: datetime
