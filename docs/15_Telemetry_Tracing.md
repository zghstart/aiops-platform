# AIOps 智能运维平台 - 分布式链路追踪与可观测数据关联

## 1. 链路追踪架构设计

### 1.1 为什么需要链路追踪？

```
场景：告警"支付接口超时"，传统排查 vs 链路追踪

【传统排查 - 30分钟】                    【链路追踪 - 3分钟】

1. 查看API日志                           1. 点击告警关联的TraceID
   发现是超时                              └─► 完整调用链可视化

2. 排查数据库                              ┌──────────┐
   数据库正常                              │ API网关  │ 2ms
                                           └────┬─────┘
3. 排查Redis                              ┌────▼─────┐
   Redis正常                               │订单服务 │ 50ms
                                           └────┬─────┘
4. 排查下游服务                           ┌────▼─────┐◄──【高亮红色】
   逐个服务看日志...                        │库存服务 │ 5002ms ⬅️ 超时
                                           └────┬─────┘
5. 发现是库存服务调用外仓API超时          ┌────▼─────┐
   30分钟过去                              │外仓API  │ 5000ms
                                           └──────────┘

   AI分析：外仓API超时99%概率是根因
```

### 1.2 三大可观测数据关联

```
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                           Metrics + Logs + Traces 三支柱关联                               │
├───────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                           │
│   ┌──────────────┐          ┌──────────────┐          ┌──────────────┐                   │
│   │   METRICS    │◄────────►│   TRACES     │◄────────►│    LOGS      │                   │
│   │   指标        │ 时间对齐 │   链路        │ TraceID  │    日志        │                   │
│   └──────────────┘          └──────────────┘          └──────────────┘                   │
│          │                         │                         │                           │
│          │    CPU: 95% ────────────┼─────────────────────────┤                           │
│          │    时间: 14:23:05       │                         │                           │
│          │                         │    TraceID: abc123       │    [ERROR] 连接超时       │
│          │    Memory: 85% ◄────────┼─────────────────────────│    TraceID: abc123        │
│          │                         │                         │                           │
│          └─────────────────────────┴─────────────────────────┘                           │
│                                    │                                                      │
│                                    ▼                                                      │
│                           ┌─────────────────┐                                            │
│                           │   AI 根因分析    │                                            │
│                           │  1. CPU飙高 +    │                                            │
│                           │  2. 库存服务耗时5s │                                            │
│                           │  3. 大量连接超时日志│                                            │
│                           │  ───────────────│                                            │
│                           │  结论: 连接池耗尽 │                                            │
│                           │  置信度: 94%     │                                            │
│                           └─────────────────┘                                            │
│                                                                                           │
└───────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. OpenTelemetry 集成方案

### 2.1 OpenTelemetry 架构

```
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                            OpenTelemetry 数据流                                           │
├───────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                           │
│   应用程序                                                                                 │
│   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                          │
│   │   Java服务      │  │   Python服务    │  │   Go服务        │                          │
│   │                 │  │                 │  │                 │                          │
│   │  ┌───────────┐  │  │  ┌───────────┐  │  │  ┌───────────┐  │                          │
│   │  │   OTel    │  │  │  │   OTel    │  │  │  │   OTel    │  │                          │
│   │  │   Agent   │  │  │  │   SDK     │  │  │  │   Agent   │  │                          │
│   │  └─────┬─────┘  │  │  └─────┬─────┘  │  │  └─────┬─────┘  │                          │
│   └────────┼────────┘  └────────┼────────┘  └────────┼────────┘                          │
│            │                    │                    │                                   │
│            └────────────────────┼────────────────────┘                                   │
│                                 │                                                        │
│                                 ▼                                                        │
│                         ┌─────────────────┐                                             │
│                         │  OTel Collector │                                             │
│                         │  ┌───────────┐  │                                             │
│                         │  │  Receiver │  │  ← 接收 Traces/Metrics/Logs                 │
│                         │  └─────┬─────┘  │                                             │
│                         │  ┌─────▼─────┐  │                                             │
│                         │  │ Processor │  │  ← 批处理、采样、富化                        │
│                         │  └─────┬─────┘  │                                             │
│                         │  ┌─────▼─────┐  │                                             │
│                         │  │  Exporter │  │  ← 导出到后端                                │
│                         │  └─────┬─────┘  │                                             │
│                         └────────┼────────┘                                             │
│                                  │                                                       │
│           ┌──────────────────────┼──────────────────────┐                                │
│           │                      │                      │                                │
│           ▼                      ▼                      ▼                                │
│    ┌──────────────┐     ┌──────────────┐     ┌──────────────┐                           │
│    │   Jaeger     │     │  Prometheus  │     │    Doris     │                           │
│    │  (Trace UI)  │     │  (Metrics)   │     │ (Logs+Trace) │                           │
│    └──────────────┘     └──────────────┘     └──────────────┘                           │
│                                                                                           │
└───────────────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Java 服务 OTel 集成

```xml
<!-- pom.xml -->
<dependencies>
    <!-- OpenTelemetry 自动埋点 -->
    <dependency>
        <groupId>io.opentelemetry.javaagent</groupId>
        <artifactId>opentelemetry-javaagent</artifactId>
        <version>1.32.0</version>
    </dependency>

    <!-- 自定义 Span 注解 -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-api</artifactId>
        <version>1.32.0</version>
    </dependency>

    <dependency>
        <groupId>io.opentelemetry.instrumentation</groupId>
        <artifactId>opentelemetry-instrumentation-annotations</artifactId>
        <version>1.32.0</version>
    </dependency>
</dependencies>
```

