# nop-deploy Skill

## Skill 概述

**名称**: nop-deploy（自动化部署）

**定位**: 基于CI/CD配置和部署需求，生成完整的自动化部署脚本、监控配置和运维文档

**输入**:
1. 所有前序阶段的产物（代码、配置、镜像等）
2. CI/CD配置（GitHub Actions、Docker、K8s）
3. 部署需求（部署环境、部署策略、回滚策略）

**输出**:
1. 部署脚本（Shell/Python）
2. 监控配置（Prometheus、Grafana）
3. 日志配置（ELK Stack）
4. 部署文档（`deploy-guide.md`）
5. 运维文档（`ops-guide.md`）

**能力**:
- 生成自动化部署脚本
- 配置监控和日志系统
- 设计回滚和故障恢复策略
- 生成运维手册和故障排查指南

**依赖**:
- Nop平台部署文档（docs-for-ai/getting-started/deploy/）
- Kubernetes文档（https://kubernetes.io/docs/）
- Prometheus文档（https://prometheus.io/docs/）
- Grafana文档（https://grafana.com/docs/）
- ELK Stack文档（https://www.elastic.co/guide/）

## 核心原则

### 1. 自动化部署
- **脚本化部署**：所有部署操作通过脚本执行
- **幂等性**：多次执行结果一致
- **可回滚**：部署失败时可以快速回滚

### 2. 监控即代码
- **声明式监控**：监控配置也是代码
- **版本化管理**：监控配置与代码一起版本化管理
- **自动化配置**：监控配置自动应用

### 3. 可观测性
- **监控**：Prometheus + Grafana监控应用和系统指标
- **日志**：ELK Stack收集和分析日志
- **追踪**：分布式追踪（可选）

### 4. 故障恢复
- **健康检查**：自动检测应用健康状态
- **自动告警**：异常情况自动告警
- **快速回滚**：部署失败时快速回滚

## 工作流程

### 阶段1：部署需求分析

**步骤1.1：理解部署需求**
```
分析部署需求描述，理解：
- 部署环境（dev、test、prod）
- 部署策略（滚动更新、蓝绿部署、金丝雀部署）
- 回滚策略（K8s rollback、镜像回滚）
- 监控需求（监控指标、告警规则）
- 日志需求（日志级别、日志保留时间）
```

**步骤1.2：分析应用架构**
```
分析应用架构，理解：
- 应用组件（Web、API、Worker、Database、Redis等）
- 组件依赖关系
- 资源需求（CPU、内存、磁盘）
- 网络配置（端口、域名、SSL证书）
```

**步骤1.3：生成部署清单**
```
生成部署清单：
- 应用组件列表
- 组件部署顺序
- 健康检查列表
- 监控指标列表
- 告警规则列表
```

### 阶段2：部署脚本设计

