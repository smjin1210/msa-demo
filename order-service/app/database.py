from sqlalchemy import create_engine
from sqlalchemy.orm import declarative_base, sessionmaker
from sqlalchemy.pool import StaticPool
import logging

from app.config import settings

logger = logging.getLogger(__name__)

# SQLite 및 PostgreSQL 호환 처리
connect_args = {}
engine_kwargs = {}
if settings.DATABASE_URL.startswith("sqlite"):
    connect_args = {"check_same_thread": False}
    engine_kwargs["poolclass"] = StaticPool
else:
    engine_kwargs["pool_pre_ping"] = True
    engine_kwargs["pool_size"] = 10
    engine_kwargs["max_overflow"] = 20

engine = create_engine(
    settings.DATABASE_URL,
    connect_args=connect_args,
    **engine_kwargs
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

def get_db():
    """데이터베이스 세션 의존성 주입"""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

def init_db():
    """테이블 자동 생성 (초기화)"""
    try:
        from app import models  # 모델 등록
        Base.metadata.create_all(bind=engine)
        logger.info("데이터베이스 테이블 초기화 완료")
    except Exception as e:
        logger.warning(f"데이터베이스 테이블 초기화 실패 (DB 연결 대기 중일 수 있음): {e}")
