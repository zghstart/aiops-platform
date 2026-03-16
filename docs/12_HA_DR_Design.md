# AIOps 智能运维平台 - 高可用与灾备设计文档

## 1. 高可用架构概览

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                  多可用区部署架构                                            │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                             │
│   可用区 A (AZ-A)                              可用区 B (AZ-B)                               │
│   ┌─────────────────────────────────────┐     ┌─────────────────────────────────────┐       │
│   │                                     │     │                                     │       │
│   │  ┌──────┐ ┌──────┐ ┌──────┐        │     │      ┌──────┐ ┌──────┐ ┌──────┐   │       │
│   │  │Java-1│ │Java-2│ │Java-3│        │     │      │Java-4│ │Java-5│ │Java-6│   │       │
│   │  └──┬───┘ └──┬───┘ └──┬───┘        │     │      └──┬───┘ └──┬───┘ └──┬───┘   │       │
│   │     └───────┬┴───────┘             │     │         └───────┬┴───────┘       │       │
│   │             │                      │     │                 │                │       │
│   │       ┌─────┴────┐                 │     │           ┌─────┴────┐           │       │
│   │       │  SLB A   │◄────────────────┼─────┼──────────►│  SLB B   │           │       │
│   │       │(主接入)   │                 │     │           │(备接入)  │           │       │
│   │       └─────┬────┘                 │     │           └─────┬────┘           │       │
│   │             │                      │     │                 │                │       │
│   │       ┌─────┴────┐                 │     │           ┌─────┴────┐           │       │
│   │       │  Redis   │◄─────────────同步复制─────────────►│  Redis   │           │       │
│   │       │Cluster-1 │                 │     │           │Cluster-2 │           │       │
│   │       └─────┬────┘                 │     │           └─────┬────┘           │       │
│   │             │                      │     │                 │                │       │
│   │       ┌─────┴────┐                 │     │           ┌─────┴────┐           │       │
│   │       │MySQL     │◄────────────────┼─────┼──────────►│MySQL     │           │       │
│   │       │Primary   │    Binlog 同步   │     │           │Replica   │           │       │
│   │       └──────────┘                 │     │           └──────────┘           │       │
│   │                                    │     │                                    │       │
│   │  ┌──────┐ ┌──────┐ ┌──────┐        │     │      ┌──────┐ ┌──────┐          │       │
│   │  │ vLLM │ │ vLLM │ │Python│        │     │      │ vLLM │ │Python│          │       │
│   │  │GPU-1 │ │GPU-2 │ │AI-1  │        │     │      │GPU-3 │ │AI-2  │          │       │
│   │  └──┬───┘ └──┬───┘ └──┬───┘        │     │      └──┬───┘ └──┬───┘          │       │
│   │     └───────┬┴───────┘             │     │         └───────┬┘              │       │
│   │             │                      │     │                 │                │       │
│   │      ┌──────┴──────┐               │     │          ┌──────┴──────┐         │       │
│   │      │Doris BE-1/2 │◄──────────────┼─────┼─────────►│Doris BE-3/4 │         │       │
│   │      │FE Primary   │               │     │          │FE Observer  │         │       │
│   │      └─────────────┘               │     │          └─────────────┘         │       │
│   │                                    │     │                                    │       │
│   └────────────────────────────────────┘     └────────────────────────────────────┘       │
│                                                                                             │
│   ┌───────────────────────────────────────────────────────────────────────────────────┐    │
│   │                           Kafka Cluster (跨 AZ)                                    │    │
│   │  ┌─────────┐         ┌─────────┐         ┌─────────┐                              │    │
│   │  │Broker-1 │◄───────►│Broker-2 │◄───────►│Broker-3 │                              │    │
│   │  │ (AZ-A)  │         │ (AZ-B)  │         │ (AZ-C)  │                              │    │
│   │  └─────────┘         └─────────┘         └─────────┘                              │    │
│   └───────────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                             │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 各组件高可用方案

### 2.1 Java 控制面高可用

#### Deployment 配置

```yaml
# deploy/k8s/ha/control-plane-ha.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: aiops-control-plane
  namespace: aiops
spec:
  replicas: 3
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
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: topology.kubernetes.io/zone
          whenUnsatisfiable: DoNotSchedule
          labelSelector:
            matchLabels:
              app: aiops-control-plane
        - maxSkew: 1
          topologyKey: kubernetes.io/hostname
          whenUnsatisfiable: ScheduleAnyway
          labelSelector:
            matchLabels:
              app: aiops-control-plane
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                labelSelector:
                  matchLabels:
                    app: aiops-control-plane
                topologyKey: kubernetes.io/hostname
      containers:
        - name: control-plane
          image: your-registry/aiops-control-plane:v1.0.0
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod,ha"
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
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
            failureThreshold: 3
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
            failureThreshold: 30
      terminationGracePeriodSeconds: 60
---
apiVersion: v1
kind: Service
metadata:
  name: aiops-control-plane
  namespace: aiops
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-cross-zone-load-balancing-enabled: "true"
spec:
  type: LoadBalancer
  selector:
    app: aiops-control-plane
  ports:
    - port: 8080
      targetPort: 8080
  sessionAffinity: None
```

#### 健康检查配置

```yaml
# application-prod.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: always
      group:
        liveness:
          include: livenessState, diskSpace
        readiness:
          include: readinessState, db, redis, kafka, diskSpace
  health:
    diskspace:
      enabled: true
      threshold: 1GB
    db:
      enabled: true
    redis:
      enabled: true
    kafka:
      enabled: true

# 自定义健康指示器 - 检查关键依赖
@Component
public class CriticalServicesHealthIndicator implements HealthIndicator {

    @Autowired
    private DorisClient dorisClient;

    @Autowired
    private VLLMHealthChecker vllmChecker;

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();

        // 检查 Doris 连接
        boolean dorisHealthy = checkDoris();
        details.put("doris", dorisHealthy ? "UP" : "DOWN");

        // 检查至少一个 vLLM 实例可用
        boolean vllmAvailable = vllmChecker.hasAvailableInstance();
        details.put("vllm", vllmAvailable ? "UP" : "DOWN");

        if (dorisHealthy && vllmAvailable) {
            return Health.up().withDetails(details).build();
        }
        return Health.down().withDetails(details).build();
    }
}
```