```yaml
# otel-config.yml
otel:
  exporter:
    otlp:
      endpoint: http://otel-collector:4317
      protocol: grpc
      timeout: 10s

  resource:
    attributes:
      service.name: ${SERVICE_NAME:aiops-control-plane}
      service.namespace: aiops
      service.version: ${APP_VERSION:1.0.0}
      deployment.environment: ${ENV:production}

  traces:
    sampler:
      # 智能采样：错误100%采集，正常10%采样
      type: parent_based_trace_id_ratio
      ratio: 0.1

  instrumentation:
    jdbc:
      enabled: true
    kafka:
      enabled: true
    http:
      client:
        enabled: true
      server:
        enabled: true
```

```java
// 自定义 Span 注解使用
@Service
public class AlertReceiverService {

    @WithSpan("alert.receive.process")
    public AlertDTO receiveAlert(
        @SpanAttribute("alert.id") String alertId,
        @SpanAttribute("alert.severity") String severity
    ) {
        // 方法内的操作自动成为子 Span
        return processAlert(alertId, severity);
    }

    @WithSpan("alert.noise.check")
    private boolean checkNoiseReduction(@SpanAttribute("alert.fingerprint") String fingerprint) {
        return noiseReducerService.shouldSuppress(fingerprint);
    }
}
```

### 2.3 Python AI 引擎 OTel 集成

```python
# app/telemetry/tracing.py
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor, ConsoleSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
from opentelemetry.instrumentation.redis import RedisInstrumentor
from functools import wraps


def init_tracer(service_name: str, endpoint: str = "http://otel-collector:4317"):
    """初始化链路追踪"""

    # 创建 Provider
    provider = TracerProvider(
        resource=Resource.create({
            "service.name": service_name,
            "service.namespace": "aiops",
            "deployment.environment": "production"
        })
    )

    # OTLP 导出器
    otlp_exporter = OTLPSpanExporter(
        endpoint=endpoint,
        insecure=True
    )

    # 批处理导出器
    span_processor = BatchSpanProcessor(
        otlp_exporter,
        max_queue_size=2048,
        max_export_batch_size=512,
        schedule_delay_millis=5000
    )

    provider.add_span_processor(span_processor)
    trace.set_tracer_provider(provider)

    # 自动埋点
    HTTPXClientInstrumentor().instrument()
    RedisInstrumentor().instrument()

    return provider


def trace_span(name: str, attributes: dict = None):
    """自定义追踪装饰器"""
    def decorator(func):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            tracer = trace.get_tracer(__name__)

            with tracer.start_as_current_span(name) as span:
                # 添加属性
                if attributes:
                    for key, value in attributes.items():
                        span.set_attribute(key, value)

                # 添加参数
                for key, value in kwargs.items():
                    if isinstance(value, (str, int, float, bool)):
                        span.set_attribute(f"arg.{key}", value)

                try:
                    result = await func(*args, **kwargs)
                    span.set_attribute("result.success", True)
                    return result
                except Exception as e:
                    span.set_attribute("result.success", False)
                    span.set_attribute("error.message", str(e))
                    span.record_exception(e)
                    raise
        return wrapper
    return decorator


# 应用到 AI 分析流程
class ReActOrchestrator:

    @trace_span("ai.analysis", {"component": "orchestrator"})
    async def analyze(self, alert: dict, **kwargs):
        """AI 分析主流程"""

        # 创建子 Span 用于每个推理步骤
        tracer = trace.get_tracer(__name__)

        for step in range(self.max_iterations):
            with tracer.start_as_current_span(f"reasoning.step_{step}") as step_span:
                step_span.set_attribute("step.number", step)
                step_span.set_attribute("alert.id", alert.get("id"))

                # LLM 推理
                with tracer.start_as_current_span("llm.inference") as llm_span:
                    llm_span.set_attribute("llm.model", "glm5")
                    llm_span.set_attribute("llm.tokens.input", len(input_tokens))

                    response = await self.llm.generate(messages)

                    llm_span.set_attribute("llm.tokens.output", len(output_tokens))
                    llm_span.set_attribute("llm.latency_ms", latency)

                # 工具调用
                if response.needs_tool_call:
                    with tracer.start_as_current_span("tool.execute") as tool_span:
                        tool_span.set_attribute("tool.name", response.tool_name)

                        result = await self.tool_executor.execute(
                            response.tool_name,
                            response.tool_args
                        )

                        tool_span.set_attribute("tool.success", result.success)
                        tool_span.set_attribute("tool.result_size", len(str(result)))
```

### 2.4 OTel Collector 配置

```yaml
# deploy/k8s/otel/collector-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: otel-collector-config
  namespace: aiops
data:
  otel-collector-config.yaml: |
    receivers:
      # gRPC 接收
      otlp:
        protocols:
          grpc:
            endpoint: 0.0.0.0:4317
            max_recv_msg_size_mib: 64
          http:
            endpoint: 0.0.0.0:4318

      # Prometheus 指标接收
      prometheus:
        config:
          scrape_configs:
            - job_name: 'otel-collector'
              scrape_interval: 10s
              static_configs:
                - targets: ['localhost:8888']

    processors:
      # 批量处理
      batch:
        timeout: 1s
        send_batch_size: 1024
        send_batch_max_size: 2048

      # 资源富化
      resource:
        attributes:
          - key: environment
            value: production
            action: upsert
          - key: platform
            value: aiops
            action: upsert

      # 智能采样处理器
      tail_sampling:
        decision_wait: 10s
        num_traces: 100000
        expected_new_traces_per_sec: 10000
        policies:
          # 错误 trace 100% 保留
          - name: errors
            type: status_code
            status_code: {status_codes: [ERROR]}

          # 慢请求 100% 保留
          - name: slow_requests
            type: latency
            latency: {threshold_ms: 1000}

          # 告警关联 trace 100% 保留
          - name: alert_correlation
            type: string_attribute
            string_attribute:
              key: has_alert
              values: ["true"]

          # 其他按 10% 采样
          - name: probabilistic
            type: probabilistic
            probabilistic: {sampling_percentage: 10}

      # 属性处理
      attributes:
        actions:
          # 注入告警上下文
          - key: aiops.alert_id
            from_attribute: alert_id
            action: upsert
          - key: aiops.trace_id
            from_attribute: trace_id
            action: upsert

    exporters:
      # 导出到 Doris (通过 OTLP HTTP)
      otlphttp/doris:
        endpoint: http://doris-trace-exporter:8080
        tls:
          insecure: true
        compression: gzip

      # 导出到 Jaeger 供开发调试
      jaeger:
        endpoint: jaeger-collector:14250
        tls:
          insecure: true

      # Prometheus 指标导出
      prometheusremotewrite:
        endpoint: http://prometheus:9090/api/v1/write

      # 日志导出
      logging:
        verbosity: detailed

      # Doris 日志导出
      file:
        path: /var/log/otel/traces.json

    service:
      pipelines:
        traces:
          receivers: [otlp]
          processors: [batch, resource, tail_sampling, attributes]
          exporters: [otlphttp/doris, jaeger]

        metrics:
          receivers: [otlp, prometheus]
          processors: [batch, resource]
          exporters: [prometheusremotewrite]

        logs:
          receivers: [otlp]
          processors: [batch, resource]
          exporters: [otlphttp/doris]
```