**步骤2.1：设计部署脚本**
```bash
#!/bin/bash

# deploy.sh - 自动化部署脚本

set -e

# 配置变量
APP_NAME="myapp"
IMAGE_TAG="${IMAGE_TAG:-latest}"
REGISTRY="${REGISTRY:-docker.io}"
NAMESPACE="${NAMESPACE:-default}"
KUBECONFIG="${KUBECONFIG:-~/.kube/config}"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查依赖
check_dependencies() {
    log_info "检查依赖..."
    
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl 未安装"
        exit 1
    fi
    
    if ! command -v docker &> /dev/null; then
        log_error "docker 未安装"
        exit 1
    fi
    
    log_info "依赖检查完成"
}

# 拉取镜像
pull_image() {
    log_info "拉取镜像 ${REGISTRY}/${APP_NAME}:${IMAGE_TAG}..."
    docker pull ${REGISTRY}/${APP_NAME}:${IMAGE_TAG}
    log_info "镜像拉取完成"
}

# 部署应用
deploy_app() {
    log_info "部署应用 ${APP_NAME} 到命名空间 ${NAMESPACE}..."
    
    # 设置镜像
    kubectl set image deployment/${APP_NAME} ${APP_NAME}=${REGISTRY}/${APP_NAME}:${IMAGE_TAG} -n ${NAMESPACE}
    
    # 等待部署完成
    log_info "等待部署完成..."
    kubectl rollout status deployment/${APP_NAME} -n ${NAMESPACE} --timeout=5m
    
    log_info "应用部署完成"
}

# 健康检查
health_check() {
    log_info "执行健康检查..."
    
    # 等待Pod就绪
    kubectl wait --for=condition=ready pod -l app=${APP_NAME} -n ${NAMESPACE} --timeout=3m
    
    # 执行HTTP健康检查
    ENDPOINT=$(kubectl get svc ${APP_NAME}-service -n ${NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
    if [ -n "$ENDPOINT" ]; then
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://${ENDPOINT}:80/actuator/health)
        if [ "$HTTP_CODE" = "200" ]; then
            log_info "健康检查通过"
        else
            log_error "健康检查失败，HTTP状态码: ${HTTP_CODE}"
            exit 1
        fi
    else
        log_warn "无法获取服务端点，跳过HTTP健康检查"
    fi
}

# 保存当前版本（用于回滚）
save_version() {
    log_info "保存当前版本..."
    
    CURRENT_REVISION=$(kubectl get deployment ${APP_NAME} -n ${NAMESPACE} -o jsonpath='{.metadata.resourceVersion}')
    echo "${CURRENT_REVISION}" > /tmp/${APP_NAME}-current-revision.txt
    log_info "当前版本已保存: ${CURRENT_REVISION}"
}

# 主函数
main() {
    log_info "开始部署 ${APP_NAME}..."
    
    check_dependencies
    save_version
    pull_image
    deploy_app
    health_check
    
    log_info "部署成功！"
}

# 执行主函数
main
```

**步骤2.2：设计回滚脚本**
```bash
#!/bin/bash

# rollback.sh - 回滚脚本

set -e

APP_NAME="${1:-myapp}"
NAMESPACE="${2:-default}"
REVISION="${3:-}"

if [ -z "$REVISION" ]; then
    # 回滚到上一个版本
    kubectl rollout undo deployment/${APP_NAME} -n ${NAMESPACE}
else
    # 回滚到指定版本
    kubectl rollout undo deployment/${APP_NAME} --to-revision=${REVISION} -n ${NAMESPACE}
fi

# 等待回滚完成
kubectl rollout status deployment/${APP_NAME} -n ${NAMESPACE} --timeout=5m

echo "回滚完成！"
```

**步骤2.3：设计监控安装脚本**
```bash
#!/bin/bash

# install-monitoring.sh - 安装监控和日志系统

set -e

NAMESPACE="monitoring"

log_info "安装Prometheus..."

# 安装Prometheus Operator
kubectl apply -f https://raw.githubusercontent.com/prometheus-operator/prometheus-operator/main/bundle.yaml

# 等待Pod就绪
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=prometheus -n ${NAMESPACE} --timeout=3m

log_info "安装Grafana..."

# 安装Grafana
kubectl apply -f grafana-deployment.yml

# 等待Pod就绪
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=grafana -n ${NAMESPACE} --timeout=3m

log_info "安装ELK Stack..."

# 安装Elasticsearch
kubectl apply -f elasticsearch-deployment.yml

# 安装Logstash
kubectl apply -f logstash-deployment.yml

# 安装Kibana
kubectl apply -f kibana-deployment.yml

# 等待Pod就绪
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=elasticsearch -n ${NAMESPACE} --timeout=3m
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=logstash -n ${NAMESPACE} --timeout=3m
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=kibana -n ${NAMESPACE} --timeout=3m

log_info "监控和日志系统安装完成！"
```

### 阶段3：监控配置生成

