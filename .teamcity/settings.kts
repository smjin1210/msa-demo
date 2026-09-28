import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs

/*
 * TeamCity Kotlin DSL Configuration for Project: K8sTest
 * MSA 주문 시스템 모듈형 마이크로서비스 CI/CD 파이프라인 (방법 A: 서비스별 완전 독립 파이프라인)
 */

version = "2024.03"

project {
    description = "MSA 주문 시스템 CI/CD 파이프라인 (RKE2 + Kubernetes Cloud Profile - 모듈형 아키텍처)"

    params {
        param("env.IMAGE_REGISTRY", "43.203.226.163:30002/msa-demo")
        param("env.IMAGE_TAG", "%build.number%")
        param("env.K8S_NAMESPACE", "msa-demo")
        password("env.REGISTRY_PASSWORD", "tangun123!", display = ParameterDisplay.HIDDEN, label = "Container Registry Password")
        param("env.REGISTRY_USER", "admin")
    }

    // 마스터 통합 배포 파이프라인
    buildType(DeployAll)

    // Stage 1: 단위 테스트 & 번들 검증
    buildType(TestProductService)
    buildType(TestOrderService)
    buildType(TestFrontend)
    buildType(TestPaymentService)
    buildType(TestNotificationService)

    // Stage 2: 서비스별 컨테이너 이미지 빌드 & 푸시 (Kaniko)
    buildType(BuildProductService)
    buildType(BuildOrderService)
    buildType(BuildFrontend)
    buildType(BuildPaymentService)
    buildType(BuildNotificationService)

    // Stage 3: 서비스별 쿠버네티스 롤링 배포 & 검증
    buildType(DeployProductService)
    buildType(DeployOrderService)
    buildType(DeployFrontend)
    buildType(DeployPaymentService)
    buildType(DeployNotificationService)

    buildTypesOrder = listOf(
        DeployAll,
        TestProductService, TestOrderService, TestFrontend, TestPaymentService, TestNotificationService,
        BuildProductService, BuildOrderService, BuildFrontend, BuildPaymentService, BuildNotificationService,
        DeployProductService, DeployOrderService, DeployFrontend, DeployPaymentService, DeployNotificationService
    )
}

// ==============================================================================
// 0. 마스터 통합 배포 파이프라인 (Composite Build)
// ==============================================================================
object DeployAll : BuildType({
    id("DeployAll")
    name = "0. Deploy All Services (Composite Pipeline)"
    description = "전체 5개 마이크로서비스 배포 상태 통합 뷰 및 일괄 트리거"
    type = Type.COMPOSITE

    vcs {
        root(DslContext.settingsRoot)
    }

    triggers {
        vcs {
            branchFilter = "+:refs/heads/main"
            triggerRules = """
                +:k8s/**
                -:.teamcity/**
                -:k8s-gitops/**
                -:argocd/**
                -:README.md
            """.trimIndent()
        }
    }

    dependencies {
        snapshot(DeployProductService) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(DeployOrderService) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(DeployFrontend) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(DeployPaymentService) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(DeployNotificationService) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }
})

// ==============================================================================
// 1. Stage 1: 단위 테스트 (1-1 ~ 1-5)
// ==============================================================================
object TestProductService : BuildType({
    id("TestProductService")
    name = "1-1. Test Product Service"
    description = "Product Service (FastAPI) 단위 테스트"

    vcs {
        root(DslContext.settingsRoot, "+:product-service")
    }

    steps {
        script {
            name = "Test Product Service (FastAPI)"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "=== Product Service 가상환경 구성 및 테스트 실행 ==="
                if [ -d "product-service" ]; then
                    cd product-service
                fi
                python3 -m venv .venv
                source .venv/bin/activate
                pip install -r requirements.txt
                pytest -v
            """.trimIndent()
        }
    }
})

object TestOrderService : BuildType({
    id("TestOrderService")
    name = "1-2. Test Order Service"
    description = "Order Service (FastAPI + SQLite Test DB) 단위 테스트"

    vcs {
        root(DslContext.settingsRoot, "+:order-service")
    }

    steps {
        script {
            name = "Test Order Service (FastAPI + SQLite Test DB)"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "=== Order Service 가상환경 구성 및 테스트 실행 ==="
                if [ -d "order-service" ]; then
                    cd order-service
                fi
                python3 -m venv .venv
                source .venv/bin/activate
                pip install -r requirements.txt
                pytest -v
            """.trimIndent()
        }
    }
})

