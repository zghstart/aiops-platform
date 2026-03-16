# AIOps 智能运维平台 - Java 控制面开发指南

## 1. 项目结构

```
aiops-control-plane/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── aiops/
│   │   │           ├── ControlPlaneApplication.java
│   │   │           ├── config/
│   │   │           │   ├── SecurityConfig.java
│   │   │           │   ├── KafkaConfig.java
│   │   │           │   ├── DorisConfig.java
│   │   │           │   └── RedisConfig.java
│   │   │           ├── controller/
│   │   │           │   ├── AlertController.java
│   │   │           │   ├── AIController.java
│   │   │           │   ├── DashboardController.java
│   │   │           │   ├── CMDBController.java
│   │   │           │   └── InternalController.java
│   │   │           ├── service/
│   │   │           │   ├── alert/
│   │   │           │   │   ├── AlertReceiverService.java
│   │   │           │   │   ├── NoiseReducerService.java
│   │   │           │   │   └── IncidentManagerService.java
│   │   │           │   ├── ai/
│   │   │           │   │   ├── AIEngineClient.java
│   │   │           │   │   ├── AITaskDispatcher.java
│   │   │           │   │   └── ReasoningStreamService.java
│   │   │           │   ├── topology/
│   │   │           │   │   ├── CMDBClient.java
│   │   │           │   │   ├── TopologyService.java
│   │   │           │   │   └── ImpactAnalyzer.java
│   │   │           │   ├── dashboard/
│   │   │           │   │   ├── DashboardDataService.java
│   │   │           │   │   └── MetricAggregator.java
│   │   │           │   └── cost/
│   │   │           │       └── CostTrackingService.java
│   │   │           ├── repository/
│   │   │           │   ├── entity/
│   │   │           │   │   ├── Alert.java
│   │   │           │   │   ├── Incident.java
│   │   │           │   │   ├── ServiceEntity.java
│   │   │           │   │   └── Tenant.java
│   │   │           │   └── repository/
│   │   │           │       ├── AlertRepository.java
│   │   │           │       └── IncidentRepository.java
│   │   │           ├── domain/
│   │   │           │   ├── model/
│   │   │           │   │   ├── AlertModel.java
│   │   │           │   │   ├── IncidentModel.java
│   │   │           │   │   └── AnalysisResult.java
│   │   │           │   └── event/
│   │   │           │       ├── AlertReceivedEvent.java
│   │   │           │       └── AnalysisCompletedEvent.java
│   │   │           ├── infrastructure/
│   │   │           │   ├── kafka/
│   │   │           │   │   ├── AlertConsumer.java
│   │   │           │   │   └── AnalysisResultProducer.java
│   │   │           │   ├── doris/
│   │   │           │   │   └── DorisClient.java
│   │   │           │   └── redis/
│   │   │           │       └── RedisCache.java
│   │   │           └── exception/
│   │   │               └── GlobalExceptionHandler.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── db/
│   │       │   └── migration/
│   │       │       └── V1__init.sql
│   │       └── logback-spring.xml
│   └── test/
├── pom.xml
├── Dockerfile
└── docker-compose.yml
```

---

## 2. 核心业务代码

### 2.1 AlertController - 告警接收