**步骤3.1：生成Prometheus配置**
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

    alerting:
      alertmanagers:
        - static_configs:
            - targets:
                - 'alertmanager:9093'

    rule_files:
      - '/etc/prometheus/rules/*.yml'

    scrape_configs:
      # 应用监控
      - job_name: 'myapp'
        static_configs:
          - targets: ['myapp-service.default.svc.cluster.local:8080']
        metrics_path: '/actuator/prometheus'

      # K8s组件监控
      - job_name: 'kubernetes-apiservers'
        kubernetes_sd_configs:
          - role: endpoints
        scheme: https
        tls_config:
          ca_file: /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
        bearer_token_file: /var/run/secrets/kubernetes.io/serviceaccount/token
        relabel_configs:
          - source_labels: [__meta_kubernetes_namespace, __meta_kubernetes_service_name, __meta_kubernetes_endpoint_port_name]
            action: keep
            regex: default;kubernetes;https

      - job_name: 'kubernetes-nodes'
        kubernetes_sd_configs:
          - role: node
        scheme: https
        tls_config:
          ca_file: /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
        bearer_token_file: /var/run/secrets/kubernetes.io/serviceaccount/token
        relabel_configs:
          - action: labelmap
            regex: __meta_kubernetes_node_label_(.+)

      - job_name: 'kubernetes-pods'
        kubernetes_sd_configs:
          - role: pod
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
            action: keep
            regex: true
          - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
            action: replace
            target_label: __metrics_path__
            regex: (.+)
          - action: labelmap
            regex: __meta_kubernetes_pod_label_(.+)
          - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
            action: replace
            regex: ([^:]+)(?::\d+)?;(\d+)
            replacement: $1:$2
            target_label: __address__
```

**步骤3.2：生成告警规则**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-rules
  namespace: monitoring
data:
  alerts.yml: |
    groups:
      - name: myapp.alerts
        interval: 30s
        rules:
          # 应用监控告警
          - alert: HighRequestRate
            expr: rate(http_server_requests_seconds_count[5m]) > 100
            for: 5m
            labels:
              severity: warning
            annotations:
              summary: "高请求率告警"
              description: "请求率过高：{{ $value }} requests/sec"

          - alert: HighErrorRate
            expr: rate(http_server_requests_seconds_count{status=~'5..'}[5m]) > 10
            for: 5m
            labels:
              severity: critical
            annotations:
              summary: "高错误率告警"
              description: "错误率过高：{{ $value }} errors/sec"

          - alert: HighResponseTime
            expr: histogram_quantile(0.95, http_server_requests_seconds_bucket) > 1
            for: 5m
            labels:
              severity: warning
            annotations:
              summary: "高响应时间告警"
              description: "95%分位响应时间过高：{{ $value }}s"

          # K8s资源告警
          - alert: HighCPUUsage
            expr: sum(rate(container_cpu_usage_seconds_total{pod=~"myapp-.*"}[5m])) > 0.8
            for: 5m
            labels:
              severity: warning
            annotations:
              summary: "高CPU使用率告警"
              description: "CPU使用率过高：{{ $value }} cores"

          - alert: HighMemoryUsage
            expr: sum(container_memory_usage_bytes{pod=~"myapp-.*"}) / sum(container_spec_memory_limit_bytes{pod=~"myapp-.*"}) > 0.8
            for: 5m
            labels:
              severity: warning
            annotations:
              summary: "高内存使用率告警"
              description: "内存使用率过高：{{ $value }}"

          - alert: PodCrashLooping
            expr: rate(kube_pod_container_status_restarts_total{pod=~"myapp-.*"}[15m]) > 0
            for: 5m
            labels:
              severity: critical
            annotations:
              summary: "Pod频繁重启告警"
              description: "Pod {{ $labels.pod }} 频繁重启"
```

**步骤3.3：生成Grafana Dashboard**
```json
{
  "dashboard": {
    "title": "MyApp Dashboard",
    "panels": [
      {
        "title": "Request Rate",
        "targets": [
          {
            "expr": "rate(http_server_requests_seconds_count[5m])",
            "legendFormat": "{{method}} {{uri}}"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Response Time",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, http_server_requests_seconds_bucket)",
            "legendFormat": "95th Percentile"
          },
          {
            "expr": "histogram_quantile(0.99, http_server_requests_seconds_bucket)",
            "legendFormat": "99th Percentile"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Error Rate",
        "targets": [
          {
            "expr": "rate(http_server_requests_seconds_count{status=~'5..'}[5m])",
            "legendFormat": "{{status}}"
          }
        ],
        "type": "graph"
      },
      {
        "title": "CPU Usage",
        "targets": [
          {
            "expr": "sum(rate(container_cpu_usage_seconds_total{pod=~'myapp-.*'}[5m]))",
            "legendFormat": "CPU"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Memory Usage",
        "targets": [
          {
            "expr": "sum(container_memory_usage_bytes{pod=~'myapp-.*'})",
            "legendFormat": "Memory"
          }
        ],
        "type": "graph"
      }
    ]
  }
}
```

