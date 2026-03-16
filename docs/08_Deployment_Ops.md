# AIOps 智能运维平台 - 部署与运维文档

## 1. 部署架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            Production Cluster                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                        Kubernetes Namespace                       │   │
│  │                                                                  │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │   │
│  │  │ Java控制面  │  │ Java控制面  │  │      HPA: 2-10        │  │   │
│  │  │ POD-1       │  │ POD-2       │  │                         │  │   │
│  │  │ (8080)      │  │ (8080)      │  │  CPU > 70%: +1          │  │   │
│  │  └──────┬──────┘  └──────┬──────┘  │  CPU < 30%: -1          │  │   │
│  │         └────────┬───────┘         └─────────────────────────┘  │   │
│  │                  │                   │                            │   │
│  │              ┌───┴────┐          ┌──┴──┐                        │   │
│  │              │ Service│          │SLB  │                        │   │
│  │              │ (8080) │          │     │                        │   │
│  │              └───┬────┘          └─────┘                        │   │
│  └──────────────────┼───────────────────────────────────────────────┘   │
│                     │                                                    │
│  ┌─────────────┐    │   ┌─────────────┐  ┌─────────────────────────┐   │
│  │ Python AI   │◄───┘   │ Python AI   │  │      HPA: 2-4          │   │
│  │ (8000)      │        │ (8000)      │  │                         │   │
│  └─────────────┘        └─────────────┘  │  GPU Queue > 10: +1     │   │
│                                          └─────────────────────────┘   │
│  ┌─────────────┐  ┌─────────────┐                                       │
│  │ vLLM GLM5   │  │ vLLM GLM5   │                                       │
│  │ (GPU)       │  │ (GPU)       │  Dedicated Pool (大客户)            │
│  └─────────────┘  └─────────────┘                                       │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Docker部署

### 2.1 Java控制面 Dockerfile

```dockerfile
# aiops-control-plane/Dockerfile
FROM eclipse-temurin:21-jdk-alpine as builder

WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/target/aiops-control-plane-*.jar app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2.2 Python AI引擎 Dockerfile

```dockerfile
# aiops-ai-engine/Dockerfile
FROM python:3.11-slim as builder

WORKDIR /app

# 安装编译依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 生产镜像
FROM python:3.11-slim

WORKDIR /app

# 非root用户
RUN groupadd -r aiops && useradd -r -g aiops aiops

COPY --from=builder /usr/local/lib/python3.11/site-packages /usr/local/lib/python3.11/site-packages
COPY --from=builder /usr/local/bin/uvicorn /usr/local/bin/uvicorn

COPY app/ ./app/

RUN chown -R aiops:aiops /app
USER aiops

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD python -c "import httpx; httpx.get('http://localhost:8000/health', timeout=5)" || exit 1

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "4"]
```

### 2.3 vLLM GLM5 Dockerfile

```dockerfile
# deploy/vllm-glm5/Dockerfile
FROM vllm/vllm-openai:latest

# 挂载模型目录
VOLUME /models

ENV MODEL_PATH=/models/glm5-32b
ENV GPU_MEMORY_UTILIZATION=0.9
ENV MAX_MODEL_LEN=8192

# 启动脚本
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]

# entrypoint.sh
# #!/bin/bash
# python -m vllm.entrypoints.openai.api_server \
#   --model $MODEL_PATH \
#   --served-model-name glm5 \
#   --max-model-len $MAX_MODEL_LEN \
#   --tensor-parallel-size 2 \
#   --gpu-memory-utilization $GPU_MEMORY_UTILIZATION \
#   --enable-auto-tool-choice \
#   --tool-call-parser glm5
```

---

## 3. Kubernetes部署

### 3.1 Java控制面 Deployment

```yaml
# deploy/k8s/control-plane-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: aiops-control-plane
  namespace: aiops
  labels:
    app: aiops-control-plane
spec:
  replicas: 2
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: aiops-control-plane
  template:
    metadata:
      labels:
        app: aiops-control-plane
    spec:
      containers:
        - name: control-plane
          image: your-registry/aiops-control-plane:latest
          ports:
            - containerPort: 8080
              name: http
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: JAVA_OPTS
              value: "-Xmx2g -Xms2g -XX:+UseG1GC"
            - name: MYSQL_HOST
              valueFrom:
                secretKeyRef:
                  name: aiops-secrets
                  key: mysql-host
            - name: MYSQL_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: aiops-secrets
                  key: mysql-password
            - name: KAFKA_BOOTSTRAP
              value: "kafka-0.kafka:9092,kafka-1.kafka:9092,kafka-2.kafka:9092"
            - name: REDIS_CLUSTER
              value: "redis-0.redis:6379,redis-1.redis:6379,redis-2.redis:6379"
            - name: DORIS_FE
              value: "doris-fe-0.doris:9030"
            - name: AI_ENGINE_URL
              value: "http://aiops-ai-engine:8000"
          resources:
            requests:
              memory: "2Gi"
              cpu: "1000m"
            limits:
              memory: "4Gi"
              cpu: "2000m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
          volumeMounts:
            - name: logs
              mountPath: /app/logs
      volumes:
        - name: logs
          emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: aiops-control-plane
  namespace: aiops
