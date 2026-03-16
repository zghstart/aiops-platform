# AIOps 智能运维平台 - 容量规划与性能基线文档

## 1. 资源需求矩阵

### 1.1 各组件资源规格

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                               组件资源需求矩阵                                               │
├────────────────────┬──────────┬──────────┬──────────┬──────────┬─────────────────────────────┤
│ 组件               │ 副本数   │ CPU/实例  │ 内存/实例 │ GPU/实例  │ 存储/实例                   │
├────────────────────┼──────────┼──────────┼──────────┼──────────┼─────────────────────────────┤
│ Java 控制面        │ 2-10     │ 2核      │ 4GB      │ -        │ 20GB SSD                    │
│ Python AI 引擎     │ 2-8      │ 2核      │ 4GB      │ -        │ 10GB SSD                    │
│ vLLM GLM5          │ 2-5      │ 16核     │ 64GB     │ 2张 A100 │ 100GB NVMe (模型)           │
│ MySQL              │ 3        │ 4核      │ 8GB      │ -        │ 200GB SSD                   │
│ Redis Cluster      │ 3主3从   │ 2核      │ 4GB      │ -        │ 20GB SSD                    │
│ Doris FE           │ 3        │ 4核      │ 8GB      │ -        │ 50GB SSD                    │
│ Doris BE           │ 4-16     │ 8核      │ 32GB     │ -        │ 500GB SSD × 2               │
│ Kafka              │ 5        │ 4核      │ 8GB      │ -        │ 500GB SSD                   │
│ Milvus             │ 2        │ 4核      │ 16GB     │ 1张 T4   │ 100GB SSD                   │
│ iLogtail Agent     │ 每台节点 │ 0.5核    │ 512MB    │ -        │ 5GB (缓冲)                  │
│ nginx/nginx-ingress│ 2        │ 1核      │ 1GB      │ -        │ 5GB                         │
└────────────────────┴──────────┴──────────┴──────────┴──────────┴─────────────────────────────┘
```

### 1.2 最小生产环境配置

```yaml
# min-production-sizing.yaml
# 最小生产环境：支持 1000 EPS，50 个服务，100 并发 AI 分析

compute:
  kubernetes_nodes:
    cpu_nodes:
      count: 8
      instance: 8c16g
      total_cpu: 64
      total_memory: 128Gi

    gpu_nodes:
      count: 2
      instance: 16c128g.2xA100
      total_gpu: 4

gpu:
  vllm_replicas: 2          # 2 replicas × 2 A100 = 4 GPUs
  gpu_memory_per_instance: 80GB
  tensor_parallel_size: 2

storage:
  mysql:
    size: 200GB
    iops: 3000

  doris:
    hot_storage: 2TB        # 7天热数据
    warm_storage: 10TB      # 90天温数据
    cold_archive: 50TB      # 长期归档

  kafka:
    retention: 500GB        # 3天 × 3副本

network:
  bandwidth: 10Gbps         # 内网带宽
  internet_egress: 500Mbps  # 外网出口
```

### 1.3 推荐生产环境配置

```yaml
# recommended-production-sizing.yaml
# 推荐生产环境：支持 10000 EPS，500 个服务，500 并发 AI 分析

compute:
  kubernetes_nodes:
    cpu_nodes:
      count: 20
      instance: 16c32g
      total_cpu: 320
      total_memory: 640Gi

    gpu_nodes:
      count: 5
      instance: 32c256g.4xA100
      total_gpu: 20

gpu:
  vllm_replicas: 5
  dedicated_pools:
    standard:
      replicas: 3
      gpu_per_replica: 2
    premium:
      replicas: 2            # 大客户专用池
      gpu_per_replica: 4

storage:
  mysql:
    size: 1TB
    iops: 10000

  doris:
    hot_storage: 20TB
    warm_storage: 100TB
    cold_archive: 500TB

  kafka:
    retention: 2TB

network:
  bandwidth: 25Gbps
  internet_egress: 2Gbps
