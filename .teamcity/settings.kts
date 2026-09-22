package .teamcity

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs

/*
 * TeamCity Kotlin DSL Configuration for Project: K8sTest
 * MSA 주문 시스템 CI/CD 파이프라인
 */

version = "2024.03"

project {
    id("K8sTest")
    name = "K8sTest"
    description = "MSA 주문 시스템 CI/CD 파이프라인 (RKE2 + Kubernetes Cloud Profile)"

    params {
        param("env.IMAGE_REGISTRY", "YOUR_REGISTRY_HOST/YOUR_NAMESPACE")
        param("env.IMAGE_TAG", "%build.number%")
        param("env.K8S_NAMESPACE", "msa-demo")
        password("env.REGISTRY_PASSWORD", "credentialsJSON:CHANGE_ME", display = ParameterDisplay.HIDDEN, label = "Container Registry Password")
        param("env.REGISTRY_USER", "registry_user")
    }

    buildType(RunUnitTests)
    buildType(BuildAndPushImages)
    buildType(DeployAndVerify)

    buildTypesOrder = listOf(RunUnitTests, BuildAndPushImages, DeployAndVerify)
}

object RunUnitTests : BuildType({
    id("K8sTest_RunUnitTests")
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
                echo "=== Product Service 테스트 실행 ==="
                cd product-service
                python3 -m pip install -r requirements.txt
                python3 -m pytest -v
            """.trimIndent()
        }

        script {
            name = "Test Order Service (FastAPI + SQLite Test DB)"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "=== Order Service 테스트 실행 ==="
                cd order-service
                python3 -m pip install -r requirements.txt
                python3 -m pytest -v
            """.trimIndent()
        }

        script {
            name = "Test & Build Frontend (React + Vite)"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "=== Frontend 테스트 및 프로덕션 빌드 검증 ==="
                cd frontend
                npm install
                npm test
                npm run build
            """.trimIndent()
        }
    }
})

object BuildAndPushImages : BuildType({
    id("K8sTest_BuildAndPushImages")
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
                
                echo "=== 이미지 빌드 및 푸시 설정 ==="
                echo "대상 레지스트리: ${'$'}REGISTRY"
                echo "이미지 태그: ${'$'}TAG"
                
                # 1. Product Service 이미지 빌드 (Kaniko Executor 호출)
                echo "Building product-service..."
                /kaniko/executor \
                  --context="dir://${'$'}{TEAMCITY_BUILD_DIR}/product-service" \
                  --dockerfile="${'$'}{TEAMCITY_BUILD_DIR}/product-service/Dockerfile" \
                  --destination="${'$'}REGISTRY/product-service:${'$'}TAG" \
                  --destination="${'$'}REGISTRY/product-service:latest" \
                  --cache=true

                # 2. Order Service 이미지 빌드
                echo "Building order-service..."
                /kaniko/executor \
                  --context="dir://${'$'}{TEAMCITY_BUILD_DIR}/order-service" \
                  --dockerfile="${'$'}{TEAMCITY_BUILD_DIR}/order-service/Dockerfile" \
                  --destination="${'$'}REGISTRY/order-service:${'$'}TAG" \
                  --destination="${'$'}REGISTRY/order-service:latest" \
                  --cache=true

                # 3. Frontend 이미지 빌드
                echo "Building frontend..."
                /kaniko/executor \
                  --context="dir://${'$'}{TEAMCITY_BUILD_DIR}/frontend" \
                  --dockerfile="${'$'}{TEAMCITY_BUILD_DIR}/frontend/Dockerfile" \
                  --destination="${'$'}REGISTRY/frontend:${'$'}TAG" \
                  --destination="${'$'}REGISTRY/frontend:latest" \
                  --cache=true
            """.trimIndent()
        }
    }
})

object DeployAndVerify : BuildType({
    id("K8sTest_DeployAndVerify")
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
                
                echo "=== 1. 네임스페이스 및 시크릿 사전 확인 ==="
                kubectl get namespace "${'$'}NAMESPACE" || kubectl apply -f k8s/00-namespace.yaml
                
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