spec:
  selector:
    app: aiops-control-plane
  ports:
    - port: 8080
      targetPort: 8080
  type: ClusterIP
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: aiops-control-plane-hpa
  namespace: aiops
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: aiops-control-plane
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 50
          periodSeconds: 60
```

### 3.2 Python AI引擎 Deployment

```yaml
# deploy/k8s/ai-engine-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: aiops-ai-engine
  namespace: aiops
spec:
  replicas: 2
  selector:
    matchLabels:
      app: aiops-ai-engine
  template:
    metadata:
      labels:
        app: aiops-ai-engine
    spec:
      containers:
        - name: ai-engine
          image: your-registry/aiops-ai-engine:latest
          ports:
            - containerPort: 8000
          env:
            - name: VLLM_URL
              value: "http://vllm-glm5:8000/v1"
            - name: REDIS_URL
              value: "redis://redis-cluster:6379"
            - name: DORIS_URL
              value: "mysql://aiops:${DORIS_PASSWORD}@doris-fe:9030/aiops"
            - name: WORKERS
              value: "4"
            - name: LOG_LEVEL
              value: "INFO"
          resources:
            requests:
              memory: "1Gi"
              cpu: "500m"
            limits:
              memory: "2Gi"
              cpu: "2000m"
          livenessProbe:
            httpGet:
              path: /health
              port: 8000
            initialDelaySeconds: 30
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: aiops-ai-engine
  namespace: aiops
spec:
  selector:
    app: aiops-ai-engine
  ports:
    - port: 8000
  type: ClusterIP
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: aiops-ai-engine-hpa
  namespace: aiops
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: aiops-ai-engine
  minReplicas: 2
  maxReplicas: 4
  metrics:
    - type: Pods
      pods:
        metric:
          name: ai_inference_queue_length
        target:
          type: AverageValue
          averageValue: "10"
```

### 3.3 vLLM GPU Deployment

```yaml
# deploy/k8s/vllm-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vllm-glm5
  namespace: aiops
spec:
  replicas: 2
  selector:
    matchLabels:
      app: vllm-glm5
  template:
    metadata:
      labels:
        app: vllm-glm5
    spec:
      nodeSelector:
        node-type: gpu
      tolerations:
        - key: nvidia.com/gpu
          operator: Exists
          effect: NoSchedule
      containers:
        - name: vllm
          image: your-registry/vllm-glm5:latest
          ports:
            - containerPort: 8000
          command:
            - python
            - -m
            - vllm.entrypoints.openai.api_server
            - --model=/models/glm5-32b
            - --served-model-name=glm5
            - --tensor-parallel-size=2
            - --dtype=half
            - --max-model-len=8192
            - --gpu-memory-utilization=0.9
            - --enable-auto-tool-choice
            - --tool-call-parser=glm5
          resources:
            limits:
              nvidia.com/gpu: 2
          volumeMounts:
            - name: models
              mountPath: /models
      volumes:
        - name: models
          persistentVolumeClaim:
            claimName: glm5-models-pvc
```

### 3.4 前端 Nginx Ingress

```yaml
# deploy/k8s/frontend-ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: aiops-dashboard
  namespace: aiops
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-buffer-size: "8k"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "600"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "600"
    nginx.org/websocket-services: "aiops-control-plane"
spec:
  tls:
    - hosts:
        - aiops.company.com
      secretName: aiops-tls
  rules:
    - host: aiops.company.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: aiops-frontend
                port:
                  number: 80
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: aiops-control-plane
                port:
                  number: 8080
          - path: /ai-stream
            pathType: Prefix
            backend:
              service:
                name: aiops-control-plane
                port:
                  number: 8080
```

---

## 4. 配置管理

### 4.1 Secret管理

```yaml
# deploy/k8s/secrets.yaml
apiVersion: v1
kind: Secret
metadata:
  name: aiops-secrets
  namespace: aiops