### 2.2 AI 引擎高可用

```yaml
# deploy/k8s/ha/ai-engine-ha.yaml
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
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: topology.kubernetes.io/zone
          whenUnsatisfiable: DoNotSchedule
      containers:
        - name: ai-engine
          image: your-registry/aiops-ai-engine:v1.0.0
          ports:
            - containerPort: 8000
          env:
            - name: VLLM_URLS
              value: "http://vllm-glm5-0:8000,http://vllm-glm5-1:8000,http://vllm-glm5-2:8000"
            - name: VLLM_HEALTH_CHECK_INTERVAL
              value: "10"
          resources:
            requests:
              memory: "2Gi"
              cpu: "1000m"
            limits:
              memory: "4Gi"
              cpu: "4000m"
          # 自定义探针 - 检查 vLLM 连接状态
          livenessProbe:
            exec:
              command:
                - python
                - -c
                - "import httpx; r = httpx.get('http://localhost:8000/health', timeout=5); exit(0 if r.status_code == 200 else 1)"
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /health
              port: 8000
            initialDelaySeconds: 5
            periodSeconds: 5
---
# AI 引擎流量分发 - 带熔断机制
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: aiops-ai-engine
  namespace: aiops
spec:
  host: aiops-ai-engine
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 100
      http:
        http1MaxPendingRequests: 50
        maxRequestsPerConnection: 10
    loadBalancer:
      simple: LEAST_CONN
    outlierDetection:
      consecutive5xxErrors: 3
      interval: 30s
      baseEjectionTime: 30s
```

#### AI 引擎故障转移代码

```python
# app/core/vllm_failover.py
import asyncio
from dataclasses import dataclass
from typing import List, Optional
import httpx
from datetime import datetime, timedelta


@dataclass
class VLLMInstance:
    url: str
    healthy: bool = True
    last_check: datetime = None
    failure_count: int = 0
    success_count: int = 0
    current_load: int = 0
    max_load: int = 10

    @property
    def is_available(self) -> bool:
        return self.healthy and self.current_load < self.max_load


class VLLMCircuitBreaker:
    """vLLM 熔断器"""

    def __init__(
        self,
        failure_threshold: int = 3,
        recovery_timeout: int = 30,
        half_open_requests: int = 1
    ):
        self.failure_threshold = failure_threshold
        self.recovery_timeout = recovery_timeout
        self.half_open_requests = half_open_requests
        self.instances: dict[str, VLLMInstance] = {}
        self._state: dict[str, str] = {}  # CLOSED, OPEN, HALF_OPEN
        self._last_failure: dict[str, datetime] = {}

    def register_instance(self, url: str):
        self.instances[url] = VLLMInstance(url=url)
        self._state[url] = "CLOSED"

    async def call(
        self,
        url: str,
        operation: callable,
        *args,
        **kwargs
    ) -> Optional[any]:
        await self._can_execute(url)

        try:
            result = await operation(*args, **kwargs)
            await self._on_success(url)
            return result
        except Exception as e:
            await self._on_failure(url)
            raise e

    async def _can_execute(self, url: str):
        state = self._state.get(url, "CLOSED")

        if state == "OPEN":
            last_fail = self._last_failure.get(url)
            if last_fail and datetime.now() - last_fail > timedelta(seconds=self.recovery_timeout):
                self._state[url] = "HALF_OPEN"
            else:
                raise CircuitBreakerOpen(f"Circuit breaker OPEN for {url}")

    async def _on_success(self, url: str):
        self.instances[url].success_count += 1
        self.instances[url].failure_count = 0

        if self._state[url] == "HALF_OPEN":
            self.instances[url].success_count += 1
            if self.instances[url].success_count >= self.half_open_requests:
                self._state[url] = "CLOSED"

    async def _on_failure(self, url: str):
        self.instances[url].failure_count += 1
        self._last_failure[url] = datetime.now()

        if self.instances[url].failure_count >= self.failure_threshold:
            self._state[url] = "OPEN"
            self.instances[url].healthy = False


class VLLMFailoverManager:
    """vLLM 故障转移管理器"""

    def __init__(self, instance_urls: List[str]):
        self.circuit_breaker = VLLMCircuitBreaker()
        self.instances: List[VLLMInstance] = []
        self._health_check_task: Optional[asyncio.Task] = None

        for url in instance_urls:
            instance = VLLMInstance(url=url)
            self.instances.append(instance)
            self.circuit_breaker.register_instance(url)

    async def start_health_check(self):
        """启动健康检查定时任务"""
        self._health_check_task = asyncio.create_task(self._health_check_loop())

    async def _health_check_loop(self):
        """健康检查循环"""
        while True:
            await self._check_all_instances()
            await asyncio.sleep(10)  # 每 10 秒检查一次

    async def _check_all_instances(self):
        """检查所有实例健康状态"""
        async with httpx.AsyncClient(timeout=5.0) as client:
            for instance in self.instances:
                try:
                    response = await client.get(f"{instance.url}/health")
                    if response.status_code == 200:
                        if not instance.healthy:
                            # 从故障中恢复
                            instance.healthy = True
                            instance.failure_count = 0
                            self.circuit_breaker._state[instance.url] = "CLOSED"
                    else:
                        instance.failure_count += 1
                        if instance.failure_count >= 3:
                            instance.healthy = False
                except Exception:
                    instance.failure_count += 1
                    if instance.failure_count >= 3:
                        instance.healthy = False

    def get_available_instance(self) -> Optional[VLLMInstance]:
        """获取可用实例（负载均衡）"""
        available = [i for i in self.instances if i.is_available]
        if not available:
            return None

        # 选择负载最低的实例
        return min(available, key=lambda x: x.current_load)

    def get_healthy_instances(self) -> List[VLLMInstance]:
        """获取所有健康实例"""
        return [i for i in self.instances if i.healthy]

    @property
    def has_available_instance(self) -> bool:
        """检查是否有可用实例"""
        return any(i.is_available for i in self.instances)

    async def generate_with_failover(
        self,
        messages: List[dict],
        **kwargs
    ) -> dict:
        """带故障转移的生成请求"""
        instance = self.get_available_instance()

        if not instance:
            raise AllInstancesUnavailable("所有 vLLM 实例均不可用")

        try:
            instance.current_load += 1
            result = await self._call_vllm(instance.url, messages, **kwargs)
            return result
        except Exception as e:
            # 标记当前实例失败，尝试其他实例
            instance.failure_count += 1
            self.circuit_breaker._on_failure(instance.url)

            # 尝试下一个可用实例
            next_instance = self.get_available_instance()
            if next_instance and next_instance.url != instance.url:
                return await self.generate_with_failover(messages, **kwargs)
            raise e
        finally:
            instance.current_load -= 1

    async def _call_vllm(self, url: str, messages: List[dict], **kwargs) -> dict:
        """调用单个 vLLM 实例"""
        async with httpx.AsyncClient(timeout=300.0) as client:
            response = await client.post(
                f"{url}/v1/chat/completions",
                json={
                    "model": "glm5",
                    "messages": messages,
                    "temperature": kwargs.get("temperature", 0.7),
                    "max_tokens": kwargs.get("max_tokens", 2048)
                }
            )
            response.raise_for_status()
            return response.json()
```

