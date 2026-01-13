# nop-cicd-design Skill

## Skill 概述

**名称**: nop-cicd-design（CI/CD设计）

**定位**: 基于所有前序阶段的产物，设计CI/CD流程，实现持续集成、持续部署、质量保证

**输入**:
1. 所有前序阶段的产物（代码、测试、配置等）
2. CI/CD需求描述（多环境支持、部署策略等）
3. 质量保证要求（代码质量、测试覆盖率等）

**输出**:
1. GitHub Actions工作流（`.github/workflows/ci.yml`）
2. Dockerfile配置
3. K8s部署配置（`k8s-deployment.yml`）
4. 多环境配置（dev/test/prod）
5. CI/CD文档（`cicd-guide.md`）

**能力**:
- 设计持续集成流程（构建、测试、质量检查）
- 设计持续部署流程（镜像构建、K8s部署）
- 设计多环境支持（dev、test、prod）
- 设计监控和日志集成
- 设计回滚策略

**依赖**:
- GitHub Actions文档（https://docs.github.com/en/actions）
- Docker文档（https://docs.docker.com/）
- Kubernetes文档（https://kubernetes.io/docs/）

## 核心原则

### 1. 持续集成（CI）
- **自动构建**：每次push/PR触发自动构建
- **自动测试**：运行单元测试、集成测试、端到端测试
- **质量检查**：Checkstyle、PMD、SonarQube
- **快速反馈**：构建时间< 10分钟

### 2. 持续部署（CD）
- **自动部署**：合并到main分支自动部署到测试环境
- **手动部署**：生产环境需要手动审批
- **零停机部署**：使用滚动更新
- **健康检查**：部署后自动进行健康检查

### 3. 多环境支持
- **开发环境（dev）**：用于开发调试
- **测试环境（test）**：用于集成测试
- **生产环境（prod）**：用于生产运行

### 4. 可观测性
- **监控**：Prometheus + Grafana
- **日志**：ELK Stack（Elasticsearch、Logstash、Kibana）
- **告警**：基于Prometheus的自定义告警

## 工作流程

### 阶段1：CI流程设计

**步骤1.1：设计构建流程**
```
设计Maven构建流程：
1. 代码检出（checkout）
2. 设置JDK环境（setup-java）
3. 依赖缓存（cache）
4. 编译（mvn compile）
5. 单元测试（mvn test）
6. 集成测试（mvn integration-test）
7. 质量检查（mvn checkstyle:check）
8. 构建镜像（docker build）
9. 推送镜像（docker push）
```

**步骤1.2：设计测试流程**
```
设计测试流程：
1. 单元测试（JUnit 5）
2. 集成测试（Nop AutoTest）
3. 端到端测试（Playwright）
4. 测试覆盖率检查
```

**步骤1.3：设计质量检查流程**
```
设计质量检查流程：
1. 代码风格检查（Checkstyle）
2. 静态代码分析（PMD）
3. 代码复杂度分析（SonarQube）
4. 安全扫描（Snyk）
```

**步骤1.4：生成GitHub Actions配置**
```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  REGISTRY: docker.io
  IMAGE_NAME: myapp

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Build with Maven
        run: mvn clean compile -DskipTests

      - name: Run unit tests
        run: mvn test

      - name: Run integration tests
        run: mvn integration-test

      - name: Check code style
        run: mvn checkstyle:check

      - name: SonarQube analysis
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        run: mvn sonar:sonar

      - name: Build Docker image
        run: docker build -t ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }} .

      - name: Log in to Docker Hub
        uses: docker/login-action@v2
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ secrets.DOCKER_USERNAME }}
          password: ${{ secrets.DOCKER_PASSWORD }}

      - name: Push Docker image
        run: docker push ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}

  test:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Run end-to-end tests
        run: mvn test -Dtest=*EndToEnd*

  deploy-dev:
    needs: [build, test]
    if: github.ref == 'refs/heads/develop'
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to dev
        run: |
          kubectl config use-context dev-cluster
          kubectl set image deployment/myapp myapp=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
          kubectl rollout status deployment/myapp

  deploy-test:
    needs: [build, test]
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to test
        run: |
          kubectl config use-context test-cluster
          kubectl set image deployment/myapp myapp=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
          kubectl rollout status deployment/myapp

  deploy-prod:
    needs: [build, test]
    if: github.ref == 'refs/heads/main'
    environment:
      name: production
      url: https://myapp.example.com
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to production
        run: |
          kubectl config use-context prod-cluster
          kubectl set image deployment/myapp myapp=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
          kubectl rollout status deployment/myapp
```

