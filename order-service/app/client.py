import httpx
from fastapi import HTTPException, status
import logging
from typing import Dict, Any

from app.config import settings

logger = logging.getLogger(__name__)

async def fetch_product(product_id: int) -> Dict[str, Any]:
    """상품 서비스로부터 상품 정보 조회"""
    url = f"{settings.PRODUCT_SERVICE_URL.rstrip('/')}/products/{product_id}"
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(url)
            if response.status_code == 404:
                raise HTTPException(
                    status_code=status.HTTP_404_NOT_FOUND,
                    detail=f"상품 ID {product_id}가 존재하지 않습니다."
                )
            if response.status_code != 200:
                logger.error(f"상품 서비스 호출 실패: status={response.status_code}, body={response.text}")
                raise HTTPException(
                    status_code=status.HTTP_502_BAD_GATEWAY,
                    detail="상품 서비스 응답 오류가 발생했습니다."
                )
            return response.json()
    except httpx.RequestError as exc:
        logger.error(f"상품 서비스 통신 오류: {exc}")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="상품 서비스와 통신할 수 없습니다."
        )