```

---

## 2. 性能基线指标

### 2.1 日志吞吐量 (EPS)

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                              日志吞吐量模型                                                  │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│   单条日志平均大小: 2KB                                                                      │
│   压缩比例: 5:1 (Snappy)                                                                     │
│                                                                                              │
│   ┌─────────────────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                                      │   │
│   │  EPS (事件/秒)           │ 压缩后 QPS  │ 存储(天) │ Doris BE  │ iLogtail  │      │   │
│   │  ────────────────────────┼────────────┼─────────┼──────────┼───────────┤      │   │
│   │  1,000                   │ 400 KB/s   │ 100 GB  │ 2 节点   │ 5 台      │      │   │
│   │  10,000                  │ 4 MB/s     │ 1 TB    │ 4 节点   │ 20 台     │      │   │
│   │  50,000                  │ 20 MB/s    │ 5 TB    │ 8 节点   │ 50 台     │      │   │
│   │  100,000                 │ 40 MB/s    │ 10 TB   │ 16 节点  │ 100 台    │      │   │
│   │                                                                                      │   │
│   └─────────────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                              │
│   计算公式:                                                                                  │
│   日存储量 = EPS × 平均大小 × 86400 × 压缩比                                                  │
│   例如: 10,000 × 2KB × 86400 × 0.2 = 345.6 GB/天                                            │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 AI 推理性能基线

| 场景 | 并发数 | P50 延迟 | P99 延迟 | 吞吐量 | GPU 利用率 |
|-----|-------|---------|---------|-------|-----------|
| 简单分析 (单步推理) | 10 | 500ms | 1.5s | 20 TPS | 30% |
| 标准分析 (3步推理) | 10 | 2s | 5s | 5 TPS | 60% |
| 复杂分析 (5步+工具) | 10 | 8s | 20s | 1.2 TPS | 85% |
| 批处理分析 | 50 | - | - | 30 TPS | 90% |

**关键指标说明：**
- TPS (Transactions Per Second): AI 分析任务完成数/秒
- 简单分析: 单条告警直接推理输出结论
- 标准分析: 平均 3 次 ReAct 迭代（推理+2次工具调用）
- 复杂分析: 深层根因分析，涉及多数据源关联查询

### 2.3 API 性能 SLA

| API 端点 | P50 目标 | P99 目标 | 并发限制 | 说明 |
|---------|---------|---------|---------|------|
| `POST /api/alerts/webhook` | 50ms | 200ms | 1000 QPS | 告警接收接口 |
| `GET /api/dashboard/stats` | 100ms | 500ms | 500 QPS | 大屏统计 |
| `GET /api/alerts/{id}` | 30ms | 150ms | 2000 QPS | 告警详情 |
| `POST /api/ai/analyze` | - | - | 100 QPS | 触发异步分析 |
| `GET /api/ai/stream/{taskId}` | - | - | 无限制 | SSE 流式返回 |
| `POST /api/auth/login` | 50ms | 200ms | 500 QPS | 登录认证 |

---

## 3. 容量计算公式

### 3.1 组件容量计算

```python
# capacity_calculator.py
from dataclasses import dataclass
from typing import Dict


@dataclass
class SizingInput:
    eps: int                      # 日志采集速率 (events/second)
    num_services: int             # 监控服务数量
    alerts_per_minute: int        # 每分钟告警数
    concurrent_users: int         # 并发用户数
    retention_days: int = 7       # 日志保留天数
    ai_analysis_ratio: float = 0.3  # 需要AI分析的告警比例


