from typing import List, Optional
from app.models import Product

# 초기 시드 상품 데이터
SAMPLE_PRODUCTS: List[Product] = [
    Product(
        id=1,
        name="클라우드 네이티브 쿠버네티스 실전 가이드",
        description="RKE2 및 쿠버네티스 기반 실무 아키텍처 가이드북",
        price=35000,
        stock=50,
        category="도서"
    ),
    Product(
        id=2,
        name="TeamCity CI/CD 파이프라인 마스터",
        description="자동화 빌드, 테스트, 배포 파이프라인 구축을 위한 완벽 가이드",
        price=42000,
        stock=30,
        category="도서"
    ),
    Product(
        id=3,
        name="MSA 아키텍처 디자인 패턴",
        description="마이크로서비스 간 통신과 분산 트랜잭션 핵심 정리",
        price=38000,
        stock=25,
        category="도서"
    ),
    Product(
        id=4,
        name="JetBrains Junie 개발자 머그컵",
        description="스마트한 개발 라이프를 위한 프리미엄 머그컵 (350ml)",
        price=15000,
        stock=100,
        category="굿즈"
    ),
    Product(
        id=5,
        name="DevOps 엔지니어 후드 집업",
        description="편안한 착용감의 오버핏 기모 후드 집업 (L/XL)",
        price=59000,
        stock=15,
        category="의류"
    ),
]

def get_all_products() -> List[Product]:
    return SAMPLE_PRODUCTS

def get_product_by_id(product_id: int) -> Optional[Product]:
    for product in SAMPLE_PRODUCTS:
        if product.id == product_id:
            return product
    return None
