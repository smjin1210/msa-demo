# TeamCity CI/CD 파이프라인 가이드 (K8sTest)

이 문서는 TeamCity의 `K8sTest` 프로젝트를 통해 RKE2 쿠버네티스 클러스터로 MSA 주문 애플리케이션을 지속적으로 통합 및 배포(CI/CD)하기 위한 설정 가이드입니다.

---

## 1. 파이프라인 아키텍처 개요

파이프라인은 다음 3단계(Stage)로 구성되며 순차적/스냅샷 종속성(Snapshot Dependency)을 가집니다:

```
[1. Run Tests] ──> [2. Build & Push Images] ──> [3. Deploy & Verify]
```

1. **Stage 1: Run Tests (단위 테스트 및 정적 검증)**
   - `product-service`: pytest 단위 테스트 실행
   - `order-service`: SQLite 인메모리 기반 pytest 단위 테스트 실행
   - `frontend`: vitest 단위 테스트 및 TypeScript 프로덕션 빌드(`npm run build`) 검증
2. **Stage 2: Build & Push Images (컨테이너 이미지 빌드 및 푸시)**
   - **중요**: RKE2 클러스터 노드는 `containerd` 런타임을 사용하므로 `/var/run/docker.sock` 데몬이 없습니다.
   - 따라서 데몬리스(Daemonless) 컨테이너 빌더인 **Google Kaniko**(`gcr.io/kaniko-project/executor`)를 사용하여 빌드 에이전트 파드 내에서 비특권 모드로 안전하게 이미지를 빌드 및 푸시합니다.
3. **Stage 3: Deploy & Verify (쿠버네티스 롤아웃 및 상태 검증)**
   - 네임스페이스(`msa-demo`) 확인 및 `kubectl set image`를 통한 무중단 롤링 업데이트
   - `kubectl rollout status`를 통해 3개 서비스의 배포 완료 및 readiness probe 통과 여부 검증 (Timeout: 180초)

---

## 2. Kubernetes Cloud Profile 및 Build Agent 설정

### Agent 최대 수량
- TeamCity 무료 라이선스(Professional) 기준 **최대 3개**의 에이전트 인스턴스로 동시 실행을 제한합니다.
- TeamCity의 Cloud Profile 설정에서 `Max number of instances: 3`으로 구성합니다.

### containerd 환경을 위한 Agent Pod Template (Kaniko 지원)
Kubernetes Cloud Profile에서 사용할 Pod 템플릿 예시:

```yaml
apiVersion: v1
kind: Pod
metadata:
  labels:
    teamcity-agent: "true"
spec:
  containers:
    - name: teamcity-agent
      image: jetbrains/teamcity-agent:latest
      resources:
        requests:
          cpu: "500m"
          memory: "1Gi"
        limits:
          cpu: "2"
          memory: "4Gi"
      volumeMounts:
        - name: kaniko-secret
          mountPath: /kaniko/.docker
  volumes:
    - name: kaniko-secret
      secret:
        secretName: regcred
        items:
          - key: .dockerconfigjson
            path: config.json
```

---

## 3. 원격 저장소 및 레지스트리 결정 시 추가 설정할 항목

원격 Git 저장소와 컨테이너 레지스트리가 최종 결정되면 TeamCity 웹 콘솔 또는 `teamcity/settings.kts`에서 다음 파라미터를 입력합니다:

| 파라미터명 | 설명 | 예시 값 |
|---|---|---|
| `git.repository.url` | 프로젝트 원격 Git 저장소 URL | `https://github.com/my-org/msa-demo.git` |
| `env.IMAGE_REGISTRY` | 컨테이너 이미지 레지스트리 주소 및 네임스페이스 | `ghcr.io/my-org` 또는 `123456789.dkr.ecr.ap-northeast-2.amazonaws.com` |
| `env.REGISTRY_USER` | 레지스트리 인증 사용자명 | `robot-user` / `AWS` / `my-username` |
| `env.REGISTRY_PASSWORD` | 레지스트리 인증 토큰/비밀번호 | TeamCity Credentials Storage에 보안 저장 |
| `env.K8S_NAMESPACE` | 배포 대상 쿠버네티스 네임스페이스 | `msa-demo` |

---

## 4. 로컬 테스트 및 설정 적용 방법
TeamCity Versioned Settings가 활성화되어 있으면 `.teamcity/settings.kts` 파일이 Git push 시 자동으로 동기화됩니다.