---

## 3. Trace-Log-Metric 关联存储设计

### 3.1 Doris Trace 表设计

```sql
-- Trace 数据表
CREATE TABLE IF NOT EXISTS aiops.traces (
    `timestamp` DATETIME NOT NULL,
    `trace_id` VARCHAR(32) NOT NULL,
    `span_id` VARCHAR(16) NOT NULL,
    `parent_span_id` VARCHAR(16),
    `span_name` VARCHAR(256) NOT NULL,
    `service_name` VARCHAR(64) NOT NULL,
    `service_namespace` VARCHAR(32),
    `span_kind` VARCHAR(16),         -- SERVER, CLIENT, PRODUCER, CONSUMER
    `duration_ms` INT NOT NULL,      -- Span 耗时
    `status_code` VARCHAR(8),        -- OK, ERROR, UNSET
    `status_message` VARCHAR(512),

    -- 属性
    `http_method` VARCHAR(8),
    `http_url` VARCHAR(2048),
    `http_status_code` INT,
    `db_system` VARCHAR(32),
    `db_statement` VARCHAR(4096),
    `messaging_system` VARCHAR(32),
    `messaging_destination` VARCHAR(256),

    -- AIops 专用字段
    `alert_id` VARCHAR(32),          -- 关联告警
    `tenant_id` VARCHAR(32) NOT NULL,
    `is_error` BOOLEAN DEFAULT FALSE,
    `is_slow` BOOLEAN DEFAULT FALSE,

    -- Events (JSON)
    `events` JSON,
    `links` JSON,

    -- 资源属性
    `host_name` VARCHAR(128),
    `pod_name` VARCHAR(128),
    `node_name` VARCHAR(128),

    INDEX idx_trace (`trace_id`) USING BITMAP,
    INDEX idx_service (`service_name`) USING BITMAP,
    INDEX idx_alert (`alert_id`) USING BITMAP,
    INDEX idx_duration (`duration_ms`),
    INDEX idx_http_url (`http_url`) USING INVERTED
)
DUPLICATE KEY(`timestamp`, `trace_id`, `span_id`)
PARTITION BY RANGE(`timestamp`) ()
DISTRIBUTED BY HASH(`trace_id`) BUCKETS 32
PROPERTIES (
    "replication_num" = "3",
    "dynamic_partition.enable" = "true",
    "dynamic_partition.time_unit" = "DAY",
    "dynamic_partition.start" = "-7",
    "dynamic_partition.end" = "3",
    "dynamic_partition.buckets" = "8"
);

-- Trace-Log 关联视图
CREATE VIEW IF NOT EXISTS aiops.trace_log_correlation AS
SELECT
    t.trace_id,
    t.span_id,
    t.span_name,
    t.service_name,
    t.duration_ms,
    t.is_error,
    l.timestamp as log_timestamp,
    l.level as log_level,
    l.message as log_message,
    l.parsed_fields as log_fields
FROM aiops.traces t
LEFT JOIN aiops.logs l
    ON t.trace_id = l.trace_id
    AND t.timestamp BETWEEN l.timestamp - INTERVAL 1 MINUTE
                        AND l.timestamp + INTERVAL 1 MINUTE
WHERE t.is_error = TRUE
ORDER BY t.timestamp DESC;

-- Trace-Metric 关联视图
CREATE VIEW IF NOT EXISTS aiops.trace_metric_correlation AS
SELECT
    t.trace_id,
    t.service_name,
    t.timestamp as trace_time,
    t.duration_ms,
    m.metric_name,
    m.metric_value,
    m.timestamp as metric_time,
    ABS(TIMESTAMPDIFF(SECOND, t.timestamp, m.timestamp)) as time_diff
FROM aiops.traces t
JOIN aiops.metrics m
    ON t.service_name = m.service_id
    AND ABS(TIMESTAMPDIFF(SECOND, t.timestamp, m.timestamp)) <= 60
WHERE t.timestamp > NOW() - INTERVAL 1 HOUR;
```

### 3.2 TraceContext 传递机制