### 阶段4：日志配置生成

**步骤4.1：生成Logstash配置**
```ruby
input {
  http {
    port => 5000
    codec => json
  }
}

filter {
  if [app] == "myapp" {
    # 解析日志时间戳
    grok {
      match => { "message" => "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:log_message}" }
    }
    
    # 解析时间戳
    date {
      match => [ "timestamp", "ISO8601" ]
    }
    
    # 添加应用标签
    mutate {
      add_field => { "application" => "myapp" }
      add_field => { "environment" => "prod" }
    }
    
    # 解析JSON日志
    json {
      source => "log_message"
      target => "json_data"
    }
  }
}

output {
  if [level] == "ERROR" or [level] == "WARN" {
    # 错误和警告日志发送到独立的索引
    elasticsearch {
      hosts => ["elasticsearch:9200"]
      index => "myapp-error-%{+YYYY.MM.dd}"
      document_type => "_doc"
    }
  } else {
    # 正常日志
    elasticsearch {
      hosts => ["elasticsearch:9200"]
      index => "myapp-%{+YYYY.MM.dd}"
      document_type => "_doc"
    }
  }
}
```

**步骤4.2：生成Kibana配置**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: kibana-config
  namespace: monitoring
data:
  kibana.yml: |
    server.name: kibana
    server.host: "0.0.0.0"
    elasticsearch.hosts: ["http://elasticsearch:9200"]
    
    monitoring.enabled: true
    monitoring.ui.container.elasticsearch.enabled: true
```

### 阶段5：部署文档生成

**步骤5.1：生成部署文档**
```markdown
# 部署指南

## 前置条件

1. 安装kubectl
2. 安装docker
3. 配置K8s集群访问
4. 配置Docker Hub访问

## 部署步骤

### 1. 部署到开发环境

```bash
# 设置环境变量
export IMAGE_TAG=dev-$(date +%Y%m%d-%H%M%S)
export NAMESPACE=dev

# 执行部署
./deploy.sh
```

### 2. 部署到测试环境

```bash
# 设置环境变量
export IMAGE_TAG=test-$(date +%Y%m%d-%H%M%S)
export NAMESPACE=test

# 执行部署
./deploy.sh
```

### 3. 部署到生产环境

```bash
# 设置环境变量
export IMAGE_TAG=prod-$(date +%Y%m%d-%H%M%S)
export NAMESPACE=prod

# 执行部署
./deploy.sh
```

## 健康检查

部署脚本会自动执行健康检查：
1. 检查Pod是否就绪
2. 执行HTTP健康检查
3. 验证应用可访问性

## 回滚

如果部署失败，可以快速回滚：

```bash
# 回滚到上一个版本
./rollback.sh myapp prod

# 回滚到指定版本
./rollback.sh myapp prod 12345
```

## 监控和日志

部署完成后，可以访问：
- Prometheus: http://prometheus.monitoring.svc.cluster.local:9090
- Grafana: http://grafana.monitoring.svc.cluster.local:3000
- Kibana: http://kibana.monitoring.svc.cluster.local:5601
```

**步骤5.2：生成运维文档**
```markdown
# 运维指南

## 监控指标

### 应用指标
- 请求率：`rate(http_server_requests_seconds_count[5m])`
- 响应时间：`histogram_quantile(0.95, http_server_requests_seconds_bucket)`
- 错误率：`rate(http_server_requests_seconds_count{status=~'5..'}[5m])`

### 系统指标
- CPU使用率：`sum(rate(container_cpu_usage_seconds_total{pod=~'myapp-.*'}[5m]))`
- 内存使用率：`sum(container_memory_usage_bytes{pod=~'myapp-.*'})`
- 磁盘使用率：`sum(node_filesystem_avail_bytes{mountpoint='/var/lib/docker'})`

## 告警规则

### 应用告警
- 高请求率告警：`HighRequestRate`（警告）
- 高错误率告警：`HighErrorRate`（严重）
- 高响应时间告警：`HighResponseTime`（警告）

