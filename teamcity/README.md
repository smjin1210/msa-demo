# TeamCity CI/CD 파이프라인 가이드 (K8sTest)

이 문서는 TeamCity의 `K8sTest` 프로젝트를 통해 RKE2 쿠버네티스 클러스터로 MSA 주문 애플리케이션을 지속적으로 통합 및 배포(CI/CD)하기 위한 설정 가이드입니다.

---

## 1. 파이프라인 아키텍처 개요 (방법 A: 마이크로서비스별 완전 독립 파이프라인)

전체 5개 마이크로서비스(`product-service`, `order-service`, `frontend`, `payment-service`, `notification-service`)가 **테스트(Stage 1) ➔ 이미지 빌드(Stage 2) ➔ 롤링 배포(Stage 3)** 전 과정을 서비스별로 완전히 독립된 체인으로 수행하며, 최상위에 일괄 제어 및 통합 뷰를 제공하는 `0. Deploy All`이 구성되어 있습니다:

```
[0. Deploy All Services (Composite Pipeline)]
  │
  ├─► [1-1. Test Product]      ──► [2-1. Build Product]      ──► [3-1. Deploy Product]
  ├─► [1-2. Test Order]        ──► [2-2. Build Order]        ──► [3-2. Deploy Order]
  ├─► [1-3. Test Frontend]     ──► [2-3. Build Frontend]     ──► [3-3. Deploy Frontend]
  ├─► [1-4. Test Payment]      ──► [2-4. Build Payment]      ──► [3-4. Deploy Payment]
  └─► [1-5. Test Notification] ──► [2-5. Build Notification] ──► [3-5. Deploy Notification]
      (Checkout Rules 기반 스마트 빌드 재사용: 변경 없는 서비스는 전 단계 0초 스킵)
```

### 주요 단계별 구성
1. **Stage 1: 서비스별 단위 테스트 & 번들 검증**
   - `1-1. Test Product Service` (`TestProductService`): FastAPI 단위 테스트 (`product-service/**`)
   - `1-2. Test Order Service` (`TestOrderService`): FastAPI + SQLite 단위 테스트 (`order-service/**`)
   - `1-3. Test Frontend` (`TestFrontend`): Vitest + Vite 프로덕션 빌드 (`frontend/**`)
   - `1-4. Test Payment Service` (`TestPaymentService`): FastAPI 단위 테스트 (`payment-service/**`)
   - `1-5. Test Notification Service` (`TestNotificationService`): FastAPI 단위 테스트 (`notification-service/**`)

2. **Stage 2: 서비스별 컨테이너 이미지 빌드 & 푸시 (Kaniko)**
   - `2-1. Build Product Service` ~ `2-5. Build Notification Service`
   - 각 서비스가 변경되었을 때만 전용 Kaniko 파드를 띄워 이미지를 빌드하고 Harbor 레지스트리에 푸시합니다.
   - 변경되지 않은 서비스는 **이전 성공 빌드 아티팩트를 0초 만에 재사용(Smart Re-use)**합니다.

3. **Stage 3: 서비스별 쿠버네티스 롤링 배포 & 헬스체크 검증**
   - `3-1. Deploy Product Service` ~ `3-5. Deploy Notification Service`
   - 해당 서비스의 Deployment에 대해서만 `kubectl set image` 및 `kubectl rollout restart`를 수행하고, 해당 파드의 Readiness Probe 통과 상태를 15~20초 내에 검증합니다.
   - 각 배포 빌드 구성에 개별 VCS 트리거가 등록되어 있어, 특정 서비스 코드 수정 푸시 시 **해당 서비스의 [테스트 ➔ 빌드 ➔ 배포] 3단계만 약 40~50초 내에 초고속 완주**됩니다.

4. **0. Deploy All Services (Composite Pipeline)**
   - 5개 서비스 전체의 배포 상태를 단일 대시보드로 조망하며, 에이전트 자원을 소모하지 않는 Composite 빌드입니다.
   - 수동으로 전체 배포를 실행하거나 `k8s/**` 공통 매니페스트 변경 시 전체 서비스 배포를 일괄 트리거합니다.

---

## 2. Kubernetes Cloud Profile 및 Build Agent 설정

### Agent 최대 수량
- TeamCity 무료 라이선스(Professional) 기준 **최대 3개**의 에이전트 인스턴스로 동시 실행을 제한합니다.
- TeamCity의 Cloud Profile 설정에서 `Max number of instances: 3`으로 구성합니다.

---

## 3. 원격 저장소 및 레지스트리 설정 파라미터

| 파라미터명 | 설명 | 현재 설정값 |
|---|---|---|
| `env.IMAGE_REGISTRY` | 컨테이너 이미지 레지스트리 주소 및 네임스페이스 | `43.203.226.163:30002/msa-demo` (Harbor) |
| `env.IMAGE_TAG` | 이미지 태그 | `%build.number%` (동시에 `latest`도 푸시) |
| `env.REGISTRY_USER` | 레지스트리 인증 사용자명 | `admin` |
| `env.REGISTRY_PASSWORD` | 레지스트리 인증 비밀번호 | `tangun123!` |
| `env.K8S_NAMESPACE` | 배포 대상 쿠버네티스 네임스페이스 | `msa-demo` |

---

## 4. 로컬 테스트 및 설정 적용 방법
TeamCity Versioned Settings가 활성화되어 있으므로 `.teamcity/settings.kts` 파일이 Git push 시 자동으로 동기화됩니다.