```java
// TraceContextPropagation.java
@Component
public class TraceContextManager {

    @Autowired
    private Tracer tracer;

    /**
     * 从告警中提取或创建 TraceContext
     */
    public Context extractOrCreateContext(AlertDTO alert) {
        // 尝试从告警标签中获取 trace_id
        String traceId = alert.getLabels().get("trace_id");
        String spanId = alert.getLabels().get("span_id");

        if (StringUtils.hasText(traceId)) {
            // 复用已有 Trace
            SpanContext spanContext = SpanContext.createFromRemoteParent(
                traceId,
                spanId != null ? spanId : SpanId.getInvalid(),
                TraceFlags.getSampled(),
                TraceState.getDefault()
            );
            return Context.current().with(Span.wrap(spanContext));
        }

        // 创建新的 Trace，与告警关联
        Span span = tracer.spanBuilder("alert.correlation")
            .setAttribute("alert.id", alert.getAlertId())
            .setAttribute("alert.title", alert.getTitle())
            .setAttribute("alert.service", alert.getServiceId())
            .startSpan();

        // 将 trace_id 写回告警
        alert.getLabels().put("trace_id", span.getSpanContext().getTraceId());

        return Context.current().with(span);
    }

    /**
     * 注入 TraceContext 到下游请求
     */
    public void injectContext(HttpHeaders headers) {
        TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();
        propagator.inject(Context.current(), headers, HttpHeaderSetter.INSTANCE);
    }
}

// Kafka 消息传递 TraceContext
@Component
public class KafkaTracePropagator {

    public ProducerRecord<String, String> injectTraceContext(
        ProducerRecord<String, String> record
    ) {
        TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();

        propagator.inject(Context.current(), record.headers(),
            (carrier, key, value) -> {
                if (carrier != null) {
                    carrier.add(key, value.getBytes(StandardCharsets.UTF_8));
                }
            }
        );

        return record;
    }

    public Context extractTraceContext(ConsumerRecord<String, String> record) {
        TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();

        return propagator.extract(
            Context.current(),
            record.headers(),
            (carrier, key) -> {
                Header header = carrier.lastHeader(key);
                return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
            }
        );
    }
}
```

---

## 4. 前端链路可视化集成

### 4.1 AntV G6 Trace 拓扑扩展

```typescript
// components/TraceGraph/TraceGraph.tsx
import React, { useEffect, useRef } from 'react';
import { Graph } from '@antv/g6';

interface TraceNode {
  id: string;
  service: string;
  operation: string;
  duration: number;
  status: 'success' | 'error' | 'slow';
  startTime: number;
  depth: number;
}

interface TraceEdge {
  source: string;
  target: string;
  duration: number;
}

interface TraceGraphProps {
  traceId: string;
  nodes: TraceNode[];
  edges: TraceEdge[];
  alertSpanId?: string;
  onNodeClick?: (node: TraceNode) => void;
}

export const TraceGraph: React.FC<TraceGraphProps> = ({
  traceId,
  nodes,
  edges,
  alertSpanId,
  onNodeClick
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const graph = new Graph({
      container: containerRef.current,
      width: containerRef.current.clientWidth,
      height: 400,
      layout: {
        type: 'trace-dagre',
        rankdir: 'LR',
        nodesep: 50,
        ranksep: 80,
        // 按时间轴布局
        align: 'UL'
      },
      nodeCfg: {
        type: 'trace-node',
        size: [180, 60],
        style: {
          fill: '#fff',
          stroke: '#e0e0e0',
          lineWidth: 1,
          radius: 4
        },
        labelCfg: {
          style: {
            fill: '#333',
            fontSize: 12
          }
        },
        // 根据状态着色
        itemType: (node: TraceNode) => {
          if (node.id === alertSpanId) return 'alert-node';
          switch (node.status) {
            case 'error': return 'error-node';
            case 'slow': return 'slow-node';
            default: return 'normal-node';
          }
        }
      },
      edgeCfg: {
        type: 'trace-edge',
        style: {
          stroke: '#999',
          lineWidth: 1,
          // 边的粗细表示调用耗时
          lineWidth: (edge: TraceEdge) => {
            if (edge.duration > 1000) return 3;
            if (edge.duration > 100) return 2;
            return 1;
          }
        },
        labelCfg: {
          style: { fill: '#666', fontSize: 10 },
          // 显示耗时
          content: (edge: TraceEdge) => `${edge.duration}ms`
        }
      },
      behaviors: [
        'drag-canvas',
        'zoom-canvas',
        'click-select'
      ]
    });

    graph.data({ nodes, edges });
    graph.render();

    // 聚焦到告警节点
    if (alertSpanId) {
      graph.focusItem(alertSpanId, {
        animation: true,
        padding: [50, 50, 50, 50]
      });

      // 高亮告警路径
      highlightErrorPath(graph, alertSpanId);
    }

    graph.on('node:click', (evt) => {
      onNodeClick?.(evt.item.getModel() as TraceNode);
    });

    graphRef.current = graph;

    return () => graph.destroy();
  }, [traceId, nodes, edges, alertSpanId]);

  // 高亮错误传播路径
  const highlightErrorPath = (graph: Graph, alertNodeId: string) => {
    const path = findRootCausePath(graph, alertNodeId);

    path.forEach((nodeId, index) => {
      graph.updateItem(nodeId, {
        style: {
          stroke: '#ff4d4f',
          lineWidth: 3,
          shadowColor: '#ff4d4f',
          shadowBlur: 10
        }
      });
    });
  };

  return <div ref={containerRef} style={{ width: '100%', height: 400 }} />;
};

// 自定义 Trace 节点
G6.registerNode('trace-node', {
  draw(cfg, group) {
    const { service, operation, duration, status } = cfg.data;

    // 状态颜色
    const colors = {
      success: '#52c41a',
      error: '#ff4d4f',
      slow: '#faad14'
    };

    const keyShape = group.addShape('rect', {
      attrs: {
        x: -90,
        y: -30,
        width: 180,
        height: 60,
        fill: '#fff',
        stroke: colors[status] || '#999',
        lineWidth: 2,
        radius: 4
      },
      name: 'key-shape'
    });

    // 服务名
    group.addShape('text', {
      attrs: {
        x: 0,
        y: -10,
        text: service,
        fontSize: 14,
        fontWeight: 'bold',
        fill: '#333',
        textAlign: 'center'
      }
    });

    // 操作名
    group.addShape('text', {
      attrs: {
        x: 0,
        y: 5,
        text: operation,
        fontSize: 11,
        fill: '#666',
        textAlign: 'center'
      }
    });

    // 耗时
    group.addShape('text', {
      attrs: {
        x: 0,
        y: 22,
        text: `${duration}ms`,
        fontSize: 12,
        fontWeight: 'bold',
        fill: colors[status] || '#666',
        textAlign: 'center'
      }
    });

    return keyShape;
  }
});
```