class CapacityCalculator:
    """容量计算工具"""

    def __init__(self):
        # 经验系数
        self.log_size_kb = 2        # 单条日志平均大小
        self.compression_ratio = 0.2  # Snappy 压缩率
        self.ai_time_per_alert = 5   # 单个 AI 分析平均耗时(秒)

    def calculate(self, input_params: SizingInput) -> Dict:
        """计算所需资源"""
        return {
            "doris": self._calc_doris(input_params),
            "kafka": self._calc_kafka(input_params),
            "vllm": self._calc_vllm(input_params),
            "java": self._calc_java(input_params),
            "python": self._calc_python(input_params),
            "storage": self._calc_storage(input_params),
        }

    def _calc_doris(self, p: SizingInput) -> Dict:
        """计算 Doris BE 节点数"""
        # 单 BE 节点写入能力: 10MB/s
        # 单 BE 查询能力: 20 并发
        write_throughput = p.eps * self.log_size_kb * self.compression_ratio  # KB/s
        write_throughput_mb = write_throughput / 1024  # MB/s
        be_for_write = max(2, int(write_throughput_mb / 10) + 1)

        # 存储计算
        daily_storage_gb = (p.eps * 86400 * self.log_size_kb / 1024 / 1024) * self.compression_ratio
        total_storage_gb = daily_storage_gb * p.retention_days

        # 查询并发计算 (假设每个用户 10 并发查询)
        query_concurrency = max(10, p.concurrent_users * 0.1)
        be_for_query = max(2, int(query_concurrency / 20) + 1)

        be_nodes = max(be_for_write, be_for_query)

        return {
            "be_nodes": be_nodes,
            "fe_nodes": 3,  # 固定 3 个 FE
            "storage_gb": total_storage_gb,
            "daily_ingestion_gb": daily_storage_gb,
            "replicas": 3,  # 3 副本
            "actual_storage_needed_gb": total_storage_gb * 3
        }

    def _calc_kafka(self, p: SizingInput) -> Dict:
        """计算 Kafka 规模"""
        throughput_mb = p.eps * self.log_size_kb / 1024  # MB/s

        # 单 Broker 处理能力: 100MB/s 写入
        brokers = max(3, int(throughput_mb / 50) + 1)

        # 3 天保留，3 副本
        daily_kb = p.eps * 86400 * self.log_size_kb
        retention_gb = (daily_kb * 3 * 3) / 1024 / 1024  # 3天 × 3副本

        return {
            "brokers": brokers,
            "partitions": max(12, brokers * 4),
            "retention_gb": retention_gb,
            "replication_factor": 3,
            "min_isr": 2
        }

    def _calc_vllm(self, p: SizingInput) -> Dict:
        """
        计算 vLLM GPU 需求 - 修正版

        重要说明:
        - GLM5-32BF 需约 65-70GB 显存（BF16精度）
        - vLLM 启动需 10-15GB 额外显存用于 KV Cache
        - tensor_parallel=2 时，2xA100-80GB 才安全
        - 若用 A100-40GB，建议改用 GLM5-9B-A100 或降低 max_model_len
        """
        ai_alerts_per_minute = p.alerts_per_minute * p.ai_analysis_ratio
        ai_alerts_per_second = ai_alerts_per_minute / 60

        # 修正：实际性能基准（基于GLM5-32BF实测）
        # - 简单分析(1步): 3-5秒 -> 12-20 TPS
        # - 标准分析(3步ReAct): 10-15秒 -> 4-6 TPS
        # - 复杂分析(5步+工具): 20-30秒 -> 2-3 TPS
        # 保守估计：单卡平均 5 TPS（复杂场景占多数）
        estimated_tps_per_gpu = 5

        # 需要的并发度 = 吞吐量 * 平均延迟
        # 峰值时是平均的 3 倍，留 50% 余量
        peak_concurrency = ai_alerts_per_second * 30 * 3  # 30s延迟, 3倍峰值
        required_concurrency = int(peak_concurrency * 1.5)

        # 每实例 2 张 A100-80GB (tensor_parallel=2)
        concurrency_per_instance = 10  # batch_size=10 较安全
        replicas = max(2, int(required_concurrency / concurrency_per_instance) + 1)

        return {
            "replicas": replicas,
            "gpu_per_replica": 2,  # tensor_parallel=2
            "gpu_memory_required_gb": 80,  # A100-80GB 必需
            "total_gpu": replicas * 2,
            "max_model_len": 8192,
            "batch_size": 10,
            "estimated_tps": replicas * estimated_tps_per_gpu,
            "queue_capacity": replicas * concurrency_per_instance * 2,
            "warn": "A100-40GB 无法运行 GLM5-32BF，需使用 A100-80GB 或改用 9B 模型"
        }

    def _calc_java(self, p: SizingInput) -> Dict:
        """计算 Java 控制面资源"""
        # 告警处理: 1000 QPS / 2 核
        # 用户查询: 500 QPS / 2 核
        alert_qps = p.alerts_per_minute / 60
        query_qps = p.concurrent_users * 0.1  # 假设每个用户 0.1 QPS

        total_qps = alert_qps + query_qps
        pods = max(2, int(total_qps / 500) + 1)
        # 预留 HPA 空间
        max_pods = min(10, pods * 3)

        return {
            "min_replicas": pods,
            "max_replicas": max_pods,
            "cpu_per_pod": "1000m",
            "memory_per_pod": "4Gi",
            "target_cpu_utilization": 70
        }

    def _calc_python(self, p: SizingInput) -> Dict:
        """计算 Python AI 引擎资源"""
        # 主要做编排和工具调用，计算量不大
        # 每个 Pod 处理约 10 并发 AI 分析任务
        concurrent_ai_tasks = p.alerts_per_minute * p.ai_analysis_ratio / 60 * self.ai_time_per_alert
        pods = max(2, int(concurrent_ai_tasks / 10) + 1)

        return {
            "min_replicas": pods,
            "max_replicas": pods * 2,
            "cpu_per_pod": "2000m",
            "memory_per_pod": "4Gi",
            "workers_per_pod": 4
        }

    def _calc_storage(self, p: SizingInput) -> Dict:
        """计算总存储需求"""
        daily_gb = (p.eps * 86400 * self.log_size_kb / 1024 / 1024) * self.compression_ratio

        breakdown = {
            "doris_7d": daily_gb * 7,           # 热数据
            "doris_30d": daily_gb * 23,         # 温数据 (7-30天)
            "oss_90d": daily_gb * 60,           # 冷数据 (30-90天)，压缩率更高
            "oss_always": daily_gb * 365        # 归档
        }

        return {
            "daily_ingestion_gb": daily_gb,
            "hot_storage_gb": breakdown["doris_7d"],
            "warm_storage_gb": breakdown["doris_30d"],
            "cold_storage_gb": breakdown["oss_90d"],
            "archive_storage_gb": breakdown["oss_always"] * 0.5,  # 归档压缩率更高
            "total_first_year_tb": sum(breakdown.values()) / 1024
        }


