pipeline {
    agent {
        kubernetes {
            yaml '''
apiVersion: v1
kind: Pod
metadata:
  labels:
    jenkins: agent
spec:
  serviceAccountName: jenkins
  containers:
  - name: python
    image: python:3.12-slim
    command:
    - cat
    tty: true
  - name: node
    image: node:20-alpine
    command:
    - cat
    tty: true
  - name: tools
    image: alpine/k8s:1.32.2
    command:
    - cat
    tty: true
'''
        }
    }
    triggers {
        pollSCM('H/2 * * * *')
    }
    environment {
        HARBOR_REGISTRY = '43.203.226.163:30002/msa-demo'
        HARBOR_HOST = '43.203.226.163:30002'
        HARBOR_CREDS = credentials('harbor-credentials')
        GITHUB_CREDS = credentials('github-credentials')
        ARGOCD_SERVER = 'http://argocd-server.argocd.svc'
        ARGOCD_USER = 'admin'
        ARGOCD_PASS = 'tangun123!'
        IMAGE_TAG = "jk-${BUILD_NUMBER}"
    }
    stages {
        stage('Unit Tests') {
            parallel {
                stage('Product Service Test') {
                    steps {
                        container('python') {
                            sh '''
                                echo "=== Product Service 단위 테스트 실행 ==="
                                cd product-service
                                python3 -m venv .venv
                                . .venv/bin/activate
                                pip install --no-cache-dir -r requirements.txt
                                pytest -v
                            '''
                        }
                    }
                }
                stage('Order Service Test') {
                    steps {
                        container('python') {
                            sh '''
                                echo "=== Order Service 단위 테스트 실행 ==="
                                cd order-service
                                python3 -m venv .venv
                                . .venv/bin/activate
                                pip install --no-cache-dir -r requirements.txt
                                pytest -v
                            '''
                        }
                    }
                }
                stage('Frontend Test & Build') {
                    steps {
                        container('node') {
                            sh '''
                                echo "=== Frontend 테스트 및 정적 번들 빌드 검증 ==="
                                cd frontend
                                npm ci || npm install
                                npm test
                                npm run build
                            '''
                        }
                    }
                }
            }
        }

        stage('Build & Push Images') {
            steps {
                container('tools') {
                    sh '''
                        set -e
                        echo "=== Kaniko Pod를 통한 컨테이너 이미지 빌드 및 Harbor 푸시 ==="
                        
                        # Harbor 인증 시크릿 동기화
                        kubectl create secret docker-registry harbor-creds -n jenkins \
                          --docker-server="${HARBOR_HOST}" \
                          --docker-username="${HARBOR_CREDS_USR}" \
                          --docker-password="${HARBOR_CREDS_PSW}" \
                          --dry-run=client -o yaml | kubectl apply -f -
                        
                        for SVC in product-service order-service frontend; do
                            POD_NAME="kaniko-jk-${SVC}-${BUILD_NUMBER}"
                            echo "--------------------------------------------------"
                            echo "Kaniko Pod 생성: ${POD_NAME} (${SVC})"
                            echo "--------------------------------------------------"
                            kubectl delete pod "${POD_NAME}" -n jenkins --ignore-not-found=true
                            
                            cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: ${POD_NAME}
  namespace: jenkins
spec:
  restartPolicy: Never
  containers:
  - name: kaniko
    image: gcr.io/kaniko-project/executor:v1.23.2-debug
    args:
    - --context=git://github.com/smjin1210/msa-demo.git#refs/heads/main
    - --context-sub-path=${SVC}
    - --destination=${HARBOR_REGISTRY}/${SVC}:${IMAGE_TAG}
    - --destination=${HARBOR_REGISTRY}/${SVC}:latest
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

                            echo "${POD_NAME} 기동 대기 중..."
                            kubectl wait --for=condition=Ready pod/"${POD_NAME}" -n jenkins --timeout=60s || true
                            kubectl logs -n jenkins "${POD_NAME}" -f
                            
                            echo "${POD_NAME} 완료 대기 중..."
                            while true; do
                                PHASE="$(kubectl get pod "${POD_NAME}" -n jenkins -o jsonpath='{.status.phase}' 2>/dev/null || true)"
                                if [ "${PHASE}" = "Succeeded" ] || [ "${PHASE}" = "Failed" ]; then
                                    break
                                fi
                                sleep 1
                            done
                            
                            STATUS="$(kubectl get pod "${POD_NAME}" -n jenkins -o jsonpath='{.status.phase}')"
                            if [ "${STATUS}" != "Succeeded" ]; then
                                echo "ERROR: ${SVC} 이미지 빌드 실패 (상태: ${STATUS})"
                                kubectl describe pod "${POD_NAME}" -n jenkins || true
                                exit 1
                            fi
                            echo "SUCCESS: ${SVC} 이미지 빌드 및 푸시 성공 (${IMAGE_TAG})"
                            kubectl delete pod "${POD_NAME}" -n jenkins --ignore-not-found=true
                        done
                    '''
                }
            }
        }

        stage('Update GitOps & Sync ArgoCD') {
            steps {
                container('tools') {
                    sh '''
                        set -e
                        echo "=== GitOps 매니페스트 이미지 태그 업데이트 및 Git 푸시 ==="
                        git config --global --add safe.directory '*'
                        git config --global user.name "jenkins-bot"
                        git config --global user.email "jenkins@msa-demo.local"
                        
                        git checkout main || git checkout -b main
                        git pull origin main || true
                        
                        sed -i "s|image: .*/product-service:.*|image: ${HARBOR_REGISTRY}/product-service:${IMAGE_TAG}|g" k8s-gitops/03-product-service.yaml
                        sed -i "s|image: .*/order-service:.*|image: ${HARBOR_REGISTRY}/order-service:${IMAGE_TAG}|g" k8s-gitops/04-order-service.yaml
                        sed -i "s|image: .*/frontend:.*|image: ${HARBOR_REGISTRY}/frontend:${IMAGE_TAG}|g" k8s-gitops/05-frontend.yaml
                        
                        git add k8s-gitops/
                        if git diff --staged --quiet; then
                            echo "매니페스트 변경 사항 없음"
                        else
                            git commit -m "chore(gitops): update image tags to ${IMAGE_TAG} [skip ci]"
                            git push https://${GITHUB_CREDS_USR}:${GITHUB_CREDS_PSW}@github.com/smjin1210/msa-demo.git main
                            echo "GitOps 매니페스트 GitHub 푸시 완료!"
                        fi
                        
                        echo "=== Argo CD 즉시 동기화(Sync) 요청 ==="
                        TOKEN=$(curl -s -X POST "${ARGOCD_SERVER}/api/v1/session" \
                          -H "Content-Type: application/json" \
                          -d "{\"username\":\"${ARGOCD_USER}\",\"password\":\"${ARGOCD_PASS}\"}" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
                        
                        if [ -n "${TOKEN}" ]; then
                            echo "Argo CD API 호출..."
                            curl -s -X POST "${ARGOCD_SERVER}/api/v1/applications/msa-demo-gitops/sync" \
                              -H "Authorization: Bearer ${TOKEN}" \
                              -H "Content-Type: application/json" \
                              -d '{"prune":true,"dryRun":false}' || true
                            echo ""
                            echo "Argo CD 즉시 동기화 요청 완료!"
                        else
                            echo "WARNING: Argo CD 토큰 발급 실패. 자동 감지 주기를 통해 동기화됩니다."
                        fi
                    '''
                }
            }
        }
    }
}
