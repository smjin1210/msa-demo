# TeamCity CI/CD 실습용 MSA 주문 시스템

TeamCity CI/CD 파이프라인 실습 및 AWS EC2 2노드 RKE2(v1.33.5) 쿠버네티스 클러스터 배포를 위한 마이크로서비스 아키텍처(MSA) 주문 애플리케이션입니다.

---

## 1. 시스템 아키텍처 및 구성

```
[ 브라우저 (사용자) ]
        │
        ▼ (상대경로 /api/...)
┌──────────────────────────────────────────┐
│  frontend (Nginx + React/TS/Vite)        │ :80 / :30080
└─────────┬──────────────────────┬─────────┘
          │ /api/products/*      │ /api/orders/*
          ▼                      ▼
┌──────────────────┐   ┌───────────────────────────┐
│ product-service  │   │ order-service             │
│ (FastAPI)        │◄──┤ (FastAPI + PostgreSQL)    │
│ :8000            │   │ :8000                     │
└──────────────────┘   └─────────────┬─────────────┘
                                     │
                                     ▼
                       ┌───────────────────────────┐
                       │ PostgreSQL Database       │
                       │ (msa-postgres-pvc)        │
                       │ StorageClass: local-path  │
                       └───────────────────────────┘
```

### 서비스별 상세 스펙

| 서비스명 | 기술 스택 | 주요 기능 | 헬스체크 엔드포인트 |
|---|---|---|---|
| **frontend** | React 18, TypeScript, Vite, Nginx | 상품 목록 조회, 주문 생성 폼, 주문 내역 조회, `/api` 프록시 라우팅 | `GET /health` |
| **product-service** | FastAPI, Pydantic, Uvicorn | 상품 목록(`GET /products`), 상품 상세(`GET /products/{id}`) | `GET /health` |
| **order-service** | FastAPI, SQLAlchemy, PostgreSQL, HTTPX | 상품 서비스 연동 가격/재고 검증, 주문 생성(`POST /orders`), 주문 목록/상세 조회, DB 연결 상태 핑 | `GET /health` |
| **postgres** | PostgreSQL 16 (Alpine) | `order-service` 전용 격리 데이터베이스 (TeamCity DB와 분리) | `pg_isready` |

---

## 2. 로컬 실행 가이드 (Docker Compose)

### 2.1 사전 요구사항
- Docker & Docker Compose 설치

### 2.2 환경 변수 설정
```bash
cp .env.example .env
```

### 2.3 전체 서비스 실행
```bash
# 컨테이너 빌드 및 백그라운드 실행
docker compose up -d --build

# 실행 상태 및 헬스체크 확인
docker compose ps
```

### 2.4 접속 주소
- **웹 UI (Frontend)**: http://localhost (또는 http://localhost:80)
- **상품 서비스 API**: http://localhost:8001/docs
- **주문 서비스 API**: http://localhost:8002/docs

### 2.5 서비스 종료
```bash
docker compose down -v
```

---

## 3. 서비스별 단위 테스트 실행 가이드

모든 서비스는 독립적으로 로컬 및 CI 파이프라인에서 실행 가능한 단위 테스트를 포함하고 있습니다.

### 3.1 Product Service 테스트
```bash
cd product-service
python3 -m pip install -r requirements.txt
python3 -m pytest -v
```

### 3.2 Order Service 테스트 (SQLite 인메모리 격리 테스트)
```bash
cd order-service
python3 -m pip install -r requirements.txt
python3 -m pytest -v
```

### 3.3 Frontend 테스트 및 프로덕션 빌드 검증
```bash
cd frontend
npm install
npm test
npm run build
```

---

## 4. Kubernetes 배포 가이드 (RKE2 v1.33.5)

### 4.1 클러스터 환경 사양
- **인프라**: AWS EC2 2-Node RKE2 Cluster (v1.33.5)
- **컨테이너 런타임**: containerd
- **스토리지 클래스**: `local-path`
- **배포 네임스페이스**: `msa-demo`

### 4.2 DB 비밀번호 및 시크릿 생성 (하드코딩 방지)
클러스터에 실제 배포하기 전, 보안 인증 정보를 담은 Kubernetes Secret을 먼저 생성합니다:

```bash
# 1. 네임스페이스 생성
kubectl apply -f k8s/00-namespace.yaml

# 2. Secret 생성 (보안 비밀번호 적용)
kubectl create secret generic msa-postgres-secret \
  --namespace=msa-demo \
  --from-literal=POSTGRES_USER=msa_user \
  --from-literal=POSTGRES_PASSWORD=YOUR_SECURE_PASSWORD \
  --from-literal=POSTGRES_DB=orders_db \
  --from-literal=DATABASE_URL="postgresql://msa_user:YOUR_SECURE_PASSWORD@postgres-service:5432/orders_db"
```