```java
package com.aiops.controller;

import com.aiops.domain.model.AlertModel;
import com.aiops.service.alert.AlertReceiverService;
import com.aiops.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertReceiverService alertReceiver;

    /**
     * 接收外部系统告警(Webhook)
     */
    @PostMapping("/webhook")
    public ApiResponse<AlertResponse> receiveAlert(
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody AlertWebhookRequest request) {

        log.info("Received alert from source: {}, tenant: {}",
                request.getSource(), request.getLabels().get("tenant_id"));

        // 验证API Key并提取租户
        String tenantId = validateApiKey(apiKey);

        // 接收告警
        AlertModel alert = alertReceiver.receive(request, tenantId);

        return ApiResponse.success(
            AlertResponse.builder()
                .alertId(alert.getId())
                .incidentId(alert.getIncidentId())
                .status(alert.getStatus())
                .aiStatus(alert.getAiStatus())
                .build()
        );
    }

    /**
     * 批量查询告警
     */
    @GetMapping
    public ApiResponse<PageResult<AlertVO>> listAlerts(
            @RequestParam String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String serviceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResult<AlertVO> result = alertReceiver.list(
            tenantId, status, severity, serviceId, page, size);

        return ApiResponse.success(result);
    }

    /**
     * 告警抑制/确认
     */
    @PostMapping("/{alertId}/silence")
    public ApiResponse<Void> silenceAlert(
            @PathVariable String alertId,
            @RequestBody SilenceRequest request) {

        alertReceiver.silence(alertId, request.getDurationMinutes(), request.getReason());
        return ApiResponse.success();
    }
}

// DTO定义
@Data
@Builder
public class AlertWebhookRequest {
    private String alertId;
    private String source;
    private String severity;
    private String title;
    private String description;
    private String serviceId;
    private Map<String, String> labels;
    private Instant startsAt;
    private Map<String, Object> payload;
}

@Data
@Builder
public class AlertResponse {
    private String alertId;
    private String incidentId;
    private String status;
    private String aiStatus;
}
```

### 2.2 AlertReceiverService - 告警处理核心

```java
package com.aiops.service.alert;

import com.aiops.domain.event.AlertReceivedEvent;
import com.aiops.domain.model.AlertModel;
import com.aiops.domain.model.IncidentModel;
import com.aiops.repository.entity.Alert;
import com.aiops.infrastructure.kafka.AlertConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertReceiverService {

    private final AlertRepository alertRepository;
    private final IncidentRepository incidentRepository;
    private final NoiseReducerService noiseReducer;
    private final AITaskDispatcher aiDispatcher;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 核心接收流程
     */
    @Transactional
    public AlertModel receive(AlertWebhookRequest request, String tenantId) {

        // 1. 创建Alert记录
        Alert alert = Alert.builder()
            .alertId(request.getAlertId() != null ? request.getAlertId() : generateId())
            .tenantId(tenantId)
            .source(request.getSource())
            .severity(request.getSeverity())
            .title(request.getTitle())
            .description(request.getDescription())
            .serviceId(request.getServiceId())
            .labels(request.getLabels())
            .startsAt(request.getStartsAt())
            .status("active")
            .aiStatus("pending")
            .payload(request.getPayload())
            .build();

        // 2. 降噪处理
        NoiseReductionResult noiseResult = noiseReducer.process(alert);

        if (noiseResult.isSuppressed()) {
            alert.setStatus("suppressed");
            alert.setSilencedBy(noiseResult.getRuleId());
            log.info("Alert {} suppressed by rule {}", alert.getAlertId(), noiseResult.getRuleId());
        }

        // 3. 关联Incident（聚类）
        IncidentModel incident = findOrCreateIncident(alert, noiseResult.getClusterKey());
        alert.setIncidentId(incident.getId());

        // 4. 保存
        alertRepository.save(alert);

        // 5. 触发AI诊断（如果是根因候选）
        if (noiseResult.isRootCauseCandidate() && !"suppressed".equals(alert.getStatus())) {
            alert.setAiStatus("in_progress");
            alertRepository.updateAiStatus(alert.getAlertId(), "in_progress");

            // 异步派发AI任务
            aiDispatcher.dispatch(incident.getId(), alert);
        }

        // 6. 发布事件
        eventPublisher.publishEvent(new AlertReceivedEvent(this, alert));

        return AlertModel.fromEntity(alert);
    }

    private IncidentModel findOrCreateIncident(Alert alert, String clusterKey) {
        // 最近30分钟内，同一聚类key的活跃incident
        Optional<Incident> existing = incidentRepository
            .findActiveByClusterKey(alert.getTenantId(), clusterKey, Instant.now().minusSeconds(1800));

        if (existing.isPresent()) {
            return IncidentModel.fromEntity(existing.get());
        }

        // 创建新Incident
        Incident incident = Incident.builder()
            .id(generateId())
            .tenantId(alert.getTenantId())
            .clusterKey(clusterKey)
            .serviceId(alert.getServiceId())
            .status("analyzing")
            .createdAt(Instant.now())
            .build();

        incidentRepository.save(incident);
        return IncidentModel.fromEntity(incident);
    }
}
```

