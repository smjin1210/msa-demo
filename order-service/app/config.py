from pydantic_settings import BaseSettings
from pydantic import Field

class Settings(BaseSettings):
    DATABASE_URL: str = Field(
        default="postgresql://postgres:postgres@localhost:5432/orders_db",
        description="PostgreSQL 데이터베이스 연결 URL"
    )
    PRODUCT_SERVICE_URL: str = Field(
        default="http://localhost:8001",
        description="상품 마이크로서비스 기본 URL"
    )
    APP_VERSION: str = Field(
        default="1.0.0",
        description="애플리케이션 버전"
    )

    class Config:
        env_file = ".env"
        extra = "ignore"

settings = Settings()