### 2.3 vLLM GPU 服务高可用

```yaml
# deploy/k8s/ha/vllm-statefulset.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: vllm-glm5
  namespace: aiops
spec:
  serviceName: vllm-glm5
  replicas: 3
  podManagementPolicy: Parallel  # 并行启动
  selector:
    matchLabels:
      app: vllm-glm5
  template:
    metadata:
      labels:
        app: vllm-glm5
    spec:
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchLabels:
                  app: vllm-glm5
              topologyKey: kubernetes.io/hostname  # 必须分布在不同节点
      nodeSelector:
        node-type: gpu
      tolerations:
        - key: nvidia.com/gpu
          operator: Exists
          effect: NoSchedule
      containers:
        - name: vllm
          image: your-registry/vllm-glm5:v1.0.0
          ports:
            - containerPort: 8000
              name: http
          command:
            - python
            - -m
            - vllm.entrypoints.openai.api_server
            - --model=/models/glm5-32b
            - --served-model-name=glm5
            - --tensor-parallel-size=2
            - --dtype=half
            - --max-model-len=8192
            - --gpu-memory-utilization=0.85
            - --enable-auto-tool-choice
            - --tool-call-parser=glm5
            - --max-num-seqs=256
            - --max-num-batched-tokens=8192
          env:
            - name: CUDA_VISIBLE_DEVICES
              value: "0,1"
            - name: VLLM_WORKER_MULTIPROC_METHOD
              value: "spawn"
          resources:
            limits:
              nvidia.com/gpu: 2
              memory: "64Gi"
              cpu: "16"
            requests:
              memory: "32Gi"
              cpu: "8"
          livenessProbe:
            httpGet:
              path: /health
              port: 8000
            initialDelaySeconds: 120
            periodSeconds: 10
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /health
              port: 8000
            initialDelaySeconds: 60
            periodSeconds: 5
            successThreshold: 1
            failureThreshold: 5
          volumeMounts:
            - name: models
              mountPath: /models
            - name: shm
              mountPath: /dev/shm
      volumes:
        - name: shm
          emptyDir:
            medium: Memory
            sizeLimit: "16Gi"
  volumeClaimTemplates:
    - metadata:
        name: models
      spec:
        accessModes: ["ReadWriteOnce"]
        storageClassName: fast-ssd
        resources:
          requests:
            storage: 100Gi
---
# Headless Service for StatefulSet DNS
apiVersion: v1
kind: Service
metadata:
  name: vllm-glm5
  namespace: aiops
spec:
  selector:
    app: vllm-glm5
  ports:
    - port: 8000
      name: http
  clusterIP: None
```

#### 模型预热与副本管理