### 2.3 NoiseReducerService - 告警降噪

```java
package com.aiops.service.alert;

import com.aiops.repository.NoiseRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoiseReducerService {

    private final StringRedisTemplate redisTemplate;
    private final NoiseRuleRepository ruleRepository;

    /**
     * 降噪处理流程
     */
    public NoiseReductionResult process(Alert alert) {

        String tenantId = alert.getTenantId();
        String alertId = alert.getAlertId();

        // 1. 时间窗口去重（5分钟内相似告警抑制）
        String fingerprint = generateFingerprint(alert);
        String cacheKey = String.format("aiops:%s:alert:fingerprint:%s", tenantId, fingerprint);

        Boolean isNew = redisTemplate.opsForValue()
            .setIfAbsent(cacheKey, alertId, Duration.ofMinutes(5));

        if (!isNew) {
            String existingId = redisTemplate.opsForValue().get(cacheKey);
            return NoiseReductionResult.suppressed("TIME_WINDOW_DEDUP", existingId);
        }

        // 2. 规则匹配抑制
        List<NoiseRule> rules = ruleRepository.findEnabledByTenant(tenantId);
        for (NoiseRule rule : rules) {
            if (matchesRule(alert, rule)) {
                return NoiseReductionResult.suppressed(rule.getId(), null);
            }
        }

        // 3. 根源识别（使用聚类算法）
        String clusterKey = determineClusterKey(alert);
        boolean isRootCause = isRootCauseCandidate(alert, clusterKey);

        return NoiseReductionResult.accepted(clusterKey, isRootCause);
    }

    private String generateFingerprint(Alert alert) {
        // 服务 + 告警名 + 实例 + 5分钟窗口
        long timeWindow = alert.getStartsAt().getEpochSecond() / 300;
        return String.format("%s|%s|%s|%d",
            alert.getServiceId(),
            alert.getTitle(),
            alert.getLabels().getOrDefault("instance", ""),
            timeWindow
        );
    }

    private boolean matchesRule(Alert alert, NoiseRule rule) {
        // 规则匹配逻辑
        if (rule.getMatchServices() != null && !rule.getMatchServices().contains(alert.getServiceId())) {
            return false;
        }
        // TODO: 更复杂的规则匹配
        return true;
    }

    private String determineClusterKey(Alert alert) {
        // 基于服务依赖关系聚类
        return alert.getServiceId() + "|" + alert.getTitle().hashCode();
    }

    private boolean isRootCauseCandidate(Alert alert, String clusterKey) {
        // 根据拓扑位置和告警特征判断是否为主题告警
        // TODO: 实现智能根因识别
        return true;
    }
}
```

### 2.4 AITaskDispatcher - AI任务派发

```java
package com.aiops.service.ai;

import com.aiops.domain.model.AITaskContext;
import com.aiops.domain.model.IncidentModel;
import com.aiops.repository.entity.Alert;
import com.aiops.service.topology.TopologyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AITaskDispatcher {

    private final TopologyService topologyService;
    private final CMDBClient cmdbClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 封装AI诊断任务并派发
     */
    public void dispatch(String incidentId, Alert alert) {

        // 1. 构建上下文
        AITaskContext context = buildAITaskContext(alert);

        // 2. 准备任务
        AITaskMessage task = AITaskMessage.builder()
            .incidentId(incidentId)
            .tenantId(alert.getTenantId())
            .alertIds(List.of(alert.getAlertId()))
            .context(context)
            .options(Map.of(
                "stream", true,
                "max_rounds", 5,
                "confidence_threshold", 0.7
            ))
            .timestamp(System.currentTimeMillis())
            .build();

        // 3. 发送到Kafka AI任务队列
        String topic = "aiops.ai-tasks";
        kafkaTemplate.send(topic, alert.getTenantId(), task)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("AI task dispatched for incident: {}", incidentId);
                } else {
                    log.error("Failed to dispatch AI task: {}", incidentId, ex);
                }
            });
    }

    private AITaskContext buildAITaskContext(Alert alert) {
        String serviceId = alert.getServiceId();
        String instance = alert.getLabels().get("instance");

        return AITaskContext.builder()
            .serviceId(serviceId)
            .instance(instance)
            .timeRange(Map.of(
                "start", alert.getStartsAt().minusSeconds(1800).toString(),  // 30分钟前
                "end", alert.getStartsAt().plusSeconds(300).toString()      // 5分钟后
            ))
            .topology(topologyService.getTopology(serviceId, 2, "both", true))
            .cmdb(instance != null ? cmdbClient.getInstanceByIp(instance) : null)
            .build();
    }
}
```