type: Opaque
stringData:
  mysql-host: "mysql-master.aiops.svc.cluster.local"
  mysql-user: "aiops"
  mysql-password: "your-secure-password"
  redis-password: "your-redis-password"
  doris-password: "your-doris-password"
  api-key: "your-api-key"
```

```bash
# 创建secret
echo -n 'password' > password.txt
kubectl create secret generic aiops-secrets \
  --from-file=mysql-password=password.txt \
  --namespace=aiops
rm password.txt
```

### 4.2 ConfigMap

```yaml
# deploy/k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: aiops-config
  namespace: aiops
data:
  # Java控制面配置
  application-prod.yml: |
    server:
      port: 8080
    spring:
      datasource:
        url: jdbc:mysql://${MYSQL_HOST}:3306/aiops
        username: ${MYSQL_USER}
      kafka:
        bootstrap-servers: ${KAFKA_BOOTSTRAP}
      redis:
        cluster:
          nodes: ${REDIS_CLUSTER}
    ai-engine:
      url: http://aiops-ai-engine:8000
      timeout: 60000
    tenant:
      isolation:
        enabled: true

  # 日志配置
  logback-spring.xml: |
    <configuration>
      <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
          <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
      </appender>
      <root level="INFO">
        <appender-ref ref="STDOUT"/>
      </root>
    </configuration>
```

---

## 5. 数据库初始化

### 5.1 Doris初始化

```sql
-- deploy/sql/init_doris.sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS aiops;

USE aiops;

-- 创建日志表
CREATE TABLE IF NOT EXISTS logs (
    `timestamp` DATETIME NOT NULL,
    `tenant_id` VARCHAR(32) NOT NULL,
    `service_id` VARCHAR(64) NOT NULL,
    `level` VARCHAR(8) NOT NULL,
    `trace_id` VARCHAR(32),
    `span_id` VARCHAR(16),
    `host_ip` VARCHAR(15),
    `pod_name` VARCHAR(128),
    `container` VARCHAR(64),
    `source_file` VARCHAR(256),
    `line_number` INT,
    `message` TEXT NOT NULL,
    `raw_message` TEXT,
    `parsed_fields` JSON,
    INDEX idx_message (`message`) USING INVERTED PROPERTIES("analyzer"="standard"),
    INDEX idx_trace (`trace_id`) USING BITMAP
)
DUPLICATE KEY(`timestamp`, `tenant_id`, `service_id`)
PARTITION BY RANGE(`timestamp`) ()
DISTRIBUTED BY HASH(`service_id`) BUCKETS 16
PROPERTIES (
    "replication_num" = "3",
    "dynamic_partition.enable" = "true",
    "dynamic_partition.time_unit" = "DAY",
    "dynamic_partition.start" = "-30",
    "dynamic_partition.end" = "3",
    "dynamic_partition.buckets" = "16"
);

-- 创建告警事件表
CREATE TABLE IF NOT EXISTS alerts (
    `alert_id` VARCHAR(32) NOT NULL,
    `tenant_id` VARCHAR(32) NOT NULL,
    `incident_id` VARCHAR(32),
    `source` VARCHAR(32) NOT NULL,
    `severity` VARCHAR(16) NOT NULL,
    `status` VARCHAR(16) NOT NULL,
    `title` VARCHAR(512) NOT NULL,
    `description` TEXT,
    `service_id` VARCHAR(64),
    `instance` VARCHAR(256),
    `labels` JSON,
    `starts_at` DATETIME NOT NULL,
    `ends_at` DATETIME,
    `created_at` DATETIME,
    INDEX idx_service (`service_id`) USING BITMAP,
    INDEX idx_status (`status`) USING BITMAP
)
UNIQUE KEY(`alert_id`)
PARTITION BY RANGE(`created_at`) ()
DISTRIBUTED BY HASH(`tenant_id`) BUCKETS 16
PROPERTIES (
    "replication_num" = "3",
    "enable_unique_key_merge_on_write" = "true"
);
```

### 5.2 MySQL初始化

```sql
-- deploy/sql/init_mysql.sql
CREATE DATABASE IF NOT EXISTS aiops_control CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE aiops_control;

-- 租户表
CREATE TABLE IF NOT EXISTS tenants (
    `id` VARCHAR(32) PRIMARY KEY,
    `name` VARCHAR(64) NOT NULL,
    `status` TINYINT DEFAULT 1,
    `plan` VARCHAR(16) DEFAULT 'basic',
    `quota_incidents_per_day` INT DEFAULT 1000,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_plan (`plan`)
) ENGINE=InnoDB;