```python
# app/models/vllm_warmup.py
import asyncio
import httpx
from typing import List


class VLLMWarmupManager:
    """vLLM 模型预热管理器"""

    WARMUP_PROMPTS = [
        {"role": "user", "content": "你好"},
        {"role": "user", "content": "分析这个告警: CPU 使用率 95%"},
        {"role": "user", "content": "查看最近的错误日志"},
    ]

    def __init__(self, instance_urls: List[str]):
        self.urls = instance_urls

    async def warmup_all(self):
        """预热所有 vLLM 实例"""
        tasks = [self._warmup_instance(url) for url in self.urls]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        for url, result in zip(self.urls, results):
            if isinstance(result, Exception):
                print(f"Warmup failed for {url}: {result}")
            else:
                print(f"Warmup completed for {url}")

    async def _warmup_instance(self, url: str):
        """预热单个实例"""
        async with httpx.AsyncClient(timeout=120.0) as client:
            # 发送预热请求，加载模型到显存
            response = await client.post(
                f"{url}/v1/chat/completions",
                json={
                    "model": "glm5",
                    "messages": self.WARMUP_PROMPTS[:1],
                    "max_tokens": 100,
                    "temperature": 0.0
                }
            )
            response.raise_for_status()


class ModelReplicaManager:
    """模型副本管理器 - 动态扩缩容"""

    def __init__(self, k8s_client, namespace="aiops"):
        self.k8s = k8s_client
        self.namespace = namespace
        self.statefulset_name = "vllm-glm5"

    async def scale_replicas(self, target: int):
        """调整副本数"""
        from kubernetes import client

        api = client.AppsV1Api()

        patch = {
            "spec": {
                "replicas": target
            }
        }

        api.patch_namespaced_stateful_set_scale(
            name=self.statefulset_name,
            namespace=self.namespace,
            body=patch
        )

    def get_current_replicas(self) -> int:
        """获取当前副本数"""
        from kubernetes import client

        api = client.AppsV1Api()
        sts = api.read_namespaced_stateful_set(
            name=self.statefulset_name,
            namespace=self.namespace
        )
        return sts.spec.replicas

    async def auto_scale(self, queue_length: int, avg_latency: float):
        """自动扩缩容决策"""
        current = self.get_current_replicas()

        # 扩容条件：队列积压或延迟过高
        if queue_length > 20 or avg_latency > 8000:  # 8s
            if current < 5:  # 最大 5 个副本
                await self.scale_replicas(current + 1)
                print(f"Scaled up to {current + 1} replicas")

        # 缩容条件：负载较低
        elif queue_length < 5 and avg_latency < 3000:  # 3s
            if current > 2:  # 最小 2 个副本
                await self.scale_replicas(current - 1)
                print(f"Scaled down to {current - 1} replicas")
```

---

## 3. 数据层高可用

### 3.1 MySQL 主从复制与故障切换

```yaml
# deploy/k8s/ha/mysql-ha.yaml
# MySQL Operator 配置（使用 Oracle MySQL Operator 或 Bitnami）
apiVersion: mysql.oracle.com/v2
kind: MySQLCluster
metadata:
  name: aiops-mysql
  namespace: aiops
spec:
  version: "8.0.35"
  replicas: 3
  primarySelector:
    mode: Cluster
  tls:
    useSelfSigned: true
  podSpec:
    resources:
      requests:
        memory: "4Gi"
        cpu: "2000m"
      limits:
        memory: "8Gi"
        cpu: "4000m"
  volumeClaimTemplate:
    spec:
      storageClassName: fast-ssd
      resources:
        requests:
          storage: 100Gi
```

#### Java 应用 MySQL 故障转移配置

```yaml
# application-prod.yml - MySQL 高可用配置
spring:
  datasource:
    url: jdbc:mysql:loadbalance://mysql-primary:3306,mysql-replica1:3306,mysql-replica2:3306/aiops_control?loadBalanceStrategy=random&retriesAllDown=3
    username: ${MYSQL_USER}
    password: ${MYSQL_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      connection-test-query: "SELECT 1"
      # 故障转移配置
      register-mbeans: true
      # 自动重连
      auto-commit: false

  # 启用健康检查
  cloud:
    loadbalancer:
      health-check:
        enabled: true
        interval: 10000
```

```java
// MySQLHaConfig.java - MySQL 高可用配置
@Configuration
public class MySQLHaConfig {

    @Bean
    public DataSource dataSource(
        @Value("${spring.datasource.url}") String url,
        @Value("${spring.datasource.username}") String username,
        @Value("${spring.datasource.password}") String password
    ) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);

        // 连接池配置
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);

        // 故障检测
        config.setConnectionTimeout(30000);
        config.setValidationTimeout(5000);
        config.setLeakDetectionThreshold(60000);

        // 健康检查
        config.setHealthCheckRegistry(new HealthCheckRegistry());
        config.addHealthCheckProperty("connectivityCheckTimeoutMs", "1000");

        return new HikariDataSource(config);
    }

    @Bean
    public RetryTemplate mysqlRetryTemplate() {
        RetryTemplate template = new RetryTemplate();

        // 重试策略 - 最多重试 3 次
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3);
        template.setRetryPolicy(retryPolicy);

        // 退避策略 - 指数退避
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);
        backOffPolicy.setMultiplier(2);
        backOffPolicy.setMaxInterval(10000);
        template.setBackOffPolicy(backOffPolicy);

        return template;
    }
}

// 数据库操作的熔断降级
@Service
public class ResilientAlertService {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private RedisTemplate<String, AlertDTO> redisTemplate;

    @Autowired
    private RetryTemplate retryTemplate;

    @CircuitBreaker(name = "mysql-alerts", fallbackMethod = "fallbackSave")
    @Retry(name = "mysql-alerts")
    public AlertDTO saveAlert(AlertDTO alert) {
        return retryTemplate.execute(context ->
            alertRepository.save(alert)
        );
    }

    // 降级方案 - 写入 Redis 队列，异步恢复
    public AlertDTO fallbackSave(AlertDTO alert, Exception ex) {
        // 写入 Redis 待处理队列
        redisTemplate.opsForList().rightPush(
            "alerts:pending:mysql-unavailable",
            alert
        );

        // 记录降级事件
        log.warn("MySQL 不可用，告警已缓存到 Redis: alertId={}", alert.getAlertId());

        alert.setStatus(AlertStatus.PENDING_RETRY);
        return alert;
    }
}
```

### 3.2 Redis Cluster 高可用