### 2.5 DashboardController - 大屏接口

```java
package com.aiops.controller;

import com.aiops.dto.ApiResponse;
import com.aiops.dto.dashboard.DashboardSummary;
import com.aiops.dto.dashboard.TopologyData;
import com.aiops.service.dashboard.DashboardDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardDataService dashboardService;
    private final ReasoningStreamService streamService;

    /**
     * 大屏摘要数据
     */
    @GetMapping("/summary")
    public ApiResponse<DashboardSummary> getSummary(@RequestParam String tenantId) {
        return ApiResponse.success(dashboardService.getSummary(tenantId));
    }

    /**
     * 拓扑图数据
     */
    @GetMapping("/topology")
    public ApiResponse<TopologyData> getTopology(
            @RequestParam String tenantId,
            @RequestParam(required = false) String serviceId,
            @RequestParam(defaultValue = "2") int depth) {
        return ApiResponse.success(dashboardService.getTopology(tenantId, serviceId, depth));
    }

    /**
     * 实时AI诊断流 (SSE)
     */
    @GetMapping(value = "/ai-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAIReasoning(@RequestParam String tenantId) {
        SseEmitter emitter = new SseEmitter(0L); // 永不超时

        streamService.subscribe(tenantId, event -> {
            try {
                emitter.send(SseEmitter.event()
                    .name(event.getType())
                    .data(event.getData()));
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> streamService.unsubscribe(tenantId));
        emitter.onTimeout(() -> streamService.unsubscribe(tenantId));

        return emitter;
    }
}
```

### 2.6 SecurityConfig - 安全配置

```java
package com.aiops.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeHttpRequests(auth -> auth
                // 健康检查公开
                .requestMatchers("/actuator/health").permitAll()
                // 告警Webhook使用API Key
                .requestMatchers("/api/v1/alerts/webhook").permitAll()
                // 内部接口仅限内部
                .requestMatchers("/internal/**").hasRole("INTERNAL")
                // 其他需要认证
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

---

## 3. 配置文件

### 3.1 application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: aiops-control-plane

  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:3306/aiops_control?useUnicode=true&characterEncoding=utf8
    username: ${MYSQL_USER:aiops}
    password: ${MYSQL_PASSWORD:xxx}
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

  redis:
    cluster:
      nodes: ${REDIS_NODES:localhost:6379}
    password: ${REDIS_PASSWORD:}
    lettuce:
      pool:
        max-active: 50

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    producer:
      acks: all
      retries: 3
    consumer:
      group-id: aiops-control
      auto-offset-reset: earliest

# Doris配置
doris:
  jdbc-url: jdbc:mysql://${DORIS_FE:localhost}:9030/aiops
  username: ${DORIS_USER:aiops}
  password: ${DORIS_PASSWORD:xxx}
  pool:
    max-total: 20

# AI引擎配置
ai-engine:
  url: ${AI_ENGINE_URL:http://localhost:8000}
  timeout: 60000
  api-key: ${AI_ENGINE_KEY:xxx}

# CMDB配置
cmdb:
  url: ${CMDB_URL:http://cmdb.internal/api}
  timeout: 5000

tenant:
  isolation:
    enabled: true
    enforce-sql-filter: true

logging:
  level:
    com.aiops: INFO
    org.springframework.kafka: WARN
```

---

## 4. 依赖 (pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.3</version>
    </parent>

    <groupId>com.aiops</groupId>
    <artifactId>control-plane</artifactId>
    <version>1.0.0</version>

    <dependencies>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>

        <!-- MyBatis (for complex Doris queries) -->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>3.0.3</version>
        </dependency>

        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt</artifactId>
            <version>0.12.3</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

---

*本文档定义了Java控制面的开发规范，开发者应以此为准。*