### 4.2 告警详情页 Trace 集成

```typescript
// components/AlertDetail/AlertTraceSection.tsx
import React, { useState, useEffect } from 'react';
import { Card, Timeline, Tag, Spin } from 'antd';
import { TraceGraph } from '../TraceGraph';
import { useTraceCorrelation } from '@/hooks/useTraceCorrelation';

interface AlertTraceSectionProps {
  alertId: string;
  traceId?: string;
}

export const AlertTraceSection: React.FC<AlertTraceSectionProps> = ({
  alertId,
  traceId: initialTraceId
}) => {
  const [selectedSpan, setSelectedSpan] = useState<TraceNode | null>(null);

  const {
    traceData,
    correlatedLogs,
    correlatedMetrics,
    loading,
    error
  } = useTraceCorrelation(alertId, initialTraceId);

  if (loading) return <Spin size="large" />;
  if (error) return <Alert message="加载链路追踪失败" type="error" />;
  if (!traceData) return <Alert message="未找到关联的链路数据" type="info" />;

  return (
    <div className="alert-trace-section">
      <Card title="调用链路可视化" bordered={false}>
        <TraceGraph
          traceId={traceData.traceId}
          nodes={traceData.nodes}
          edges={traceData.edges}
          alertSpanId={traceData.alertSpanId}
          onNodeClick={setSelectedSpan}
        />

        {/* 选中 Span 详情 */}
        {selectedSpan && (
          <div className="span-detail">
            <h4>Span 详情</h4>
            <p><strong>服务:</strong> {selectedSpan.service}</p>
            <p><strong>操作:</strong> {selectedSpan.operation}</p>
            <p><strong>耗时:</strong> {selectedSpan.duration}ms</p>
            <p><strong>开始时间:</strong> {new Date(selectedSpan.startTime).toLocaleString()}</p>
          </div>
        )}
      </Card>

      {/* 关联日志 */}
      <Card title="关联日志" bordered={false} style={{ marginTop: 16 }}>
        <Timeline mode="left">
          {correlatedLogs?.map((log) => (
            <Timeline.Item
              key={log.id}
              label={new Date(log.timestamp).toLocaleTimeString()}
              color={log.level === 'ERROR' ? 'red' : 'blue'}
            >
              <Tag color={log.level === 'ERROR' ? 'red' : 'blue'}>
                {log.level}
              </Tag>
              <span>{log.message}</span>
            </Timeline.Item>
          ))}
        </Timeline>
      </Card>

      {/* 关联指标 */}
      <Card title="关联指标 (告警前后1分钟)" bordered={false} style={{ marginTop: 16 }}>
        <MetricSparkline
          data={correlatedMetrics}
          alertTime={traceData.alertTime}
        />
      </Card>

      {/* AI 链路分析 */}
      <Card title="AI 链路分析" bordered={false} style={{ marginTop: 16 }}>
        <TraceAIAnalysis
          traceId={traceData.traceId}
          alertSpanId={traceData.alertSpanId}
        />
      </Card>
    </div>
  );
};

// AI 链路分析组件
const TraceAIAnalysis: React.FC<{ traceId: string; alertSpanId: string }> = ({
  traceId,
  alertSpanId
}) => {
  const { analysis, streaming } = useTraceAIAnalysis(traceId, alertSpanId);

  return (
    <div className="trace-ai-analysis">
      {streaming ? (
        <TypeWriter text={analysis} speed={50} />
      ) : (
        <div className="analysis-result">
          <h5>链路根因分析</h5>
          <pre>{analysis}</pre>
        </div>
      )}
    </div>
  );
};
```

---

## 5. AI 利用链路数据进行分析

### 5.1 链路数据增强 AI 分析