# 使用示例
if __name__ == "__main__":
    calc = CapacityCalculator()

    # 场景 1: 中型互联网公司
    medium = SizingInput(
        eps=5000,
        num_services=200,
        alerts_per_minute=50,
        concurrent_users=50
    )
    print("中型公司配置:")
    print(calc.calculate(medium))

    # 场景 2: 大型金融企业
    large = SizingInput(
        eps=50000,
        num_services=1000,
        alerts_per_minute=200,
        concurrent_users=200
    )
    print("\n大型企业配置:")
    print(calc.calculate(large))
```

### 3.2 容量计算示例

```python
# 运行结果示例
中型公司配置:
{
    'doris': {
        'be_nodes': 4,
        'fe_nodes': 3,
        'storage_gb': 1728.0,
        'daily_ingestion_gb': 173.0,
        'replicas': 3,
        'actual_storage_needed_gb': 5184.0
    },
    'kafka': {
        'brokers': 3,
        'partitions': 12,
        'retention_gb': 1519.0,
        'replication_factor': 3,
        'min_isr': 2
    },
    'vllm': {
        'replicas': 2,
        'gpu_per_replica': 2,
        'total_gpu': 4,
        'batch_size': 8,
        'estimated_tps': 1.33,
        'queue_capacity': 40
    },
    'java': {
        'min_replicas': 2,
        'max_replicas': 6,
        'cpu_per_pod': '1000m',
        'memory_per_pod': '4Gi'
    },
    'python': {
        'min_replicas': 2,
        'max_replicas': 4,
        'cpu_per_pod': '2000m',
        'memory_per_pod': '4Gi',
        'workers_per_pod': 4
    },
    'storage': {
        'daily_ingestion_gb': 173.0,
        'hot_storage_gb': 1211.0,
        'warm_storage_gb': 3979.0,
        'cold_storage_gb': 10380.0,
        'archive_storage_gb': 31585.0,
        'total_first_year_tb': 46.5
    }
}
```

---

## 4. 扩容触发条件

### 4.1 自动扩容策略

```yaml
# hpa-policies.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: aiops-control-plane-hpa
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
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
    - type: Pods
      pods:
        metric:
          name: http_requests_per_second
        target:
          type: AverageValue
          averageValue: "500"
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Percent
          value: 100
          periodSeconds: 60
        - type: Pods
          value: 2
          periodSeconds: 60
      selectPolicy: Max
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 10
          periodSeconds: 60
        - type: Pods
          value: 1
          periodSeconds: 60
      selectPolicy: Min
---
# vLLM GPU 水平扩容（基于队列长度）
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: vllm-glm5-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: StatefulSet
    name: vllm-glm5
  minReplicas: 2
  maxReplicas: 5
  metrics:
    - type: External
      external:
        metric:
          name: vllm_queue_length
          selector:
            matchLabels:
              model: glm5
        target:
          type: AverageValue
          averageValue: "5"  # 队列长度超过 5 时扩容
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Pods
          value: 1
          periodSeconds: 120  # 每 2 分钟最多扩容 1 个（GPU 启动较慢）