```yaml
# deploy/k8s/ha/redis-cluster.yaml
apiVersion: redis.redis.opstreelabs.in/v1beta2
kind: RedisCluster
metadata:
  name: aiops-redis
  namespace: aiops
spec:
  clusterSize: 3
  kubernetesConfig:
    image: redis:7-alpine
    resources:
      requests:
        cpu: 500m
        memory: 1Gi
      limits:
        cpu: 2000m
        memory: 4Gi
  storage:
    volumeClaimTemplate:
      spec:
        storageClassName: standard
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 20Gi
  redisExporter:
    enabled: true
    image: oliver006/redis_exporter:latest
```

```java
// RedisClusterConfig.java
@Configuration
public class RedisClusterConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // 集群节点配置
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(
            Arrays.asList(
                "redis-cluster-0:6379",
                "redis-cluster-1:6379",
                "redis-cluster-2:6379"
            )
        );
        clusterConfig.setMaxRedirects(3);

        // Lettuce 客户端配置
        ClientOptions clientOptions = ClientOptions.builder()
            .socketOptions(SocketOptions.builder()
                .connectTimeout(Duration.ofSeconds(5))
                .build())
            .timeoutOptions(TimeoutOptions.builder()
                .timeoutCommands(true)
                .connectionTimeout().build())
            .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
            .clientOptions(clientOptions)
            .readFrom(ReadFrom.REPLICA_PREFERRED)  // 优先从副本读取
            .build();

        return new LettuceConnectionFactory(clusterConfig, clientConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
        LettuceConnectionFactory connectionFactory
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
```

### 3.3 Doris 高可用架构

```yaml
# deploy/k8s/ha/doris-cluster.yaml
apiVersion: doris.selectdb.com/v1
kind: DorisCluster
metadata:
  name: aiops-doris
  namespace: aiops
spec:
  feSpec:
    replicas: 3  # 3 个 FE，自动选举 Leader
    image: selectdb/doris.fe-ubuntu:2.0.3
    resource:
      requests:
        cpu: 2000m
        memory: 4Gi
      limits:
        cpu: 4000m
        memory: 8Gi
    service:
      type: ClusterIP
    configMap:
      replicas: 3
      # FE 高可用配置
      fe.conf: |
        edit_log_port = 9010
        http_port = 8030
        rpc_port = 9020
        query_port = 9030
        arrow_flight_sql_port = 9040

        # 自动故障转移
        disable_fuzzy_point_query = false
        enable_http_server_v2 = true

  beSpec:
    replicas: 6  # 6 个 BE，3 个副本
    image: selectdb/doris.be-ubuntu:2.0.3
    resource:
      requests:
        cpu: 4000m
        memory: 16Gi
      limits:
        cpu: 8000m
        memory: 32Gi
    storage:
      storageClassName: fast-ssd
      storageSize: 500Gi
    configMap:
      be.conf: |
        be_port = 9060
        webserver_port = 8040
        heartbeat_service_port = 9050
        brpc_port = 8060

        # 存储路径
        storage_root_path = /opt/apache-doris/be/storage

        # 数据可靠性配置
        max_garbage_sweep_interval = 3600
        min_garbage_sweep_interval = 180
```

#### Doris 故障自动恢复

```python
# app/lib/doris_ha_manager.py
from typing import List, Optional
import httpx
import asyncio


class DorisHAFallbackManager:
    """Doris 高可用故障转移管理器"""

    def __init__(self, fe_endpoints: List[str]):
        self.fe_endpoints = fe_endpoints
        self.current_leader = None
        self._healthy_fes = set()

    async def init(self):
        """初始化，确定当前 Leader"""
        await self._discover_leader()

    async def _discover_leader(self):
        """发现当前 Leader FE"""
        for endpoint in self.fe_endpoints:
            try:
                async with httpx.AsyncClient(timeout=5.0) as client:
                    response = await client.get(
                        f"http://{endpoint}:8030/api/bootstrap"
                    )
                    data = response.json()

                    if data.get("msg") == "success":
                        self.current_leader = endpoint
                        self._healthy_fes.add(endpoint)
                        return endpoint
            except Exception:
                continue

        raise Exception("无法发现 Doris FE Leader")

    async def execute_with_failover(self, query: str, params: dict = None) -> list:
        """带故障转移的查询执行"""
        if not self.current_leader:
            await self._discover_leader()

        try:
            return await self._execute_query(self.current_leader, query, params)
        except Exception:
            # 尝试其他 FE
            for endpoint in self.fe_endpoints:
                if endpoint == self.current_leader:
                    continue
                    try:
                        result = await self._execute_query(endpoint, query, params)
                        self.current_leader = endpoint  # 切换到新的 Leader
                        return result
                    except Exception:
                        continue

            raise Exception("所有 Doris FE 均不可用")

    async def _execute_query(
        self,
        endpoint: str,
        query: str,
        params: dict = None
    ) -> list:
        """在指定 FE 上执行查询"""
        import pymysql

        conn = pymysql.connect(
            host=endpoint,
            port=9030,
            user="root",
            database="aiops",
            connect_timeout=5,
            read_timeout=30,
            write_timeout=30
        )
        try:
            with conn.cursor() as cursor:
                cursor.execute(query, params)
                return cursor.fetchall()
        finally:
            conn.close()
```

### 3.4 Kafka 高可用

```yaml
# deploy/k8s/ha/kafka-cluster.yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: Kafka
metadata:
  name: aiops-kafka
  namespace: aiops
spec:
  kafka:
    version: 3.6.0
    replicas: 5  # 奇数个，3个可用区分布
    listeners:
      - name: plain
        port: 9092
        type: internal
        tls: false
      - name: tls
        port: 9093
        type: internal
        tls: true
    config:
      offsets.topic.replication.factor: 3
      transaction.state.log.replication.factor: 3
      transaction.state.log.min.isr: 2
      default.replication.factor: 3
      min.insync.replicas: 2
      num.partitions: 12
    storage:
      type: persistent-claim
      size: 500Gi
      class: fast-ssd
    rack:
      topologyKey: topology.kubernetes.io/zone  # 跨可用区分布
    metricsConfig:
      type: jmxPrometheusExporter
      valueFrom:
        configMapKeyRef:
          name: kafka-metrics
          key: kafka-metrics-config.yml
  zookeeper:
    replicas: 3
    storage:
      type: persistent-claim
      size: 50Gi
      class: standard
```