```python
# app/services/trace_analyzer.py
from typing import List, Dict, Optional
from dataclasses import dataclass


@dataclass
class TraceAnalysisResult:
    root_cause_service: str
    root_cause_span: str
    error_propagation_path: List[str]
    slowest_span: Optional[str]
    confidence: float
    explanation: str


class TraceAIAnalyzer:
    """基于链路追踪数据的 AI 分析器"""

    def __init__(self, doris_client, llm_adapter):
        self.doris = doris_client
        self.llm = llm_adapter

    async def analyze_trace_for_alert(
        self,
        alert_id: str,
        trace_id: Optional[str] = None
    ) -> TraceAnalysisResult:
        """为告警分析链路数据"""

        # 1. 获取告警关联的 Trace
        if not trace_id:
            trace_id = await self._find_trace_by_alert(alert_id)

        if not trace_id:
            return None

        # 2. 获取完整调用链
        spans = await self._get_trace_spans(trace_id)

        # 3. 分析错误传播路径
        error_path = self._analyze_error_propagation(spans)

        # 4. 分析慢请求
        slow_spans = self._analyze_slow_spans(spans)

        # 5. 获取关联日志
        correlated_logs = await self._get_correlated_logs(trace_id, spans)

        # 6. 获取关联指标
        correlated_metrics = await self._get_correlated_metrics(spans)

        # 7. LLM 综合分析
        analysis_prompt = self._build_analysis_prompt(
            spans, error_path, slow_spans,
            correlated_logs, correlated_metrics
        )

        llm_result = await self.llm.generate(analysis_prompt)

        return TraceAnalysisResult(
            root_cause_service=llm_result.get('root_cause_service'),
            root_cause_span=llm_result.get('root_cause_span'),
            error_propagation_path=error_path,
            slowest_span=slow_spans[0]['span_id'] if slow_spans else None,
            confidence=llm_result.get('confidence', 0.8),
            explanation=llm_result.get('explanation')
        )

    def _analyze_error_propagation(self, spans: List[dict]) -> List[str]:
        """分析错误传播路径"""
        # 构建父子关系图
        span_map = {s['span_id']: s for s in spans}

        # 找到所有错误 Span
        error_spans = [s for s in spans if s['status_code'] == 'ERROR']

        if not error_spans:
            return []

        # 找到最底层的错误（叶节点）
        def get_children_count(span_id: str) -> int:
            return sum(1 for s in spans if s.get('parent_span_id') == span_id)

        leaf_errors = [s for s in error_spans if get_children_count(s['span_id']) == 0]

        if not leaf_errors:
            leaf_errors = error_spans

        # 构建根因路径
        paths = []
        for error in leaf_errors:
            path = [error['span_id']]
            current = error

            while current.get('parent_span_id'):
                parent_id = current['parent_span_id']
                parent = span_map.get(parent_id)
                if parent:
                    path.append(parent_id)
                    current = parent
                else:
                    break

            paths.append(list(reversed(path)))

        # 返回最长的路径（最具体的根因）
        return max(paths, key=len) if paths else []

    def _analyze_slow_spans(self, spans: List[dict], threshold_ms: int = 1000) -> List[dict]:
        """分析慢 Span"""
        slow_spans = [
            s for s in spans
            if s['duration_ms'] > threshold_ms
        ]

        # 按耗时排序
        slow_spans.sort(key=lambda x: x['duration_ms'], reverse=True)

        return slow_spans[:5]  # 返回前5个最慢的

    def _build_analysis_prompt(
        self,
        spans: List[dict],
        error_path: List[str],
        slow_spans: List[dict],
        logs: List[dict],
        metrics: List[dict]
    ) -> str:
        """构建 LLM 分析提示"""

        spans_summary = "\n".join([
            f"- {s['service_name']}/{s['span_name']}: {s['duration_ms']}ms, status={s['status_code']}"
            for s in spans[:20]  # 限制数量
        ])

        error_spans = [s for s in spans if s['span_id'] in error_path]
        error_summary = "\n".join([
            f"- {s['service_name']}/{s['span_name']}: {s.get('status_message', 'Unknown error')}"
            for s in error_spans
        ])

        slow_summary = "\n".join([
            f"- {s['service_name']}/{s['span_name']}: {s['duration_ms']}ms"
            for s in slow_spans[:5]
        ])

        log_summary = "\n".join([
            f"- [{l['level']}] {l['message'][:100]}"
            for l in logs[:10]
        ])

        return f"""
你是一个运维专家。请分析以下分布式调用链数据，找出根因：

## 调用链信息
总 Span 数: {len(spans)}
错误 Span 数: {len([s for s in spans if s['status_code'] == 'ERROR'])}

### 调用路径
{spans_summary}

### 错误传播路径
{error_summary}

### 慢请求 (超过1秒)
{slow_summary}

### 关联日志
{log_summary}

## 任务
1. 根据错误传播路径，识别最可能的根因服务和操作
2. 分析慢请求和错误的关联性
3. 结合日志给出具体的根因解释
4. 给出置信度分数(0-1)

请以JSON格式输出：
{{
    "root_cause_service": "服务名",
    "root_cause_span": "操作名",
    "root_cause_explanation": "详细解释",
    "severity": "critical|high|medium|low",
    "confidence": 0.95,
    "recommended_actions": ["建议1", "建议2"]
}}
"""

    async def _find_trace_by_alert(self, alert_id: str) -> Optional[str]:
        """查找告警关联的 Trace"""
        sql = f"""
        SELECT trace_id FROM aiops.traces
        WHERE alert_id = '{alert_id}'
        ORDER BY timestamp DESC
        LIMIT 1
        """
        result = await self.doris.execute(sql)
        return result[0]['trace_id'] if result else None

    async def _get_trace_spans(self, trace_id: str) -> List[dict]:
        """获取 Trace 的所有 Span"""
        sql = f"""
        SELECT * FROM aiops.traces
        WHERE trace_id = '{trace_id}'
        ORDER BY timestamp ASC
        """
        return await self.doris.execute(sql)

    async def _get_correlated_logs(
        self,
        trace_id: str,
        spans: List[dict]
    ) -> List[dict]:
        """获取关联日志"""
        services = set(s['service_name'] for s in spans)
        time_range = self._get_time_range(spans)

        sql = f"""
        SELECT * FROM aiops.logs
        WHERE service_id IN ({','.join(f"'{s}'" for s in services)})
        AND (trace_id = '{trace_id}' OR level = 'ERROR')
        AND timestamp BETWEEN '{time_range['start']}' AND '{time_range['end']}'
        ORDER BY timestamp ASC
        LIMIT 50
        """
        return await self.doris.execute(sql)

    async def _get_correlated_metrics(self, spans: List[dict]) -> List[dict]:
        """获取关联指标"""
        services = set(s['service_name'] for s in spans)
        time_range = self._get_time_range(spans)

        sql = f"""
        SELECT * FROM aiops.metrics
        WHERE service_id IN ({','.join(f"'{s}'" for s in services)})
        AND timestamp BETWEEN '{time_range['start']}' AND '{time_range['end']}'
        ORDER BY timestamp ASC
        """
        return await self.doris.execute(sql)

    def _get_time_range(self, spans: List[dict]) -> dict:
        """获取时间范围"""
        timestamps = [s['timestamp'] for s in spans if 'timestamp' in s]
        if not timestamps:
            return {'start': 'NOW() - INTERVAL 5 MINUTE', 'end': 'NOW()'}

        return {
            'start': 'MIN(timestamp) - INTERVAL 1 MINUTE',
            'end': 'MAX(timestamp) + INTERVAL 1 MINUTE'
        }
```