object TestFrontend : BuildType({
    id("TestFrontend")
    name = "1-3. Test Frontend"
    description = "Frontend (React + Vite) 단위 테스트 및 빌드 검증"

    vcs {
        root(DslContext.settingsRoot, "+:frontend")
    }

    steps {
        script {
            name = "Test Frontend (React + Vite)"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "=== Frontend 단위 테스트 및 빌드 검증 ==="
                NODE_VERSION="v20.18.0"
                NODE_DIR="/tmp/node-${'$'}{NODE_VERSION}-linux-x64"
                if [ ! -d "${'$'}NODE_DIR" ]; then
                    echo "Node.js ${'$'}NODE_VERSION 다운로드 중..."
                    curl -fsSL "https://nodejs.org/dist/${'$'}{NODE_VERSION}/node-${'$'}{NODE_VERSION}-linux-x64.tar.xz" | tar -xJ -C /tmp
                fi
                export PATH="${'$'}NODE_DIR/bin:${'$'}PATH"

                if [ -d "frontend" ]; then
                    cd frontend
                fi
                npm ci || npm install
                npm test
                npm run build
            """.trimIndent()
        }
    }
})

object TestPaymentService : BuildType({
    id("TestPaymentService")
    name = "1-4. Test Payment Service"
    description = "Payment Service (FastAPI) 단위 테스트"

    vcs {
        root(DslContext.settingsRoot, "+:payment-service")
    }

    steps {
        script {
            name = "Test Payment Service (FastAPI)"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "=== Payment Service 가상환경 구성 및 테스트 실행 ==="
                if [ -d "payment-service" ]; then
                    cd payment-service
                fi
                python3 -m venv .venv
                source .venv/bin/activate
                pip install -r requirements.txt
                pytest -v
            """.trimIndent()
        }
    }
})