### 系统告警
- 高CPU使用率告警：`HighCPUUsage`（警告）
- 高内存使用率告警：`HighMemoryUsage`（警告）
- Pod频繁重启告警：`PodCrashLooping`（严重）

## 故障排查

### 1. 应用无法启动

```bash
# 查看Pod状态
kubectl get pods -n prod -l app=myapp

# 查看Pod日志
kubectl logs -f -n prod -l app=myapp

# 查看Pod事件
kubectl describe pod -n prod <pod-name>
```

### 2. 应用响应慢

```bash
# 查看资源使用情况
kubectl top pods -n prod -l app=myapp

# 查看资源限制
kubectl describe pod -n prod <pod-name>

# 查看应用日志
kubectl logs -f -n prod -l app=myapp
```

### 3. 数据库连接失败

```bash
# 查看数据库连接
kubectl exec -it -n prod <pod-name> -- netstat -an | grep 3306

# 查看数据库日志
kubectl logs -f -n prod <db-pod-name>
```

## 常见问题

### Q1: 如何查看应用日志？

A: 使用以下命令：
```bash
kubectl logs -f -n prod -l app=myapp
```

### Q2: 如何进入Pod调试？

A: 使用以下命令：
```bash
kubectl exec -it -n prod <pod-name> -- /bin/bash
```

### Q3: 如何扩容应用？

A: 使用以下命令：
```bash
kubectl scale deployment myapp --replicas=5 -n prod
```

### Q4: 如何查看资源使用情况？

A: 使用以下命令：
```bash
kubectl top pods -n prod -l app=myapp
kubectl top nodes
```
```

## AI推理策略

### 1. 部署策略推理
- **部署方式选择**：
  - 滚动更新：默认选择，支持零停机
  - 蓝绿部署：关键业务场景，快速回滚
  - 金丝雀部署：灰度发布场景

- **健康检查设计**：
  - 识别需要监控的指标（响应时间、错误率、资源使用）
  - 设计健康检查路径（/actuator/health）

### 2. 监控配置推理
- **监控指标识别**：
  - 应用指标（请求率、响应时间、错误率）
  - 系统指标（CPU、内存、磁盘）
  - 业务指标（订单量、支付成功率）

- **告警规则设计**：
  - 识别需要告警的指标
  - 设计告警阈值
  - 设计告警级别（警告、严重）

### 3. 日志配置推理
- **日志级别设计**：
  - 开发环境：DEBUG
  - 测试环境：INFO
  - 生产环境：WARN

- **日志保留策略**：
  - 错误日志：保留90天
  - 正常日志：保留30天

## 验证点

### 1. 部署脚本验证
- [ ] 部署流程是否正确
- [ ] 回滚流程是否正确
- [ ] 健康检查是否完整

### 2. 监控配置验证
- [ ] Prometheus配置是否正确
- [ ] 告警规则是否合理
- [ ] Grafana Dashboard是否完整

### 3. 日志配置验证
- [ ] Logstash配置是否正确
- [ ] 日志过滤规则是否合理
- [ ] 日志保留策略是否合理

## 输出产物

### 1. 部署脚本（deploy.sh、rollback.sh）
```bash
#!/bin/bash
# deploy.sh
```

### 2. 监控配置（prometheus.yml、alerts.yml）
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
# ...
```

### 3. Grafana Dashboard
```json
{
  "dashboard": {
    "title": "MyApp Dashboard",
    "panels": [...]
  }
}
```

### 4. 日志配置（logstash.conf、kibana.yml）
```ruby
input {
  # ...
}
```

### 5. 部署文档（deploy-guide.md）
```markdown
# 部署指南
...
```

### 6. 运维文档（ops-guide.md）
```markdown
# 运维指南
...
```

## 下一步工作

当前skill完成自动化部署，生成以下产物：
1. 部署脚本（deploy.sh、rollback.sh）
2. 监控配置（Prometheus、Grafana）
3. 日志配置（ELK Stack）
4. 部署文档（deploy-guide.md）
5. 运维文档（ops-guide.md）

所有8个阶段的skill已完成，可以调用代码生成器生成完整应用并自动化部署！