---

## 4. 容灾备份方案

### 4.1 备份策略矩阵

| 数据类型 | 备份方式 | 频率 | 保留周期 | 异地备份 |
|---------|---------|------|---------|---------|
| MySQL 业务数据 | 物理备份 + Binlog | 每日全备，实时 Binlog | 30天 | 是 |
| Doris 日志数据 | Snapshot + 增量 | 每日 Snapshot | 7天热数据，90天归档 | 是 |
| Redis 缓存 | RDB + AOF | 每 6 小时 RDB | 3天 | 否（可重建）|
| Kafka 消息 | MirrorMaker 复制 | 实时 | 3天 | 是 |
| Milvus 向量 | Backup API | 每日 | 30天 | 是 |
| 配置文件 | Git + 对象存储 | 实时 | 永久 | 是 |

### 4.2 MySQL 备份与恢复

```bash
#!/bin/bash
# scripts/backup/mysql_backup.sh

BACKUP_DIR="/backup/mysql/$(date +%Y%m%d)"
RETENTION_DAYS=30

# 全量物理备份（使用 XtraBackup）
innobackupex \
    --user=backup \
    --password=$BACKUP_PASSWORD \
    --host=mysql-primary.aiops.svc \
    --parallel=4 \
    --compress \
    --backup $BACKUP_DIR

# 增量备份
innobackupex \
    --incremental $BACKUP_DIR/incr_$(date +%H%M) \
    --incremental-basedir=$BACKUP_DIR \
    --compress \
    --parallel=4

# 上传对象存储
aws s3 sync $BACKUP_DIR s3://aiops-backup/mysql/$(date +%Y%m%d)/

# 清理旧备份
find /backup/mysql -type d -mtime +$RETENTION_DAYS -exec rm -rf {} \;
aws s3 ls s3://aiops-backup/mysql/ | awk -F/ '{print $1}' | while read date; do
    if [[ $(date -d "$date" +%s) -lt $(date -d "-$RETENTION_DAYS days" +%s) ]]; then
        aws s3 rm --recursive s3://aiops-backup/mysql/$date/
    fi
done
```

```java
// MySQLRestoreService.java - MySQL 恢复服务
@Service
@Slf4j
public class MySQLRestoreService {

    @Autowired
    private S3Client s3Client;

    public void restoreFromBackup(String backupDate, String targetHost) {
        // 1. 从 S3 下载备份
        String backupKey = String.format("mysql/%s/backup.xbstream", backupDate);
        Path localBackup = downloadFromS3(backupKey);

        // 2. 解压缩
        executeCommand(String.format(
            "xbstream -x < %s -C /restore/mysql/",
            localBackup
        ));

        // 3. 准备备份
        executeCommand(
            "innobackupex --apply-log --use-memory=4G /restore/mysql/"
        );

        // 4. 恢复数据
        executeCommand(String.format(
            "innobackupex --copy-back --target-dir=/restore/mysql/ --datadir=/var/lib/mysql/",
            targetHost
        ));

        // 5. 启动 MySQL
        executeRemoteCommand(targetHost, "systemctl start mysql");

        // 6. 应用 Binlog 到指定时间点（如果有）
        applyBinlog(targetHost, backupDate);

        log.info("MySQL restore completed on {}", targetHost);
    }

    private void applyBinlog(String targetHost, String fromDate) {
        // 下载并应用 Binlog，实现时间点恢复
        // ...
    }
}
```

### 4.3 Doris 备份与恢复

```python
# app/lib/doris_backup.py
from datetime import datetime, timedelta


class DorisBackupManager:
    """Doris 数据备份管理器"""

    def __init__(self, doris_client, s3_client):
        self.doris = doris_client
        self.s3 = s3_client

    async def create_snapshot(self, snapshot_name: str = None) -> str:
        """创建 Doris snapshot"""
        if not snapshot_name:
            snapshot_name = f"aiops_{datetime.now().strftime('%Y%m%d_%H%M%S')}"

        # 创建仓库
        create_repo_sql = f"""
        CREATE REPOSITORY IF NOT EXISTS `oss_repo`
        WITH S3
        ON LOCATION "s3://aiops-backup/doris/"
        PROPERTIES(
            "s3.endpoint" = "oss-cn-beijing.aliyuncs.com",
            "s3.access_key" = "{self.s3.access_key}",
            "s3.secret_key" = "{self.s3.secret_key}"
        );
        """
        await self.doris.execute(create_repo_sql)

        # 创建 Snapshot
        snapshot_sql = f"""
        CREATE SNAPSHOT `aiops`.`{snapshot_name}`
        ON `oss_repo`
        PROPERTIES("type" = "full");
        """
        await self.doris.execute(snapshot_sql)

        return snapshot_name

    async def restore_snapshot(self, snapshot_name: str, target_db: str = None):
        """从 Snapshot 恢复"""
        if not target_db:
            target_db = "aiops"

        restore_sql = f"""
        RESTORE SNAPSHOT `aiops`.`{snapshot_name}`
        FROM `oss_repo`
        ON (`aiops` AS `{target_db}`)
        PROPERTIES(
            "backup_timestamp" = "{datetime.now().isoformat()}"
        );
        """
        await self.doris.execute(restore_sql)

    async def cleanup_old_snapshots(self, retention_days: int = 7):
        """清理过期 Snapshot"""
 cutoff = datetime.now() - timedelta(days=retention_days)

        list_sql = "SHOW SNAPSHOT ON oss_repo;"
        snapshots = await self.doris.execute(list_sql)

        for snapshot in snapshots:
            snapshot_time = datetime.strptime(
                snapshot['SnapshotFinishTime'],
                '%Y-%m-%d %H:%M:%S'
            )
            if snapshot_time < cutoff:
                drop_sql = f"DROP SNAPSHOT `aiops`.`{snapshot['Snapshot']}` ON `oss_repo`;"
                await self.doris.execute(drop_sql)


class DorisDisasterRecovery:
    """Doris 跨集群容灾"""

    def __init__(self, primary_client, standby_client):
        self.primary = primary_client
        self.standby = standby_client

    async def sync_to_standby(self, tables: List[str]):
        """实时同步数据到备集群"""
        # 使用 Doris 的 CCR (Cross-Cluster Replication)
        for table in tables:
            ccr_sql = f"""
            CREATE CCR REPLICATION {table}_repl
            FROM aiops.{table}
            TO standby_cluster.aiops.{table}
            PROPERTIES (
                "host" = "standby-doris-fe.aiops-dr.svc",
                "port" = "9030",
                "throttle_interval_ms" = "500"
            );
            """
            await self.primary.execute(ccr_sql)
```

