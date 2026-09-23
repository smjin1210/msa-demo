import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs

/*
 * TeamCity Kotlin DSL Configuration for Project: K8sTest
 * MSA 주문 시스템 CI/CD 파이프라인
 */

version = "2024.03"

project {
    description = "MSA 주문 시스템 CI/CD 파이프라인 (RKE2 + Kubernetes Cloud Profile)"

    params {
        param("env.IMAGE_REGISTRY", "15.165.77.105:30002/msa-demo")
        param("env.IMAGE_TAG", "%build.number%")
        param("env.K8S_NAMESPACE", "msa-demo")
        password("env.REGISTRY_PASSWORD", "HarborPassword123!", display = ParameterDisplay.HIDDEN, label = "Container Registry Password")
        param("env.REGISTRY_USER", "admin")
    }

    buildType(RunUnitTests)
    buildType(BuildAndPushImages)
    buildType(DeployAndVerify)

    buildTypesOrder = listOf(RunUnitTests, BuildAndPushImages, DeployAndVerify)
}

object RunUnitTests : BuildType({
    id("RunUnitTests")
    name = "1. Run Tests (Frontend & Services)"
    description = "각 마이크로서비스 단위 테스트 및 프론트엔드 빌드 검증"

    vcs {
        root(DslContext.settingsRoot)
    }

    triggers {
        vcs {
            branchFilter = "+:refs/heads/main"
        }
    }

    steps {
        script {
            name = "Test Product Service (FastAPI)"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "=== Product Service 가상환경 구성 및 테스트 실행 ==="
                cd product-service
                python3 -m venv .venv
                source .venv/bin/activate
                pip install -r requirements.txt
                pytest -v
            """.trimIndent()
        }

        script {
            name = "Test Order Service (FastAPI + SQLite Test DB)"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "=== Order Service 가상환경 구성 및 테스트 실행 ==="
                cd order-service
                python3 -m venv .venv
                source .venv/bin/activate
                pip install -r requirements.txt
                pytest -v
            """.trimIndent()
        }

        script {
            name = "Test & Build Frontend (React + Vite)"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "=== Node.js 바이너리 환경 구성 ==="
                NODE_VERSION="v20.18.0"
                NODE_DIR="/tmp/node-${'$'}{NODE_VERSION}-linux-x64"

                if [ ! -d "${'$'}NODE_DIR" ]; then
                    echo "Node.js ${'$'}{NODE_VERSION} 다운로드 중..."
                    curl -fsSL "https://nodejs.org/dist/${'$'}{NODE_VERSION}/node-${'$'}{NODE_VERSION}-linux-x64.tar.xz" | tar -xJ -C /tmp
                fi

                export PATH="${'$'}NODE_DIR/bin:${'$'}PATH"
                echo "Node version: ${'$'}(node -v)"
                echo "NPM version: ${'$'}(npm -v)"

                echo "=== Frontend 테스트 및 프로덕션 빌드 검증 ==="
                cd frontend
                npm ci || npm install
                npm test
                npm run build
            """.trimIndent()
        }
    }
})

object BuildAndPushImages : BuildType({
    id("BuildAndPushImages")
    name = "2. Build & Push Container Images (Kaniko)"
    description = "containerd 노드 환경에서 Kaniko를 이용한 비특권 도커 이미지 빌드 및 원격 레지스트리 푸시"

    dependencies {
        snapshot(RunUnitTests) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    steps {
        script {
            name = "Build & Push All Images via Kaniko"
            scriptContent = """
                #!/bin/bash
                set -e
                
                REGISTRY="%env.IMAGE_REGISTRY%"
                TAG="%env.IMAGE_TAG%"
                REG_USER="%env.REGISTRY_USER%"
                REG_PASS="%env.REGISTRY_PASSWORD%"
                
                echo "=== 이미지 빌드 및 푸시 설정 ==="
                echo "대상 레지스트리: ${'$'}REGISTRY"
                echo "이미지 태그: ${'$'}TAG"
                
                # 1. kubectl 환경 확인 및 준비
                if ! command -v kubectl &> /dev/null; then
                    echo "kubectl 다운로드 중..."
                    curl -fsSL -o /tmp/kubectl "https://dl.k8s.io/release/v1.33.5/bin/linux/amd64/kubectl"
                    chmod +x /tmp/kubectl
                    export PATH="/tmp:${'$'}PATH"
                fi
                echo "kubectl 준비 완료"

                # 2. Harbor 인증 시크릿 갱신
                REG_HOST="${'$'}(echo "${'$'}REGISTRY" | cut -d/ -f1)"
                kubectl create secret docker-registry harbor-creds -n teamcity \
                  --docker-server="${'$'}REG_HOST" \
                  --docker-username="${'$'}REG_USER" \
                  --docker-password="${'$'}REG_PASS" \
                  --dry-run=client -o yaml | kubectl apply -f -

                # 3. 마이크로서비스별 Kaniko 파드 빌드 실행
                for SVC in product-service order-service frontend; do
                    echo "=================================================="
                    echo "Kaniko Pod를 통한 ${'$'}SVC 컨테이너 빌드 및 푸시 시작"
                    echo "=================================================="
                    
                    POD_NAME="kaniko-build-${'$'}SVC-%build.number%"
                    kubectl delete pod "${'$'}POD_NAME" -n teamcity --ignore-not-found=true
                    
                    cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: ${'$'}POD_NAME
  namespace: teamcity
spec:
  restartPolicy: Never
  containers:
  - name: kaniko
    image: gcr.io/kaniko-project/executor:v1.23.2-debug
    args:
    - --context=git://github.com/smjin1210/msa-demo.git#refs/heads/main
    - --context-sub-path=${'$'}SVC
    - --destination=${'$'}REGISTRY/${'$'}SVC:${'$'}TAG
    - --destination=${'$'}REGISTRY/${'$'}SVC:latest
    - --cache=true
    - --insecure
    - --skip-tls-verify
    volumeMounts:
    - name: creds
      mountPath: /kaniko/.docker/
  volumes:
  - name: creds
    secret:
      secretName: harbor-creds
      items:
      - key: .dockerconfigjson
        path: config.json
EOF

                    echo "${'$'}POD_NAME 기동 대기 중..."
                    kubectl wait --for=condition=Ready pod/"${'$'}POD_NAME" -n teamcity --timeout=60s || true
                    kubectl logs -n teamcity "${'$'}POD_NAME" -f
                    
                    STATUS="${'$'}(kubectl get pod "${'$'}POD_NAME" -n teamcity -o jsonpath='{.status.phase}')"
                    if [ "${'$'}STATUS" != "Succeeded" ]; then
                        echo "ERROR: ${'$'}SVC 빌드 실패 (상태: ${'$'}STATUS)"
                        exit 1
                    fi
                    echo "${'$'}SVC 이미지 빌드 및 푸시 성공!"
                    kubectl delete pod "${'$'}POD_NAME" -n teamcity --ignore-not-found=true
                done
                echo "모든 마이크로서비스 이미지 빌드 및 푸시 완료!"
            """.trimIndent()
        }
    }
})

object DeployAndVerify : BuildType({
    id("DeployAndVerify")
    name = "3. Deploy & Verify Rollout"
    description = "쿠버네티스(RKE2) 클러스터에 최신 이미지 롤아웃 및 상태 검증"

    dependencies {
        snapshot(BuildAndPushImages) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    steps {
        script {
            name = "Deploy to RKE2 and Rollout Check"
            scriptContent = """
                #!/bin/bash
                set -e
                
                NAMESPACE="%env.K8S_NAMESPACE%"
                REGISTRY="%env.IMAGE_REGISTRY%"
                TAG="%env.IMAGE_TAG%"
                
                # 1. kubectl 환경 확인
                if ! command -v kubectl &> /dev/null; then
                    curl -fsSL -o /tmp/kubectl "https://dl.k8s.io/release/v1.33.5/bin/linux/amd64/kubectl"
                    chmod +x /tmp/kubectl
                    export PATH="/tmp:${'$'}PATH"
                fi
                
                echo "=== 1. 네임스페이스 및 기본 리소스 배포 ==="
                kubectl get namespace "${'$'}NAMESPACE" || kubectl apply -f k8s/00-namespace.yaml
                kubectl apply -f k8s/02-postgres.yaml
                kubectl apply -f k8s/03-product-service.yaml
                kubectl apply -f k8s/04-order-service.yaml
                kubectl apply -f k8s/05-frontend.yaml

                echo "=== 2. 신규 이미지 롤아웃 트리거 ==="
                kubectl set image deployment/product-service product-service="${'$'}REGISTRY/product-service:${'$'}TAG" -n "${'$'}NAMESPACE" || true
                kubectl set image deployment/order-service order-service="${'$'}REGISTRY/order-service:${'$'}TAG" -n "${'$'}NAMESPACE" || true
                kubectl set image deployment/frontend frontend="${'$'}REGISTRY/frontend:${'$'}TAG" -n "${'$'}NAMESPACE" || true

                echo "=== 3. 롤아웃 상태 검증 (Timeout: 180초) ==="
                kubectl rollout status deployment/product-service -n "${'$'}NAMESPACE" --timeout=180s
                kubectl rollout status deployment/order-service -n "${'$'}NAMESPACE" --timeout=180s
                kubectl rollout status deployment/frontend -n "${'$'}NAMESPACE" --timeout=180s

                echo "=== 4. 배포 파드 및 엔드포인트 헬스체크 확인 ==="
                kubectl get pods -n "${'$'}NAMESPACE" -o wide
                kubectl get svc -n "${'$'}NAMESPACE"
            """.trimIndent()
        }
    }
})