### 5.2 MCP Trace 工具

```python
# app/mcp/tools/trace_tools.py
from mcp.server import FastMCP

mcp = FastMCP("aiops-trace")

@mcp.tool()
async def query_trace_by_alert(alert_id: str) -> dict:
    """
    根据告警ID查询关联的调用链路

    用于AI分析告警相关的分布式调用链，帮助定位跨服务问题
    """
    trace_analyzer = get_trace_analyzer()
    result = await trace_analyzer.analyze_trace_for_alert(alert_id)

    if not result:
        return {"found": False, "message": "未找到关联的调用链"}

    return {
        "found": True,
        "trace_id": result.trace_id if hasattr(result, 'trace_id') else None,
        "root_cause_service": result.root_cause_service,
        "root_cause_span": result.root_cause_span,
        "error_propagation_path": result.error_propagation_path,
        "slowest_operation": result.slowest_span,
        "confidence": result.confidence,
        "analysis": result.explanation
    }

@mcp.tool()
async def get_service_dependencies(service_name: str, time_range: str = "1h") -> dict:
    """
    获取服务的上下游依赖关系

    用于分析故障在服务间的传播路径
    """
    sql = f"""
    SELECT
        upstream_service,
        downstream_service,
        COUNT(*) as call_count,
        AVG(duration_ms) as avg_latency,
        SUM(CASE WHEN status_code = 'ERROR' THEN 1 ELSE 0 END) as error_count
    FROM (
        SELECT
            t1.service_name as upstream_service,
            t2.service_name as downstream_service,
            t2.duration_ms,
            t2.status_code
        FROM aiops.traces t1
        JOIN aiops.traces t2 ON t1.span_id = t2.parent_span_id
        WHERE t1.timestamp > NOW() - INTERVAL '{time_range}'
    ) calls
    WHERE upstream_service = '{service_name}' OR downstream_service = '{service_name}'
    GROUP BY upstream_service, downstream_service
    """

    result = await doris.execute(sql)

    upstream = [r for r in result if r['downstream_service'] == service_name]
    downstream = [r for r in result if r['upstream_service'] == service_name]

    return {
        "service": service_name,
        "upstream_dependencies": [
            {"service": r["upstream_service"], "calls": r["call_count"], "errors": r["error_count"]}
            for r in upstream
        ],
        "downstream_dependencies": [
            {"service": r["downstream_service"], "calls": r["call_count"], "errors": r["error_count"]}
            for r in downstream
        ]
    }

@mcp.tool()
async def find_bottleneck_services(time_range: str = "1h", threshold_ms: int = 1000) -> list:
    """
    发现系统中的性能瓶颈服务

    识别平均响应时间超过阈值的服务
    """
    sql = f"""
    SELECT
        service_name,
        COUNT(*) as total_spans,
        AVG(duration_ms) as avg_duration,
        PERCENTILE(duration_ms, 0.99) as p99_duration,
        SUM(CASE WHEN duration_ms > {threshold_ms} THEN 1 ELSE 0 END) as slow_count
    FROM aiops.traces
    WHERE timestamp > NOW() - INTERVAL '{time_range}'
    GROUP BY service_name
    HAVING avg_duration > {threshold_ms} OR p99_duration > {threshold_ms * 2}
    ORDER BY avg_duration DESC
    """

    return await doris.execute(sql)
```

## 6. 系统监控体系

### 6.1 监控架构

```
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                                  监控体系架构                                           │
├───────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                           │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────────┐   │
│   │  数据源      │────►│  采集层      │────►│  存储层      │────►│  告警与分析      │   │
│   └──────────────┘     └──────────────┘     └──────────────┘     └──────────────────┘   │
│      │                     │                     │                     │                │
│      ▼                     ▼                     ▼                     ▼                │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────────┘   │
│   │  应用指标    │     │  Prometheus  │     │  VictoriaMetrics│   │                     │
│   │  系统指标    │     │  Node Exporter│     │  (长期存储)    │   │                     │
│   │  网络指标    │     │  Blackbox    │     └──────────────┘   │                     │
│   │  业务指标    │     └──────────────┘                        │                     │
│   └──────────────┘                                            │                     │
│                                                               │                     │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    │                     │
│   │  日志        │────►│  iLogtail    │────►│  Doris       │────┘                     │
│   └──────────────┘     └──────────────┘     └──────────────┘                            │
│                                                                                           │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐                            │
│   │  链路追踪    │────►│  OpenTelemetry│────►│  Jaeger/Doris│                            │
│   └──────────────┘     └──────────────┘     └──────────────┘                            │
│                                                                                           │
└───────────────────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 监控指标体系

| 指标类别 | 具体指标 | 监控频率 | 告警阈值 |
|----------|----------|----------|----------|
| 系统指标 | CPU使用率 | 10s | >80%持续5分钟 |
| 系统指标 | 内存使用率 | 10s | >85%持续5分钟 |
| 系统指标 | 磁盘使用率 | 1m | >90% |
| 系统指标 | 网络流量 | 10s | 超过带宽80% |
| 应用指标 | 响应时间 | 10s | P99>1000ms |
| 应用指标 | 错误率 | 1m | >5% |
| 应用指标 | QPS | 10s | 超过配置上限 |
| 应用指标 | 线程池使用率 | 1m | >80% |
| 业务指标 | 告警数量 | 1m | >100/分钟 |
| 业务指标 | AI推理延迟 | 1m | >5000ms |

### 6.3 告警管理

#### 6.3.1 告警分级

| 级别 | 描述 | 处理时间 | 通知方式 |
|------|------|----------|----------|
| P0 | 系统完全不可用 | 立即处理 | 电话 + 短信 + 邮件 |
| P1 | 核心功能受损 | 30分钟内 | 短信 + 邮件 |
| P2 | 非核心功能受损 | 2小时内 | 邮件 |
| P3 | 性能异常 | 4小时内 | 邮件 |

#### 6.3.2 告警降噪

- **时间窗口聚合**: 5分钟内同类告警归为一组
- **根源告警识别**: AI判断根因，其他为扩散告警
- **抑制策略**: 维护窗口、依赖抑制
- **智能静默**: 基于历史模式自动静默误报

### 6.4 运维自动化

#### 6.4.1 自动修复

- **重启服务**: 针对服务假死情况
- **扩缩容**: 基于负载自动调整实例数
- **切换副本**: 主节点故障时自动切换
- **清理缓存**: 缓存异常时自动清理

#### 6.4.2 运维脚本

```bash
#!/bin/bash
# 服务健康检查脚本