### 4.4 跨地域容灾架构

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    异地容灾架构                                               │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│   主数据中心 (北京)                                灾备中心 (上海)                            │
│   ┌───────────────────────────────────────┐       ┌───────────────────────────────────────┐  │
│   │                                       │       │                                       │  │
│   │  ┌─────────┐    ┌─────────┐          │       │       ┌─────────┐    ┌─────────┐    │  │
│   │  │ Ingest  │◄───│ 告警源  │          │       │       │  Ingest │    │(Standby)│    │  │
│   │  │ Server  │    │         │          │       │       │ Server  │    │         │    │  │
│   │  └────┬────┘    └─────────┘          │       │       └────┬────┘    └─────────┘    │  │
│   │       │                              │       │            │                        │  │
│   │  ┌────┴────┐    ┌─────────┐          │       │       ┌────┴────┐    ┌─────────┐    │  │
│   │  │ Java    │◄───►│ Python  │          │       │       │ Java    │◄───►│ Python  │    │  │
│   │  │ Control │    │ AI      │          │       │       │ Control │    │ AI      │    │  │
│   │  │ Plane   │    │ Engine  │          │       │       │ Plane   │    │ Engine  │    │  │
│   │  └────┬────┘    └─────────┘          │       │       └────┬────┘    └─────────┘    │  │
│   │       │                              │       │            │                        │  │
│   │  ┌────┴─────────┐                    │       │       ┌────┴─────────┐              │  │
│   │  │              │                    │       │       │              │              │  │
│   │  ▼              ▼                    │       │       ▼              ▼              │  │
│   │ ┌──────┐    ┌──────┐  ┌──────┐      │       │      ┌──────┐    ┌──────┐           │  │
│   │ │MySQL │    │Doris │  │ Redis│      │       │      │MySQL │    │Doris │           │  │
│   │ │Master│───►│ Log  │  │Cache │      │       │      │Replica│   │Replica│         │  │
│   │ └──────┘    │ Data │  └──────┘      │       │      └──────┘    │(Lag)  │         │  │
│   │             └──────┘                 │       │                  └──────┘         │  │
│   │                 │                    │       │                      ▲              │  │
│   │                 │  Binlog 同步        │       │                      │              │  │
│   │                 │◄───────────────────┼───────┼──────────────────────┘              │  │
│   │                 │                    │       │                                     │  │
│   │                 │  Snapshot 复制     │       │                                     │  │
│   │                 └───►───S3───►───────┼───────┼─────────────────────────────────────│  │
│   │                                      │       │                                     │  │
│   └──────────────────────────────────────┘       └─────────────────────────────────────┘  │
│                                                                                             │
│                              全局 DNS (智能路由)                                             │
│                   ┌─────────────────────────────────────────┐                               │
│                   │  aiops.company.com ─────► 北京 (优先)   │                               │
│                   │                      └──► 上海 (故障时) │                               │
│                   └─────────────────────────────────────────┘                               │
│                                                                                             │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. 故障切换流程

### 5.1 故障级别定义

| 级别 | 描述 | 影响 | 切换 RTO | 切换 RPO |
|-----|------|------|---------|---------|
| P0 | 数据中心级故障 | 整个平台不可用 | 5分钟 | 0 (同步复制) |
| P1 | 核心服务故障（如 MySQL 主节点） | 数据写入受阻 | 30秒 | <1秒 |
| P2 | 组件故障（单 vLLM 实例） | AI 能力降级 | 10秒 | 0 |
| P3 | 非核心组件故障 | 部分功能受限 | 手动 | 不适用 |

### 5.2 自动故障切换脚本