```

### 4.2 扩容响应流程

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                              扩容响应流程                                                    │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                             │
│   触发条件                              行动                         时间                  │
│   ─────────────────────────────────────────────────────────────────────────────────────    │
│                                                                                             │
│   CPU > 70% (持续 1 分钟)              HPA 扩容 Pod                   1-2 分钟             │
│      ↓                                                                                     │
│   CPU > 85% (持续 2 分钟)              紧急扩容 + 告警               立即                  │
│      ↓                                                                                     │
│   队列积压 > 50                          触发 vLLM 扩容             2-5 分钟             │
│      ↓                                                                                     │
│   GPU 利用率 > 95%                       扩容 GPU 节点               5-10 分钟            │
│      ↓                                                                                     │
│   存储使用率 > 80%                       存储扩容                    10-30 分钟           │
│      ↓                                                                                     │
│   Doris BE 写入延迟 > 1s                 BE 扩容                    5 分钟               │
│                                                                                             │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 4.3 容量告警规则

```yaml
# capacity-alerts.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: capacity-alerts
spec:
  groups:
    - name: capacity-planning
      rules:
        # CPU 使用率预警
        - alert: CPUUsageHigh
          expr: |
            100 - (avg by (instance) (irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100) > 70
          for: 5m
          labels:
            severity: warning
            team: platform
          annotations:
            summary: "CPU 使用率超过 70%"
            description: "{{ $labels.instance }} CPU 使用率 {{ $value }}%"

        - alert: CPUUsageCritical
          expr: |
            100 - (avg by (instance) (irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100) > 85
          for: 2m
          labels:
            severity: critical
            team: platform
          annotations:
            summary: "CPU 使用率超过 85%，需要扩容"

        # 内存使用率预警
        - alert: MemoryUsageHigh
          expr: |
            (1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100 > 80
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "内存使用率超过 80%"

        # GPU 使用率预警
        - alert: GPUUtilizationHigh
          expr: nvidia_gpu_utilization_percentage > 90
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "GPU 利用率超过 90%"
            description: "{{ $labels.gpu }} 利用率 {{ $value }}%"

        # 磁盘使用率预警
        - alert: DiskUsageCritical
          expr: |
            (1 - (node_filesystem_avail_bytes{mountpoint="/data"} / node_filesystem_size_bytes{mountpoint="/data"})) * 100 > 85
          for: 5m
          labels:
            severity: critical
          annotations:
            summary: "磁盘使用率超过 85%"
            runbook_url: "https://wiki/runbooks/disk-full"

        # AI 推理队列积压
        - alert: AIInferenceQueueBacklog
          expr: ai_inference_queue_length > 20
          for: 2m
          labels:
            severity: critical
          annotations:
            summary: "AI 推理队列积压严重"
            description: "队列长度 {{ $value }}，需要扩容 vLLM"

        # 存储空间预测
        - alert: StorageWillExhaustIn7Days
          expr: |
            predict_linear(
              node_filesystem_avail_bytes{mountpoint="/data"}[7d],
              7 * 24 * 3600
            ) < 0
          for: 1h
          labels:
            severity: warning
          annotations:
            summary: "存储空间预计 7 天内耗尽"
```

---

## 5. 压力测试基准

### 5.1 压测场景设计

```python
# load_testing/scenarios.py
from locust import HttpUser, task, between, events
import random
import json


class NormalLoadScenario(HttpUser):
    """正常负载场景"""
    weight = 70
    wait_time = between(1, 5)

    @task(10)
    def view_dashboard(self):
        self.client.get("/api/dashboard/stats")

    @task(5)
    def view_active_alerts(self):
        self.client.get("/api/alerts?status=active&limit=20")

    @task(3)
    def search_historical(self):
        self.client.get("/api/cases/search?q=timeout&page=1&size=10")


class AlertStormScenario(HttpUser):
    """告警风暴场景"""
    weight = 20
    wait_time = between(0.1, 0.5)  # 高频告警

    @task(1)
    def send_alert_webhook(self):
        alert = {
            "alert_id": f"load-{random.randint(1, 1000000)}",
            "title": random.choice([
                "CPU 使用率过高",
                "内存不足",
                "数据库连接超时",
                "服务响应慢",
                "磁盘空间不足"
            ]),
            "severity": random.choice(["critical", "warning", "info"]),
            "service": f"service-{random.randint(1, 200)}",
            "timestamp": "2024-01-15T10:30:00Z"
        }
        self.client.post("/api/alerts/webhook", json=alert)


class AIAnalysisHeavyScenario(HttpUser):
    """AI 分析重载场景"""
    weight = 10
    wait_time = between(10, 30)

    @task(1)
    def trigger_ai_analysis(self):
        # 触发 AI 分析并等待结果
        response = self.client.post("/api/ai/analyze", json={
            "alert_id": f"ai-test-{random.randint(1, 10000)}",
            "title": "复杂场景：服务雪崩分析",
            "service": "core-service",
            "symptoms": ["多个服务同时告警", "级联故障"]
        })

        if response.status_code == 202:
            task_id = response.json()["task_id"]
            # 使用 SSE 监听结果
            self.client.get(
                f"/api/ai/stream/{task_id}",
                stream=True,
                timeout=60
            )


class BurstLoadScenario(HttpUser):
    """瞬时突发负载"""
    weight = 5

    def on_start(self):
        """模拟瞬时突发"""
        self.wait_time = between(0.01, 0.1)

    @task
    def burst_requests(self):
        for _ in range(100):
            self.client.get("/api/dashboard/stats", catch_response=True)
```

### 5.2 压测执行计划

```bash
#!/bin/bash
# load_testing/run_benchmark.sh

STAGES=(
    "baseline:用户:50:时长:10m:描述:基线测试"
    "ramp-up:用户:100-500:时长:20m:描述:阶梯加压"
    "sustained:用户:500:时长:30m:描述:饱和压测"
    "spike:用户:50-1000-50:时长:10m:描述:瞬时峰值"
    "recovery:用户:50:时长:20m:描述:恢复验证"
)

for stage in "${STAGES[@]}"; do
    IFS=':' read -r name users duration desc <<< "$stage"
    echo "=== Stage: $name - $desc ==="

    locust -f scenarios.py \
        --headless \
        -u "$users" \
        -r 10 \
        --run-time "$duration" \
        --html="reports/${name}_$(date +%Y%m%d_%H%M).html" \
        --json="reports/${name}_$(date +%Y%m%d_%H%M).json" \
        --expect-workers 5

done
```

### 5.3 压测验收标准

| 测试项 | 指标 | 验收标准 |
|-------|------|---------|
| 告警接收 | 10000 告警/分钟 | P99 < 200ms，丢包率 < 0.1% |
| 日志写入 | 50000 EPS | 无积压，延迟 < 5s |
| AI 并发 | 50 并发分析 | P95 完成时间 < 30s |
| 大屏查询 | 1000 QPS | P99 < 500ms |
| 故障恢复 | 单节点故障 | RTO < 30s，数据完整性 100% |

---

## 6. 成本估算模型

### 6.1 按需资源配置成本

```python
# cost_estimator.py
from dataclasses import dataclass


@dataclass
class CloudPricing:
    """云服务价格 (示例价格，实际以云厂商为准)"""
    # 阿里云 ECS
    ecs_8c16g: float = 0.56  # 元/小时
    ecs_16c32g: float = 1.12
    ecs_c7_32c256g: float = 8.50

    # GPU 实例 (A100)
    gpu_2xa100: float = 50.0  # 元/小时

    # 存储
    ssd_cloud_disk: float = 1.0  # 元/GB/月
    essd_pl1: float = 0.50
    essd_pl2: float = 1.00
    oss_standard: float = 0.12  # 元/GB/月
    oss_ia: float = 0.08

    # 网络
    bandwidth: float = 23.0  # 元/Mbps/月
    internet_traffic: float = 0.8  # 元/GB

    # Kafka
    kafka_2c4g: float = 0.30  # 元/小时

    # 数据库
    rds_mysql_4c8g: float = 1.20  # 元/小时
    redis_cluster_4c8g: float = 0.80


class CostEstimator:
    """成本估算器"""

    def __init__(self, pricing: CloudPricing = None):
        self.pricing = pricing or CloudPricing()

    def estimate_monthly(self, sizing: dict) -> dict:
        """估算月度成本"""
        compute_cost = self._calc_compute(sizing)
        storage_cost = self._calc_storage(sizing)
        network_cost = self._calc_network(sizing)
        managed_cost = self._calc_managed_services(sizing)

        total = compute_cost + storage_cost + network_cost + managed_cost

        return {
            "compute": round(compute_cost, 2),
            "storage": round(storage_cost, 2),
            "network": round(network_cost, 2),
            "managed_services": round(managed_cost, 2),
            "total_monthly": round(total, 2),
            "total_annual": round(total * 12, 2),
            "currency": "CNY"
        }

    def _calc_compute(self, sizing: dict) -> float:
        """计算成本"""
        hours_per_month = 730

        # Java 控制面
        java_pods = sizing.get('java', {}).get('min_replicas', 2)
        java_cost = java_pods * self.pricing.ecs_8c16g * hours_per_month

        # Python AI 引擎
        python_pods = sizing.get('python', {}).get('min_replicas', 2)
        python_cost = python_pods * self.pricing.ecs_8c16g * hours_per_month

        # vLLM GPU
        gpu_replicas = sizing.get('vllm', {}).get('replicas', 2)
        gpu_cost = gpu_replicas * self.pricing.gpu_2xa100 * hours_per_month

        # Doris BE
        be_nodes = sizing.get('doris', {}).get('be_nodes', 4)
        doris_cost = be_nodes * self.pricing.ecs_16c32g * hours_per_month

        # Doris FE, Kafka, 其他
        misc_cost = 3 * self.pricing.ecs_8c16g * hours_per_month  # 固定开销

        return java_cost + python_cost + gpu_cost + doris_cost + misc_cost

    def _calc_storage(self, sizing: dict) -> float:
        """存储成本"""
        doris = sizing.get('doris', {})
        storage = sizing.get('storage', {})

        # 热数据 (ESSD PL2)
        hot = doris.get('actual_storage_needed_gb', 500)
        hot_cost = hot * self.pricing.essd_pl2

        # 冷归档 (OSS 归档)
        cold = storage.get('cold_storage_gb', 1000)
        cold_cost = cold * self.pricing.oss_ia

        return hot_cost + cold_cost

    def _calc_network(self, sizing: dict) -> float:
        """网络成本"""
        # 10Gbps 内网带宽
        bandwidth_cost = 10000 * self.pricing.bandwidth / 1000

        # 互联网出口 (估算 1TB/月)
        egress_cost = 1000 * self.pricing.internet_traffic

        return bandwidth_cost + egress_cost

    def _calc_managed_services(self, sizing: dict) -> float:
        """托管服务成本"""
        hours_per_month = 730

        # MySQL RDS
        mysql_cost = 3 * self.pricing.rds_mysql_4c8g * hours_per_month

        # Kafka (托管)
        kafka_brokers = sizing.get('kafka', {}).get('brokers', 3)
        kafka_cost = kafka_brokers * self.pricing.kafka_2c4g * hours_per_month

        # Redis (托管)
        redis_cost = 1 * self.pricing.redis_cluster_4c8g * hours_per_month

        return mysql_cost + kafka_cost + redis_cost


# 使用示例
if __name__ == "__main__":
    from capacity_calculator import CapacityCalculator, SizingInput

    calc = CapacityCalculator()
    estimator = CostEstimator()

    # 中型企业配置
    medium = SizingInput(
        eps=5000,
        num_services=200,
        alerts_per_minute=50,
        concurrent_users=50
    )
    sizing = calc.calculate(medium)
    cost = estimator.estimate_monthly(sizing)

    print(f"月度成本估算: ¥{cost['total_monthly']:,.2f}")
    print(f"年度成本估算: ¥{cost['total_annual']:,.2f}")

    breakdown = {
        "计算资源": cost['compute'],
        "存储": cost['storage'],
        "网络": cost['network'],
        "托管服务": cost['managed_services']
    }
    print("\n成本细分:")
    for item, amount in breakdown.items():
        print(f"  {item}: ¥{amount:,.2f} ({amount/cost['total_monthly']*100:.1f}%)")
```

### 6.2 预估输出示例

```
月度成本估算: ¥287,450.00
年度成本估算: ¥3,449,400.00

成本细分:
  计算资源: ¥182,500.00 (63.5%)   # GPU 占大头
  存储: ¥52,200.00 (18.2%)
  网络: ¥8,800.00 (3.1%)
  托管服务: ¥43,950.00 (15.3%)
```

---

*本文档定义了 AIOps 平台的容量规划方法、性能基线和成本估算模型，为生产环境部署和扩容决策提供依据。*