### 阶段2：CD流程设计

**步骤2.1：设计部署流程**
```
设计部署流程：
1. 部署到dev环境（develop分支，自动）
2. 部署到test环境（main分支，自动）
3. 部署到prod环境（main分支，手动审批）
4. 健康检查
5. 回滚（如果健康检查失败）
```

**步骤2.2：设计K8s部署配置**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
  namespace: default
  labels:
    app: myapp
    version: v1
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: myapp
  template:
    metadata:
      labels:
        app: myapp
        version: v1
    spec:
      containers:
      - name: myapp
        image: myapp:${IMAGE_TAG}
        ports:
        - containerPort: 8080
          name: http
          protocol: TCP
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: myapp-secrets
              key: database-url
        - name: REDIS_HOST
          valueFrom:
            configMapKeyRef:
              name: myapp-config
              key: redis-host
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        volumeMounts:
        - name: config
          mountPath: /app/config
      volumes:
      - name: config
        configMap:
          name: myapp-config
---
apiVersion: v1
kind: Service
metadata:
  name: myapp-service
  namespace: default
  labels:
    app: myapp
spec:
  selector:
    app: myapp
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
    name: http
  type: LoadBalancer
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: myapp-hpa
  namespace: default
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: myapp
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

**步骤2.3：设计ConfigMap**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: myapp-config
  namespace: default
data:
  application.yml: |
    spring:
      profiles:
        active: ${SPRING_PROFILES_ACTIVE:prod}
      datasource:
        url: ${DATABASE_URL}
        username: ${DATABASE_USERNAME}
        password: ${DATABASE_PASSWORD}
      redis:
        host: ${REDIS_HOST}
        port: 6379
    logging:
      level:
        io.nop: DEBUG
        org.springframework: INFO
    management:
      endpoints:
        web:
          exposure:
            include: health,info,metrics
      health:
        probes:
          enabled: true
  redis-host: "redis-service"
```

**步骤2.4：设计Secret**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: myapp-secrets
  namespace: default
type: Opaque
data:
  database-url: <base64-encoded-url>
  database-username: <base64-encoded-username>
  database-password: <base64-encoded-password>
```

### 阶段3：Docker配置生成

**步骤3.1：生成Dockerfile**
```dockerfile
# 构建阶段
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**步骤3.2：生成.dockerignore**
```
# Maven
target/
pom.xml.tag
pom.xml.releaseBackup
pom.xml.versionsBackup
pom.xml.next
release.properties

# IDE
.idea/
*.iml
.vscode/
.settings/
.project
.classpath

# Git
.git/
.gitignore

# Documentation
docs/
README.md
CHANGELOG.md
```

### 阶段4：监控和日志集成

**步骤4.1：设计Prometheus配置**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: monitoring
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
      evaluation_interval: 15s

    scrape_configs:
      - job_name: 'myapp'
        static_configs:
          - targets: ['myapp-service.default.svc.cluster.local:8080']
        metrics_path: '/actuator/prometheus'
```

**步骤4.2：设计Grafana配置**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-dashboards
  namespace: monitoring
data:
  myapp-dashboard.json: |
    {
      "dashboard": {
        "title": "MyApp Dashboard",
        "panels": [
          {
            "title": "Request Rate",
            "targets": [
              {
                "expr": "rate(http_server_requests_seconds_count[1m])"
              }
            ]
          },
          {
            "title": "Error Rate",
            "targets": [
              {
                "expr": "rate(http_server_requests_seconds_count{status=~'5..'}[1m])"
              }
            ]
          }
        ]
      }
    }