```python
#!/usr/bin/env python3
# scripts/failover/auto_failover.py
import asyncio
import click
import httpx
from typing import Dict
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("failover")


class FailoverOrchestrator:
    """故障切换编排器"""

    def __init__(self, kube_config: str, dr_config: Dict):
        self.kube_config = kube_config
        self.dr_config = dr_config

    async def assess_failure(self) -> Dict:
        """评估故障范围"""
        checks = {
            "mysql_primary": await self._check_mysql(),
            "redis_cluster": await self._check_redis(),
            "vllm_instances": await self._check_vllm(),
            "control_plane": await self._check_control_plane(),
        }

        failed = [k for k, v in checks.items() if not v]

        if len(failed) >= 3:
            return {"level": "P0", "components": failed}
        elif len(failed) >= 1:
            return {"level": "P1", "components": failed}
        else:
            return {"level": "NORMAL", "components": []}

    async def trigger_failover(self, level: str, components: List[str]):
        """触发故障切换"""
        logger.info(f"Triggering {level} failover for: {components}")

        if level == "P0":
            await self._execute_dr_failover()
        elif level == "P1":
            for component in components:
                await self._failover_component(component)

    async def _execute_dr_failover(self):
        """执行跨地域灾备切换"""
        logger.info("Executing DR failover to Shanghai...")

        # 1. 更新全局 DNS
        await self._update_dns_route("shanghai")

        # 2. 提升灾备 MySQL 为主
        await self._promote_mysql_replica()

        # 3. 激活灾备 Doris
        await self._activate_doris_standby()

        # 4. 启动灾备 AI 引擎
        await self._scale_ai_engine_replicas("aiops-dr", 2)

        logger.info("DR failover completed")

    async def _failover_component(self, component: str):
        """单组件故障切换"""
        handlers = {
            "mysql_primary": self._failover_mysql_primary,
            "redis_cluster": self._failover_redis,
            "vllm_instances": self._failover_vllm,
        }

        handler = handlers.get(component)
        if handler:
            await handler()

    async def _failover_mysql_primary(self):
        """MySQL 主节点故障切换"""
        # 使用 Orchestrator 或 MHA 进行自动切换
        pass

    async def _failover_redis(self):
        """Redis 故障切换"""
        # Redis Cluster 自动故障转移
        pass

    async def _failover_vllm(self):
        """vLLM 实例故障，触发扩容"""
        await self._scale_vllm_replicas(3)


@click.group()
def cli():
    """AIOps 故障切换 CLI"""
    pass


@cli.command()
@click.option('--dry-run', is_flag=True, help='模拟运行，不执行实际切换')
def assess(dry_run):
    """评估系统健康状况"""
    orchestrator = FailoverOrchestrator(None, None)
    result = asyncio.run(orchestrator.assess_failure())

    click.echo(f"Failure Level: {result['level']}")
    click.echo(f"Failed Components: {', '.join(result['components'])}")

    if not dry_run and result['level'] != 'NORMAL':
        if click.confirm("Trigger failover?"):
            asyncio.run(
                orchestrator.trigger_failover(result['level'], result['components'])
            )


@cli.command()
@click.option('--target', required=True, type=click.Choice(['beijing', 'shanghai']))
def switch_dc(target):
    """手动切换数据中心"""
    orchestrator = FailoverOrchestrator(None, None)
    asyncio.run(orchestrator._update_dns_route(target))
    click.echo(f"Switched to {target}")


if __name__ == "__main__":
    cli()
```

---

## 6. 监控与告警

### 6.1 高可用监控指标

```yaml
# deploy/monitoring/ha-alerts.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: aiops-ha-alerts
  namespace: monitoring
spec:
  groups:
    - name: availability
      interval: 15s
      rules:
        # 服务可用性告警
        - alert: ServiceAvailabilityCritical
          expr: up{job=~"aiops-.*"} == 0
          for: 1m
          labels:
            severity: critical
          annotations:
            summary: "AIOps 服务不可用"
            description: "{{ $labels.job }} 在 {{ $labels.instance }} 已宕机"

        # MySQL 复制延迟
        - alert: MySQLReplicationLagHigh
          expr: mysql_slave_lag_seconds > 10
          for: 2m
          labels:
            severity: warning
          annotations:
            summary: "MySQL 复制延迟过高"
            description: "复制延迟 {{ $value }} 秒"

        # Redis Cluster 节点异常
        - alert: RedisClusterNodeDown
          expr: redis_connected_slaves < 2
          for: 1m
          labels:
            severity: critical
          annotations:
            summary: "Redis Cluster 节点异常"

        # vLLM 健康实例不足
        - alert: VLLMHealthyInstancesLow
          expr: count(vllm_health_status == 1) < 2
          for: 1m
          labels:
            severity: critical
          annotations:
            summary: "vLLM 健康实例不足"
            description: "可用 vLLM 实例少于 2 个"

        # Doris FE Leader 异常
        - alert: DorisFELeaderNotFound
          expr: doris_fe_leader_count != 1
          for: 30s
          labels:
            severity: critical
          annotations:
            summary: "Doris FE Leader 异常"

        # 跨 AZ 流量不均衡
        - alert: CrossAZTrafficImbalance
          expr: stddev(rate(requests_total[5m])) by (availability_zone) > 0.3
          for: 5m
          labels:
            severity: warning
          annotations:
            summary: "跨可用区流量不均衡"
```

---

## 7. 恢复演练计划

### 7.1 定期演练安排

| 演练类型 | 频率 | 范围 | 参与团队 |
|---------|------|------|---------|
| 组件级故障演练 | 每周 | 单组件（MySQL/Redis/vLLM） | SRE |
| 服务级故障演练 | 每月 | 完整服务链路 | SRE + 开发 |
| 容灾演练 | 每季度 | 跨数据中心切换 | 全团队 |
| 混沌工程 | 每月 | 随机注入故障 | SRE |

### 7.2 演练检查清单

```markdown
## 故障演练检查清单

### 演练前准备
- [ ] 确定演练时间和范围
- [ ] 通知相关团队
- [ ] 准备回滚方案
- [ ] 确保监控正常
- [ ] 记录基线指标

### 演练执行
- [ ] 注入故障
- [ ] 观察系统自动恢复
- [ ] 检查监控告警
- [ ] 验证业务影响
- [ ] 记录恢复时间(RTO)

### 演练后复盘
- [ ] 恢复服务
- [ ] 数据分析
- [ ] 识别改进点
- [ ] 更新 SOP
- [ ] 更新文档
```

---

*本文档定义了 AIOps 平台的高可用架构、容灾备份方案和故障切换流程，确保平台满足企业级可用性要求。*