-- 服务表
CREATE TABLE IF NOT EXISTS services (
    `id` VARCHAR(64) PRIMARY KEY,
    `tenant_id` VARCHAR(32) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `owner_team` VARCHAR(64),
    `service_tier` TINYINT DEFAULT 3,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant (`tenant_id`)
) ENGINE=InnoDB;

-- 降噪规则表
CREATE TABLE IF NOT EXISTS noise_rules (
    `id` VARCHAR(32) PRIMARY KEY,
    `tenant_id` VARCHAR(32) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `enabled` BOOLEAN DEFAULT TRUE,
    `rule_type` VARCHAR(32),
    `match_services` JSON,
    `action` VARCHAR(32),
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant (`tenant_id`)
) ENGINE=InnoDB;
```

---

## 6. 监控与告警

### 6.1 Prometheus规则

```yaml
# deploy/monitoring/prometheus-rules.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: aiops-alerts
  namespace: monitoring
spec:
  groups:
    - name: aiops.rules
      rules:
        # AI推理延迟告警
        - alert: AIInferenceLatencyHigh
          expr: ai_inference_latency_ms > 10000
          for: 2m
          labels:
            severity: warning
          annotations:
            summary: "AI推理延迟过高"
            description: "AI引擎推理延迟{{ $value }}ms，超过10秒阈值"

        # AI推理队列积压
        - alert: AIInferenceQueueBacklog
          expr: ai_inference_queue_length > 50
          for: 1m
          labels:
            severity: critical
          annotations:
            summary: "AI推理队列积压"
            description: "队列积压{{ $value }}个任务，需要扩容"

        # 告警降噪比
        - alert: NoiseReductionRatioLow
          expr: rate(alerts_filtered_total[1h]) / rate(alerts_received_total[1h]) < 0.5
          for: 10m
          labels:
            severity: info
          annotations:
            summary: "告警降噪效果欠佳"

        # vLLM GPU利用率
        - alert: vLLMGPUUtilizationHigh
          expr: vllm_gpu_utilization > 95
          for: 5m
          labels:
            severity: critical
          annotations:
            summary: "vLLM GPU利用率过高"
```

### 6.2 Grafana Dashboard

```json
{
  "dashboard": {
    "title": "AIOps Platform Overview",
    "panels": [
      {
        "title": "AI推理QPS",
        "type": "stat",
        "targets": [
          {
            "expr": "sum(rate(ai_inference_requests_total[5m]))"
          }
        ]
      },
      {
        "title": "平均推理延迟",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.99, rate(ai_inference_duration_bucket[5m]))",
            "legendFormat": "P99"
          },
          {
            "expr": "histogram_quantile(0.95, rate(ai_inference_duration_bucket[5m]))",
            "legendFormat": "P95"
          }
        ]
      },
      {
        "title": "告警降噪率",
        "type": "gauge",
        "targets": [
          {
            "expr": "(sum(rate(alerts_suppressed_total[1h])) / sum(rate(alerts_received_total[1h]))) * 100"
          }
        ]
      }
    ]
  }
}
```

---

## 7. 日常运维

### 7.1 日志查看

```bash
# Java控制面日志
kubectl logs -f deployment/aiops-control-plane -n aiops --tail=100

# Python AI引擎日志
kubectl logs -f deployment/aiops-ai-engine -n aiops --tail=100

# vLLM日志
kubectl logs -f deployment/vllm-glm5 -n aiops

# 查看错误日志
kubectl logs deployment/aiops-control-plane -n aiops | grep ERROR
```

### 7.2 伸缩操作

```bash
# 手动扩容
kubectl scale deployment aiops-control-plane --replicas=5 -n aiops

# 查看HPA状态
kubectl get hpa -n aiops
# NAME                      REFERENCE                     TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
# aiops-control-plane-hpa   Deployment/aiops-control-plane   45%/70%   2         10        3          7d

# 临时关闭HPA
kubectl delete hpa aiops-control-plane-hpa -n aiops
```

### 7.3 备份与恢复

```bash
# MySQL备份
mysqldump -h mysql-host -u aiops -p aiops_control > backup_$(date +%Y%m%d).sql

# Doris备份
# 使用Doris的Backup功能
mysql -h doris-fe -P 9030 -u root -e "
BACKUP SNAPSHOT aiops.backup_$(date +%Y%m%d)
TO s3://aiops-backup/doris/
PROPERTIES('type' = 's3');
"

# Redis备份
kubectl exec -it redis-0 -- redis-cli SAVE
kubectl cp redis-0:/data/dump.rdb ./redis_backup.rdb
```

---

*本文档定义了AIOps平台的部署与运维规范，运维人员应以此为准。*