service_name=$1

echo "Checking $service_name health..."

# 检查服务状态
status=$(systemctl status $service_name | grep "Active:")

if [[ $status == *"active (running)"* ]]; then
    echo "$service_name is running"
    
    # 检查响应时间
    response_time=$(curl -o /dev/null -s -w "%{time_total}" http://localhost:8080/health)
    
    if (( $(echo "$response_time > 5" | bc -l) )); then
        echo "WARNING: $service_name response time is high: $response_time seconds"
        # 触发告警
        curl -X POST http://alert-manager/api/v1/alerts -d "{
            \"alerts\": [{
                \"labels\": {
                    \"alertname\": \"HighResponseTime\",
                    \"service\": \"$service_name\",
                    \"severity\": \"warning\"  
                },
                \"annotations\": {
                    \"summary\": \"$service_name response time is high\",
                    \"description\": \"Response time: $response_time seconds\"
                }
            }]\n"
    else
        echo "$service_name response time is normal: $response_time seconds"
    fi
else
    echo "ERROR: $service_name is not running"
    # 尝试重启
    systemctl restart $service_name
    sleep 5
    
    # 检查是否重启成功
    status=$(systemctl status $service_name | grep "Active:")
    if [[ $status == *"active (running)"* ]]; then
        echo "$service_name restarted successfully"
    else
        echo "FAILED: $service_name restart failed"
        # 触发P0告警
        curl -X POST http://alert-manager/api/v1/alerts -d "{
            \"alerts\": [{
                \"labels\": {
                    \"alertname\": \"ServiceDown\",
                    \"service\": \"$service_name\",
                    \"severity\": \"critical\"  
                },
                \"annotations\": {
                    \"summary\": \"$service_name is down\",
                    \"description\": \"Service failed to restart\"
                }
            }]\n"
    fi
fi
```

### 6.5 容量规划

#### 6.5.1 资源监控

- **CPU/内存使用趋势**
- **存储使用增长**
- **网络带宽使用**
- **数据库连接数**
- **API调用量**

#### 6.5.2 容量预测

- **基于历史数据的趋势分析**
- **季节性模式识别**
- **业务增长预测**
- **自动扩容建议**

### 6.6 故障处理流程

```
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                                    故障处理流程                                         │
├───────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                           │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────────┐   │
│   │  故障发现    │────►│  故障分类    │────►│  根因分析    │────►│  故障修复        │   │
│   └──────────────┘     └──────────────┘     └──────────────┘     └──────────────────┘   │
│      │                     │                     │                     │                │
│      ▼                     ▼                     ▼                     ▼                │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────────┘   │
│   │  监控告警    │     │  P0/P1/P2/P3 │     │  AI分析      │     │                     │
│   │  用户反馈    │     └──────────────┘     │  专家分析    │     │                     │
│   │  日志异常    │                          └──────────────┘     │                     │
│   └──────────────┘                                              │                     │
│                                                                 │                     │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    │                     │
│   │  故障验证    │◄────┤  验证修复    │◄────┤  实施修复    │────┘                     │
│   └──────────────┘     └──────────────┘     └──────────────┘                            │
│        │                     │                     │                                    │
│        ▼                     │                     │                                    │
│   ┌──────────────┐           │                     │                                    │
│   │  故障总结    │───────────┘                     │                                    │
│   └──────────────┘                                 │                                    │
│        │                                           │                                    │
│        ▼                                           │                                    │
│   ┌──────────────┐                                 │                                    │
│   │  知识库更新  │─────────────────────────────────┘                                    │
│   └──────────────┘                                                                       │
│                                                                                           │
└───────────────────────────────────────────────────────────────────────────────────────────┘
```

### 6.7 监控大屏

#### 6.7.1 系统概览大屏

- **健康状态**: 系统整体健康评分
- **告警概览**: 各级别告警数量
- **性能指标**: CPU、内存、网络使用情况
- **业务指标**: QPS、响应时间、错误率
- **AI分析**: 根因分析准确率、推理延迟

#### 6.7.2 服务详情大屏

- **服务拓扑**: 服务依赖关系图
- **性能指标**: 响应时间、错误率、QPS
- **资源使用**: CPU、内存、磁盘使用
- **调用链路**: 分布式调用链可视化
- **关联日志**: 服务相关的错误日志

---

*本文档定义了 AIOps 平台的分布式链路追踪架构，通过 OpenTelemetry 实现 Trace-Log-Metric 三大可观测数据的关联，显著提升 AI 根因分析的准确性，并为前端大屏提供可视化能力。*