```

**步骤4.3：设计ELK Stack配置**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: logstash-config
  namespace: logging
data:
  logstash.conf: |
    input {
      http {
        port => 5000
        codec => json
      }
    }

    filter {
      if [app] == "myapp" {
        grok {
          match => { "message" => "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:log_message}" }
        }
        date {
          match => [ "timestamp", "ISO8601" ]
        }
      }
    }

    output {
      elasticsearch {
        hosts => ["elasticsearch:9200"]
        index => "myapp-%{+YYYY.MM.dd}"
      }
    }
```

### 阶段5：CI/CD文档生成

生成CI/CD文档，包括：
- CI流程说明
- CD流程说明
- 多环境配置说明
- 监控和日志说明
- 回滚策略说明
- 故障排查指南

## AI推理策略

### 1. CI流程推理
- **构建流程设计**：
  - 识别需要缓存的内容（Maven依赖、Docker层）
  - 识别需要并行的任务（单元测试、集成测试）
  - 识别需要串行的任务（构建→测试→部署）

- **测试流程设计**：
  - 识别需要运行的测试类型（单元测试、集成测试、端到端测试）
  - 设计测试执行顺序

- **质量检查流程设计**：
  - 识别需要检查的质量指标（代码风格、代码复杂度、安全漏洞）
  - 设计质量检查顺序

### 2. CD流程推理
- **部署策略推理**：
  - 识别部署环境（dev、test、prod）
  - 设计部署顺序（dev → test → prod）
  - 设计部署触发条件（push、merge、手动）

- **回滚策略推理**：
  - 识别回滚触发条件（健康检查失败、错误率过高）
  - 设计回滚流程（K8s rollback、镜像回滚）

### 3. 监控配置推理
- **监控指标识别**：
  - 应用指标（请求率、响应时间、错误率）
  - 系统指标（CPU、内存、磁盘）
  - 业务指标（订单量、支付成功率）

- **告警规则识别**：
  - 识别需要告警的指标
  - 设计告警阈值
  - 设计告警通知方式

## 验证点

### 1. CI流程验证
- [ ] 构建流程是否正确
- [ ] 测试流程是否完整
- [ ] 质量检查流程是否完整
- [ ] 构建时间是否合理（< 10分钟）

### 2. CD流程验证
- [ ] 部署流程是否正确
- [ ] 健康检查是否配置
- [ ] 回滚策略是否合理

### 3. K8s配置验证
- [ ] Deployment配置是否正确
- [ ] Service配置是否正确
- [ ] HPA配置是否正确
- [ ] 资源限制是否合理

### 4. 监控配置验证
- [ ] Prometheus配置是否正确
- [ ] Grafana Dashboard是否完整
- [ ] 告警规则是否合理

## 输出产物

### 1. GitHub Actions工作流（`.github/workflows/ci.yml`）
```yaml
name: CI/CD Pipeline
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]
jobs:
  build:
    # ...
  test:
    # ...
  deploy-dev:
    # ...
  deploy-test:
    # ...
  deploy-prod:
    # ...
```

### 2. Dockerfile
```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
# ...
FROM eclipse-temurin:17-jre
# ...
```

### 3. K8s部署配置（`k8s-deployment.yml`）
```yaml
apiVersion: apps/v1
kind: Deployment
# ...
```

### 4. 多环境配置
- dev环境配置
- test环境配置
- prod环境配置

### 5. CI/CD文档（`cicd-guide.md`）
包含：
- CI流程说明
- CD流程说明
- 多环境配置说明
- 监控和日志说明
- 回滚策略说明
- 故障排查指南

## 下一步工作

当前skill完成CI/CD设计，生成以下产物：
1. GitHub Actions工作流（`.github/workflows/ci.yml`）
2. Dockerfile配置
3. K8s部署配置（`k8s-deployment.yml`）
4. 多环境配置
5. CI/CD文档（`cicd-guide.md`）

这些产物将传递给下一个skill（nop-deploy）用于自动化部署。