### 4.3 리소스 매니페스트 적용 순서
```bash
# 3. PostgreSQL 데이터베이스 (PVC StorageClass: local-path)
kubectl apply -f k8s/02-postgres.yaml

# 4. 백엔드 마이크로서비스 배포 (Requests/Limits 및 Probe 적용)
kubectl apply -f k8s/03-product-service.yaml
kubectl apply -f k8s/04-order-service.yaml

# 5. 프론트엔드 배포 (NodePort: 30080 및 Ingress)
kubectl apply -f k8s/05-frontend.yaml
```

### 4.4 리소스 배포 상태 확인
```bash
kubectl get all,pvc -n msa-demo
```

---

## 5. TeamCity CI/CD 파이프라인 설계 (Project: K8sTest)

TeamCity에서 **Kubernetes Cloud Profile**을 활용하여 무료 라이선스 기준 최대 3개의 에이전트 환경에서 동작하도록 파이프라인이 설계되었습니다.

### 5.1 파이프라인 단계 구조
1. **Stage 1: Run Tests** (`K8sTest_RunUnitTests`)
   - 프론트엔드 및 백엔드 서비스의 단위 테스트와 빌드 검증을 병렬/순차 수행
2. **Stage 2: Build & Push Images** (`K8sTest_BuildAndPushImages`)
   - **containerd 런타임 빌드 대응**: RKE2 워커 노드에는 docker daemon이 없으므로, Kubernetes 에이전트 파드 내에서 **Google Kaniko**(`/kaniko/executor`)를 사용하여 데몬리스로 컨테이너 이미지를 빌드하고 원격 레지스트리로 푸시합니다.
3. **Stage 3: Deploy & Verify** (`K8sTest_DeployAndVerify`)
   - `msa-demo` 네임스페이스의 각 디플로이먼트 이미지 롤아웃 수행
   - `kubectl rollout status` 명령을 통해 180초 동안 Pod Readiness 및 롤아웃 완료를 검증

### 5.2 TeamCity Kotlin DSL 설정 파일
- `.teamcity/settings.kts` (및 `teamcity/settings.kts`)

---

## 6. 외부 저장소 및 레지스트리 결정 시 추가 설정할 사항

원격 Git 저장소 및 컨테이너 레지스트리가 준비된 후 다음 항목을 설정하면 CI/CD 파이프라인이 완성됩니다.

### 6.1 TeamCity 파라미터 등록
TeamCity 프로젝트 `K8sTest`의 **Parameters** 메뉴에서 아래 파라미터의 실제 값을 등록합니다:

1. `git.repository.url`: 결정된 원격 Git 리포지토리 주소
2. `env.IMAGE_REGISTRY`: 사용할 컨테이너 레지스트리 주소 (예: `ghcr.io/your-username` 또는 AWS ECR 주소)
3. `env.REGISTRY_USER`: 레지스트리 로그인 계정
4. `env.REGISTRY_PASSWORD`: 레지스트리 Access Token 또는 패스워드 (TeamCity Password 타입으로 설정)

### 6.2 Kubernetes 클러스터 내 이미지 풀 시크릿(ImagePullSecret) 설정 (필요 시)
비공개(Private) 레지스트리를 사용할 경우:
```bash
kubectl create secret docker-registry regcred \
  --namespace=msa-demo \
  --docker-server=YOUR_REGISTRY_HOST \
  --docker-username=YOUR_USER \
  --docker-password=YOUR_PASSWORD
```

---

## 7. 디렉터리 구조 요약

```
msa-demo/
├── .env.example               # 로컬 실행용 환경 변수 예시
├── docker-compose.yml         # 로컬 올인원 실행용 Docker Compose 설정
├── README.md                  # 프로젝트 종합 가이드 (본 문서)
├── frontend/                  # React + TypeScript + Vite + Nginx 프론트엔드
│   ├── Dockerfile             # Multi-stage 빌드 Dockerfile
│   ├── nginx.conf             # Nginx 리버스 프록시 및 SPA 설정
│   ├── package.json
│   ├── src/
│   └── tests/
├── product-service/           # FastAPI 상품 마이크로서비스
│   ├── Dockerfile
│   ├── requirements.txt       # 고정 버전 의존성
│   ├── app/
│   └── tests/
├── order-service/             # FastAPI + PostgreSQL 주문 마이크로서비스
│   ├── Dockerfile
│   ├── requirements.txt       # 고정 버전 의존성
│   ├── app/
│   └── tests/
├── k8s/                       # 쿠버네티스(RKE2) 배포 매니페스트
│   ├── 00-namespace.yaml      # 네임스페이스 (msa-demo)
│   ├── 01-secrets-template.yaml # 시크릿 템플릿
│   ├── 02-postgres.yaml       # PostgreSQL Deployment + PVC (local-path)
│   ├── 03-product-service.yaml# 상품 서비스 Deployment + Service
│   ├── 04-order-service.yaml  # 주문 서비스 Deployment + Service
│   └── 05-frontend.yaml       # 프론트엔드 Deployment + NodePort Service + Ingress
└── teamcity/                  # TeamCity CI/CD 설정 및 Kotlin DSL
    ├── README.md              # TeamCity 파이프라인 상세 가이드
    └── settings.kts           # TeamCity Kotlin DSL 빌드 체인 정의
```