object TestNotificationService : BuildType({
    id("TestNotificationService")
    name = "1-5. Test Notification Service"
    description = "Notification Service (FastAPI) 단위 테스트"

    vcs {
        root(DslContext.settingsRoot, "+:notification-service")
    }

    steps {
        script {
            name = "Test Notification Service (FastAPI)"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "=== Notification Service 가상환경 구성 및 테스트 실행 ==="
                if [ -d "notification-service" ]; then
                    cd notification-service
                fi
                python3 -m venv .venv
                source .venv/bin/activate
                pip install -r requirements.txt
                pytest -v
            """.trimIndent()
        }
    }
})

// ==============================================================================
// 2. Stage 2: 서비스별 이미지 빌드 & 푸시 (Kaniko) (2-1 ~ 2-5)
// ==============================================================================
object BuildProductService : BuildType({
    id("BuildProductService")
    name = "2-1. Build Product Service"
    description = "Kaniko를 이용한 Product Service 도커 이미지 빌드 및 Harbor 푸시"

    vcs {
        root(DslContext.settingsRoot, "+:product-service")
    }

    dependencies {
        snapshot(TestProductService) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    steps {
        script {
            name = "Build & Push Product Service via Kaniko"
            scriptContent = """
                #!/bin/bash
                set -e
                
                REGISTRY="%env.IMAGE_REGISTRY%"
                TAG="%env.IMAGE_TAG%"
                REG_USER="%env.REGISTRY_USER%"
                REG_PASS="%env.REGISTRY_PASSWORD%"
                SVC="product-service"
                
                echo "=== Kaniko Pod를 통한 ${'$'}SVC 컨테이너 빌드 및 푸시 시작 ==="
                if ! command -v kubectl &> /dev/null; then
                    curl -fsSL -o /tmp/kubectl "https://dl.k8s.io/release/v1.33.5/bin/linux/amd64/kubectl"
                    chmod +x /tmp/kubectl
                    export PATH="/tmp:${'$'}PATH"
                fi

                REG_HOST="${'$'}(echo "${'$'}REGISTRY" | cut -d/ -f1)"
                kubectl create secret docker-registry harbor-creds -n teamcity \
                  --docker-server="${'$'}REG_HOST" \
                  --docker-username="${'$'}REG_USER" \
                  --docker-password="${'$'}REG_PASS" \
                  --dry-run=client -o yaml | kubectl apply -f -

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

                kubectl wait --for=condition=Ready pod/"${'$'}POD_NAME" -n teamcity --timeout=60s || true
                kubectl logs -n teamcity "${'$'}POD_NAME" -f
                
                while true; do
                    PHASE="${'$'}(kubectl get pod "${'$'}POD_NAME" -n teamcity -o jsonpath='{.status.phase}' 2>/dev/null || true)"
                    if [ "${'$'}PHASE" = "Succeeded" ] || [ "${'$'}PHASE" = "Failed" ]; then
                        break
                    fi
                    sleep 1
                done
                
                STATUS="${'$'}(kubectl get pod "${'$'}POD_NAME" -n teamcity -o jsonpath='{.status.phase}')"
                if [ "${'$'}STATUS" != "Succeeded" ]; then
                    echo "ERROR: ${'$'}SVC 빌드 실패 (상태: ${'$'}STATUS)"
                    kubectl describe pod "${'$'}POD_NAME" -n teamcity || true
                    exit 1
                fi
                echo "${'$'}SVC 이미지 빌드 및 푸시 성공!"
                kubectl delete pod "${'$'}POD_NAME" -n teamcity --ignore-not-found=true
            """.trimIndent()
        }
    }
})

object BuildOrderService : BuildType({
    id("BuildOrderService")
    name = "2-2. Build Order Service"
    description = "Kaniko를 이용한 Order Service 도커 이미지 빌드 및 Harbor 푸시"

    vcs {
        root(DslContext.settingsRoot, "+:order-service")
    }

    dependencies {
        snapshot(TestOrderService) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    steps {
        script {
            name = "Build & Push Order Service via Kaniko"
            scriptContent = """
                #!/bin/bash
                set -e
                
                REGISTRY="%env.IMAGE_REGISTRY%"
                TAG="%env.IMAGE_TAG%"
                REG_USER="%env.REGISTRY_USER%"
                REG_PASS="%env.REGISTRY_PASSWORD%"
                SVC="order-service"
                
                echo "=== Kaniko Pod를 통한 ${'$'}SVC 컨테이너 빌드 및 푸시 시작 ==="
                if ! command -v kubectl &> /dev/null; then
                    curl -fsSL -o /tmp/kubectl "https://dl.k8s.io/release/v1.33.5/bin/linux/amd64/kubectl"
                    chmod +x /tmp/kubectl
                    export PATH="/tmp:${'$'}PATH"
                fi

                REG_HOST="${'$'}(echo "${'$'}REGISTRY" | cut -d/ -f1)"
                kubectl create secret docker-registry harbor-creds -n teamcity \
                  --docker-server="${'$'}REG_HOST" \
                  --docker-username="${'$'}REG_USER" \
                  --docker-password="${'$'}REG_PASS" \
                  --dry-run=client -o yaml | kubectl apply -f -

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

                kubectl wait --for=condition=Ready pod/"${'$'}POD_NAME" -n teamcity --timeout=60s || true
                kubectl logs -n teamcity "${'$'}POD_NAME" -f
                
                while true; do
                    PHASE="${'$'}(kubectl get pod "${'$'}POD_NAME" -n teamcity -o jsonpath='{.status.phase}' 2>/dev/null || true)"
                    if [ "${'$'}PHASE" = "Succeeded" ] || [ "${'$'}PHASE" = "Failed" ]; then
                        break
                    fi
                    sleep 1
                done
                
                STATUS="${'$'}(kubectl get pod "${'$'}POD_NAME" -n teamcity -o jsonpath='{.status.phase}')"
                if [ "${'$'}STATUS" != "Succeeded" ]; then
                    echo "ERROR: ${'$'}SVC 빌드 실패 (상태: ${'$'}STATUS)"
                    kubectl describe pod "${'$'}POD_NAME" -n teamcity || true
                    exit 1
                fi
                echo "${'$'}SVC 이미지 빌드 및 푸시 성공!"
                kubectl delete pod "${'$'}POD_NAME" -n teamcity --ignore-not-found=true
            """.trimIndent()
        }
    }
})

object BuildFrontend : BuildType({
    id("BuildFrontend")
    name = "2-3. Build Frontend"
    description = "Kaniko를 이용한 Frontend 도커 이미지 빌드 및 Harbor 푸시"

    vcs {
        root(DslContext.settingsRoot, "+:frontend")
    }

    dependencies {
        snapshot(TestFrontend) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    steps {
        script {
            name = "Build & Push Frontend via Kaniko"
            scriptContent = """
                #!/bin/bash
                set -e
                
                REGISTRY="%env.IMAGE_REGISTRY%"
                TAG="%env.IMAGE_TAG%"
                REG_USER="%env.REGISTRY_USER%"
                REG_PASS="%env.REGISTRY_PASSWORD%"
                SVC="frontend"
                
                echo "=== Kaniko Pod를 통한 ${'$'}SVC 컨테이너 빌드 및 푸시 시작 ==="
                if ! command -v kubectl &> /dev/null; then
                    curl -fsSL -o /tmp/kubectl "https://dl.k8s.io/release/v1.33.5/bin/linux/amd64/kubectl"
                    chmod +x /tmp/kubectl
                    export PATH="/tmp:${'$'}PATH"
                fi

                REG_HOST="${'$'}(echo "${'$'}REGISTRY" | cut -d/ -f1)"
                kubectl create secret docker-registry harbor-creds -n teamcity \
                  --docker-server="${'$'}REG_HOST" \
                  --docker-username="${'$'}REG_USER" \
                  --docker-password="${'$'}REG_PASS" \
                  --dry-run=client -o yaml | kubectl apply -f -

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

                kubectl wait --for=condition=Ready pod/"${'$'}POD_NAME" -n teamcity --timeout=60s || true
                kubectl logs -n teamcity "${'$'}POD_NAME" -f
                
                while true; do
                    PHASE="${'$'}(kubectl get pod "${'$'}POD_NAME" -n teamcity -o jsonpath='{.status.phase}' 2>/dev/null || true)"
                    if [ "${'$'}PHASE" = "Succeeded" ] || [ "${'$'}PHASE" = "Failed" ]; then
                        break
                    fi
                    sleep 1
                done
                
                STATUS="${'$'}(kubectl get pod "${'$'}POD_NAME" -n teamcity -o jsonpath='{.status.phase}')"
                if [ "${'$'}STATUS" != "Succeeded" ]; then
                    echo "ERROR: ${'$'}SVC 빌드 실패 (상태: ${'$'}STATUS)"
                    kubectl describe pod "${'$'}POD_NAME" -n teamcity || true
                    exit 1
                fi
                echo "${'$'}SVC 이미지 빌드 및 푸시 성공!"
                kubectl delete pod "${'$'}POD_NAME" -n teamcity --ignore-not-found=true
            """.trimIndent()
        }
    }
})

object BuildPaymentService : BuildType({
    id("BuildPaymentService")
    name = "2-4. Build Payment Service"
    description = "Kaniko를 이용한 Payment Service 도커 이미지 빌드 및 Harbor 푸시"

    vcs {
        root(DslContext.settingsRoot, "+:payment-service")
    }

    dependencies {
        snapshot(TestPaymentService) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    steps {
        script {
            name = "Build & Push Payment Service via Kaniko"
            scriptContent = """
                #!/bin/bash
                set -e
                
                REGISTRY="%env.IMAGE_REGISTRY%"
                TAG="%env.IMAGE_TAG%"
                REG_USER="%env.REGISTRY_USER%"
                REG_PASS="%env.REGISTRY_PASSWORD%"
                SVC="payment-service"
                
                echo "=== Kaniko Pod를 통한 ${'$'}SVC 컨테이너 빌드 및 푸시 시작 ==="
                if ! command -v kubectl &> /dev/null; then
                    curl -fsSL -o /tmp/kubectl "https://dl.k8s.io/release/v1.33.5/bin/linux/amd64/kubectl"
                    chmod +x /tmp/kubectl
                    export PATH="/tmp:${'$'}PATH"
                fi

                REG_HOST="${'$'}(echo "${'$'}REGISTRY" | cut -d/ -f1)"
                kubectl create secret docker-registry harbor-creds -n teamcity \
                  --docker-server="${'$'}REG_HOST" \
                  --docker-username="${'$'}REG_USER" \
                  --docker-password="${'$'}REG_PASS" \
                  --dry-run=client -o yaml | kubectl apply -f -

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

                kubectl wait --for=condition=Ready pod/"${'$'}POD_NAME" -n teamcity --timeout=60s || true
                kubectl logs -n teamcity "${'$'}POD_NAME" -f
                
                while true; do
                    PHASE="${'$'}(kubectl get pod "${'$'}POD_NAME" -n teamcity -o jsonpath='{.status.phase}' 2>/dev/null || true)"
                    if [ "${'$'}PHASE" = "Succeeded" ] || [ "${'$'}PHASE" = "Failed" ]; then
                        break
                    fi
                    sleep 1
                done
                
                STATUS="${'$'}(kubectl get pod "${'$'}POD_NAME" -n teamcity -o jsonpath='{.status.phase}')"
                if [ "${'$'}STATUS" != "Succeeded" ]; then
                    echo "ERROR: ${'$'}SVC 빌드 실패 (상태: ${'$'}STATUS)"
                    kubectl describe pod "${'$'}POD_NAME" -n teamcity || true
                    exit 1
                fi
                echo "${'$'}SVC 이미지 빌드 및 푸시 성공!"
                kubectl delete pod "${'$'}POD_NAME" -n teamcity --ignore-not-found=true
            """.trimIndent()
        }
    }
})

object BuildNotificationService : BuildType({
    id("BuildNotificationService")
    name = "2-5. Build Notification Service"
    description = "Kaniko를 이용한 Notification Service 도커 이미지 빌드 및 Harbor 푸시"

    vcs {
        root(DslContext.settingsRoot, "+:notification-service")
    }

    dependencies {
        snapshot(TestNotificationService) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    steps {
        script {
            name = "Build & Push Notification Service via Kaniko"
            scriptContent = """
                #!/bin/bash
                set -e
                
                REGISTRY="%env.IMAGE_REGISTRY%"
                TAG="%env.IMAGE_TAG%"
                REG_USER="%env.REGISTRY_USER%"
                REG_PASS="%env.REGISTRY_PASSWORD%"
                SVC="notification-service"
                
                echo "=== Kaniko Pod를 통한 ${'$'}SVC 컨테이너 빌드 및 푸시 시작 ==="
                if ! command -v kubectl &> /dev/null; then
                    curl -fsSL -o /tmp/kubectl "https://dl.k8s.io/release/v1.33.5/bin/linux/amd64/kubectl"
                    chmod +x /tmp/kubectl
                    export PATH="/tmp:${'$'}PATH"
                fi

                REG_HOST="${'$'}(echo "${'$'}REGISTRY" | cut -d/ -f1)"
                kubectl create secret docker-registry harbor-creds -n teamcity \
                  --docker-server="${'$'}REG_HOST" \
                  --docker-username="${'$'}REG_USER" \
                  --docker-password="${'$'}REG_PASS" \
                  --dry-run=client -o yaml | kubectl apply -f -

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

                kubectl wait --for=condition=Ready pod/"${'$'}POD_NAME" -n teamcity --timeout=60s || true
                kubectl logs -n teamcity "${'$'}POD_NAME" -f
                
                while true; do
                    PHASE="${'$'}(kubectl get pod "${'$'}POD_NAME" -n teamcity -o jsonpath='{.status.phase}' 2>/dev/null || true)"
                    if [ "${'$'}PHASE" = "Succeeded" ] || [ "${'$'}PHASE" = "Failed" ]; then
                        break
                    fi
                    sleep 1
                done
                
                STATUS="${'$'}(kubectl get pod "${'$'}POD_NAME" -n teamcity -o jsonpath='{.status.phase}')"
                if [ "${'$'}STATUS" != "Succeeded" ]; then
                    echo "ERROR: ${'$'}SVC 빌드 실패 (상태: ${'$'}STATUS)"
                    kubectl describe pod "${'$'}POD_NAME" -n teamcity || true
                    exit 1
                fi
                echo "${'$'}SVC 이미지 빌드 및 푸시 성공!"
                kubectl delete pod "${'$'}POD_NAME" -n teamcity --ignore-not-found=true
            """.trimIndent()
        }
    }
})

// ==============================================================================
// 3. Stage 3: 서비스별 배포 & 롤아웃 검증 (3-1 ~ 3-5)
// ==============================================================================
object DeployProductService : BuildType({
    id("DeployProductService")
    name = "3-1. Deploy Product Service"
    description = "쿠버네티스(RKE2) 클러스터에 Product Service 최신 이미지 롤아웃 및 상태 검증"
    type = Type.DEPLOYMENT

    vcs {
        root(DslContext.settingsRoot, "+:product-service", "+:k8s/03-product-service.yaml")
    }

    triggers {
        vcs {
            branchFilter = "+:refs/heads/main"
            triggerRules = """
                +:product-service/**
                +:k8s/03-product-service.yaml
                -:.teamcity/**
                -:k8s-gitops/**
                -:argocd/**
                -:README.md
            """.trimIndent()
        }
    }

    dependencies {
        snapshot(BuildProductService) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    steps {
        script {
            name = "Deploy Product Service to RKE2 and Rollout Check"
            scriptContent = """
                #!/bin/bash
                set -e
                
                NAMESPACE="%env.K8S_NAMESPACE%"
                REGISTRY="%env.IMAGE_REGISTRY%"
                TAG="latest"
                SVC="product-service"
                YAML_FILE="03-product-service.yaml"
                
                if ! command -v kubectl &> /dev/null; then
                    curl -fsSL -o /tmp/kubectl "https://dl.k8s.io/release/v1.33.5/bin/linux/amd64/kubectl"
                    chmod +x /tmp/kubectl
                    export PATH="/tmp:${'$'}PATH"
                fi
                
                if [ ! -f "k8s/${'$'}YAML_FILE" ]; then
                    echo "k8s/${'$'}YAML_FILE 매니페스트가 없어 GitHub 저장소에서 최신 코드를 다운로드합니다..."
                    rm -rf /tmp/msa-demo-repo
                    git clone --depth 1 https://github.com/smjin1210/msa-demo.git /tmp/msa-demo-repo
                    cd /tmp/msa-demo-repo
                fi

                echo "=== 1. 네임스페이스 및 ${'$'}SVC 리소스 배포 ==="
                kubectl get namespace "${'$'}NAMESPACE" 2>/dev/null || kubectl create namespace "${'$'}NAMESPACE"
                kubectl apply -f k8s/${'$'}YAML_FILE

                echo "=== 2. ${'$'}SVC 신규 이미지 롤아웃 트리거 ==="
                kubectl set image deployment/${'$'}SVC ${'$'}SVC="${'$'}REGISTRY/${'$'}SVC:${'$'}TAG" -n "${'$'}NAMESPACE" || true
                kubectl rollout restart deployment/${'$'}SVC -n "${'$'}NAMESPACE"

                echo "=== 3. 롤아웃 상태 검증 (Timeout: 180초) ==="
                kubectl rollout status deployment/${'$'}SVC -n "${'$'}NAMESPACE" --timeout=180s

                echo "=== 4. 배포 파드 및 엔드포인트 헬스체크 확인 ==="
                kubectl get pods -n "${'$'}NAMESPACE" -l app=${'$'}SVC -o wide
            """.trimIndent()
        }
    }
})

object DeployOrderService : BuildType({
    id("DeployOrderService")
    name = "3-2. Deploy Order Service"
    description = "쿠버네티스(RKE2) 클러스터에 Order Service 최신 이미지 롤아웃 및 상태 검증"
    type = Type.DEPLOYMENT

    vcs {
        root(DslContext.settingsRoot, "+:order-service", "+:k8s/04-order-service.yaml")
    }

    triggers {
        vcs {
            branchFilter = "+:refs/heads/main"
            triggerRules = """
                +:order-service/**
                +:k8s/04-order-service.yaml
                -:.teamcity/**
                -:k8s-gitops/**
                -:argocd/**
                -:README.md
            """.trimIndent()
        }
    }

    dependencies {
        snapshot(BuildOrderService) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    steps {
        script {
            name = "Deploy Order Service to RKE2 and Rollout Check"
            scriptContent = """
                #!/bin/bash
                set -e
                
                NAMESPACE="%env.K8S_NAMESPACE%"
                REGISTRY="%env.IMAGE_REGISTRY%"
                TAG="latest"
                SVC="order-service"
                YAML_FILE="04-order-service.yaml"
                
                if ! command -v kubectl &> /dev/null; then
                    curl -fsSL -o /tmp/kubectl "https://dl.k8s.io/release/v1.33.5/bin/linux/amd64/kubectl"
                    chmod +x /tmp/kubectl
                    export PATH="/tmp:${'$'}PATH"
                fi
                
                if [ ! -f "k8s/${'$'}YAML_FILE" ]; then
                    echo "k8s/${'$'}YAML_FILE 매니페스트가 없어 GitHub 저장소에서 최신 코드를 다운로드합니다..."
                    rm -rf /tmp/msa-demo-repo
                    git clone --depth 1 https://github.com/smjin1210/msa-demo.git /tmp/msa-demo-repo
                    cd /tmp/msa-demo-repo
                fi

                echo "=== 1. 네임스페이스 및 ${'$'}SVC 리소스 배포 ==="
                kubectl get namespace "${'$'}NAMESPACE" 2>/dev/null || kubectl create namespace "${'$'}NAMESPACE"
                if [ -f "k8s/02-postgres.yaml" ]; then
                    kubectl apply -f k8s/02-postgres.yaml
                fi
                kubectl apply -f k8s/${'$'}YAML_FILE

                echo "=== 2. ${'$'}SVC 신규 이미지 롤아웃 트리거 ==="
                kubectl set image deployment/${'$'}SVC ${'$'}SVC="${'$'}REGISTRY/${'$'}SVC:${'$'}TAG" -n "${'$'}NAMESPACE" || true
                kubectl rollout restart deployment/${'$'}SVC -n "${'$'}NAMESPACE"

                echo "=== 3. 롤아웃 상태 검증 (Timeout: 180초) ==="
                kubectl rollout status deployment/${'$'}SVC -n "${'$'}NAMESPACE" --timeout=180s

                echo "=== 4. 배포 파드 및 엔드포인트 헬스체크 확인 ==="
                kubectl get pods -n "${'$'}NAMESPACE" -l app=${'$'}SVC -o wide
            """.trimIndent()
        }
    }
})

object DeployFrontend : BuildType({
    id("DeployFrontend")
    name = "3-3. Deploy Frontend"
    description = "쿠버네티스(RKE2) 클러스터에 Frontend 최신 이미지 롤아웃 및 상태 검증"
    type = Type.DEPLOYMENT

    vcs {
        root(DslContext.settingsRoot, "+:frontend", "+:k8s/05-frontend.yaml")
    }

    triggers {
        vcs {
            branchFilter = "+:refs/heads/main"
            triggerRules = """
                +:frontend/**
                +:k8s/05-frontend.yaml
                -:.teamcity/**
                -:k8s-gitops/**
                -:argocd/**
                -:README.md
            """.trimIndent()
        }
    }

    dependencies {
        snapshot(BuildFrontend) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    steps {
        script {
            name = "Deploy Frontend to RKE2 and Rollout Check"
            scriptContent = """
                #!/bin/bash
                set -e
                
                NAMESPACE="%env.K8S_NAMESPACE%"
                REGISTRY="%env.IMAGE_REGISTRY%"
                TAG="latest"
                SVC="frontend"
                YAML_FILE="05-frontend.yaml"
                
                if ! command -v kubectl &> /dev/null; then
                    curl -fsSL -o /tmp/kubectl "https://dl.k8s.io/release/v1.33.5/bin/linux/amd64/kubectl"
                    chmod +x /tmp/kubectl
                    export PATH="/tmp:${'$'}PATH"
                fi
                
                if [ ! -f "k8s/${'$'}YAML_FILE" ]; then
                    echo "k8s/${'$'}YAML_FILE 매니페스트가 없어 GitHub 저장소에서 최신 코드를 다운로드합니다..."
                    rm -rf /tmp/msa-demo-repo
                    git clone --depth 1 https://github.com/smjin1210/msa-demo.git /tmp/msa-demo-repo
                    cd /tmp/msa-demo-repo
                fi

                echo "=== 1. 네임스페이스 및 ${'$'}SVC 리소스 배포 ==="
                kubectl get namespace "${'$'}NAMESPACE" 2>/dev/null || kubectl create namespace "${'$'}NAMESPACE"
                kubectl apply -f k8s/${'$'}YAML_FILE

                echo "=== 2. ${'$'}SVC 신규 이미지 롤아웃 트리거 ==="
                kubectl set image deployment/${'$'}SVC ${'$'}SVC="${'$'}REGISTRY/${'$'}SVC:${'$'}TAG" -n "${'$'}NAMESPACE" || true
                kubectl rollout restart deployment/${'$'}SVC -n "${'$'}NAMESPACE"

                echo "=== 3. 롤아웃 상태 검증 (Timeout: 180초) ==="
                kubectl rollout status deployment/${'$'}SVC -n "${'$'}NAMESPACE" --timeout=180s

                echo "=== 4. 배포 파드 및 엔드포인트 헬스체크 확인 ==="
                kubectl get pods -n "${'$'}NAMESPACE" -l app=${'$'}SVC -o wide
            """.trimIndent()
        }
    }
})

object DeployPaymentService : BuildType({
    id("DeployPaymentService")
    name = "3-4. Deploy Payment Service"
    description = "쿠버네티스(RKE2) 클러스터에 Payment Service 최신 이미지 롤아웃 및 상태 검증"
    type = Type.DEPLOYMENT

    vcs {
        root(DslContext.settingsRoot, "+:payment-service", "+:k8s/06-payment-service.yaml")
    }

    triggers {
        vcs {
            branchFilter = "+:refs/heads/main"
            triggerRules = """
                +:payment-service/**
                +:k8s/06-payment-service.yaml
                -:.teamcity/**
                -:k8s-gitops/**
                -:argocd/**
                -:README.md
            """.trimIndent()
        }
    }

    dependencies {
        snapshot(BuildPaymentService) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    steps {
        script {
            name = "Deploy Payment Service to RKE2 and Rollout Check"
            scriptContent = """
                #!/bin/bash
                set -e
                
                NAMESPACE="%env.K8S_NAMESPACE%"
                REGISTRY="%env.IMAGE_REGISTRY%"
                TAG="latest"
                SVC="payment-service"
                YAML_FILE="06-payment-service.yaml"
                
                if ! command -v kubectl &> /dev/null; then
                    curl -fsSL -o /tmp/kubectl "https://dl.k8s.io/release/v1.33.5/bin/linux/amd64/kubectl"
                    chmod +x /tmp/kubectl
                    export PATH="/tmp:${'$'}PATH"
                fi
                
                if [ ! -f "k8s/${'$'}YAML_FILE" ]; then
                    echo "k8s/${'$'}YAML_FILE 매니페스트가 없어 GitHub 저장소에서 최신 코드를 다운로드합니다..."
                    rm -rf /tmp/msa-demo-repo
                    git clone --depth 1 https://github.com/smjin1210/msa-demo.git /tmp/msa-demo-repo
                    cd /tmp/msa-demo-repo
                fi

                echo "=== 1. 네임스페이스 및 ${'$'}SVC 리소스 배포 ==="
                kubectl get namespace "${'$'}NAMESPACE" 2>/dev/null || kubectl create namespace "${'$'}NAMESPACE"
                kubectl apply -f k8s/${'$'}YAML_FILE

                echo "=== 2. ${'$'}SVC 신규 이미지 롤아웃 트리거 ==="
                kubectl set image deployment/${'$'}SVC ${'$'}SVC="${'$'}REGISTRY/${'$'}SVC:${'$'}TAG" -n "${'$'}NAMESPACE" || true
                kubectl rollout restart deployment/${'$'}SVC -n "${'$'}NAMESPACE"

                echo "=== 3. 롤아웃 상태 검증 (Timeout: 180초) ==="
                kubectl rollout status deployment/${'$'}SVC -n "${'$'}NAMESPACE" --timeout=180s

                echo "=== 4. 배포 파드 및 엔드포인트 헬스체크 확인 ==="
                kubectl get pods -n "${'$'}NAMESPACE" -l app=${'$'}SVC -o wide
            """.trimIndent()
        }
    }
})

object DeployNotificationService : BuildType({
    id("DeployNotificationService")
    name = "3-5. Deploy Notification Service"
    description = "쿠버네티스(RKE2) 클러스터에 Notification Service 최신 이미지 롤아웃 및 상태 검증"
    type = Type.DEPLOYMENT

    vcs {
        root(DslContext.settingsRoot, "+:notification-service", "+:k8s/07-notification-service.yaml")
    }

    triggers {
        vcs {
            branchFilter = "+:refs/heads/main"
            triggerRules = """
                +:notification-service/**
                +:k8s/07-notification-service.yaml
                -:.teamcity/**
                -:k8s-gitops/**
                -:argocd/**
                -:README.md
            """.trimIndent()
        }
    }

    dependencies {
        snapshot(BuildNotificationService) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    steps {
        script {
            name = "Deploy Notification Service to RKE2 and Rollout Check"
            scriptContent = """
                #!/bin/bash
                set -e
                
                NAMESPACE="%env.K8S_NAMESPACE%"
                REGISTRY="%env.IMAGE_REGISTRY%"
                TAG="latest"
                SVC="notification-service"
                YAML_FILE="07-notification-service.yaml"
                
                if ! command -v kubectl &> /dev/null; then
                    curl -fsSL -o /tmp/kubectl "https://dl.k8s.io/release/v1.33.5/bin/linux/amd64/kubectl"
                    chmod +x /tmp/kubectl
                    export PATH="/tmp:${'$'}PATH"
                fi
                
                if [ ! -f "k8s/${'$'}YAML_FILE" ]; then
                    echo "k8s/${'$'}YAML_FILE 매니페스트가 없어 GitHub 저장소에서 최신 코드를 다운로드합니다..."
                    rm -rf /tmp/msa-demo-repo
                    git clone --depth 1 https://github.com/smjin1210/msa-demo.git /tmp/msa-demo-repo
                    cd /tmp/msa-demo-repo
                fi

                echo "=== 1. 네임스페이스 및 ${'$'}SVC 리소스 배포 ==="
                kubectl get namespace "${'$'}NAMESPACE" 2>/dev/null || kubectl create namespace "${'$'}NAMESPACE"
                kubectl apply -f k8s/${'$'}YAML_FILE

                echo "=== 2. ${'$'}SVC 신규 이미지 롤아웃 트리거 ==="
                kubectl set image deployment/${'$'}SVC ${'$'}SVC="${'$'}REGISTRY/${'$'}SVC:${'$'}TAG" -n "${'$'}NAMESPACE" || true
                kubectl rollout restart deployment/${'$'}SVC -n "${'$'}NAMESPACE"

                echo "=== 3. 롤아웃 상태 검증 (Timeout: 180초) ==="
                kubectl rollout status deployment/${'$'}SVC -n "${'$'}NAMESPACE" --timeout=180s

                echo "=== 4. 배포 파드 및 엔드포인트 헬스체크 확인 ==="
                kubectl get pods -n "${'$'}NAMESPACE" -l app=${'$'}SVC -o wide
            """.trimIndent()
        }
    }
})
