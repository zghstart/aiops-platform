# AIOps 智能运维平台 - 测试规范文档

## 1. 测试策略概览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           测试金字塔                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                         ┌─────────────┐                                     │
│                         │   E2E测试    │  ← 5%  核心业务流验证               │
│                         │  (Playwright)│                                     │
│                        ┌┴─────────────┴┐                                    │
│                        │  集成测试      │  ← 15% 组件交互验证                │
│                        │(TestContainers)│                                    │
│                       ┌┴───────────────┴┐                                   │
│                       │    单元测试      │  ← 80% 逻辑 correctness           │
│                       │ (JUnit5/pytest) │                                   │
│                       └─────────────────┘                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 单元测试规范

### 2.1 Java 控制面单元测试

#### 测试框架与依赖

```xml
<!-- pom.xml -->
<dependencies>
    <!-- JUnit 5 -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Mockito -->
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-junit-jupiter</artifactId>
        <version>5.8.0</version>
        <scope>test</scope>
    </dependency>

    <!-- AssertJ -->
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <version>3.24.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

#### 测试目录结构

```
src/test/java/com/aiops/
├── unit/                              # 纯单元测试
│   ├── service/
│   │   ├── AlertReceiverServiceTest.java
│   │   ├── NoiseReducerServiceTest.java
│   │   └── AITaskDispatcherTest.java
│   ├── util/
│   │   ├── LogParserUtilTest.java
│   │   └── TraceIdGeneratorTest.java
│   └── security/
│       └── JwtTokenProviderTest.java
├── integration/                       # 集成测试（需要 Spring 上下文）
│   ├── repository/
│   │   ├── AlertRepositoryTest.java
│   │   └── TenantRepositoryTest.java
│   └── controller/
│       ├── AlertControllerTest.java
│       └── DashboardControllerTest.java
└── fixtures/                          # 测试数据工厂
    ├── AlertFixture.java
    ├── TenantFixture.java
    └── LogEntryFixture.java
```

#### 核心服务测试示例

```java
// AlertReceiverServiceTest.java
@ExtendWith(MockitoExtension.class)
class AlertReceiverServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private NoiseReducerService noiseReducerService;

    @Mock
    private KafkaTemplate<String, AlertEvent> kafkaTemplate;

    @InjectMocks
    private AlertReceiverService alertReceiverService;

    @Test
    @DisplayName("接收告警-正常场景-应保存告警并发送事件")
    void receiveAlert_NormalScenario_ShouldSaveAndPublish() {
        // Given
        AlertDTO alert = AlertFixture.createValidAlert();
        when(noiseReducerService.shouldSuppress(any())).thenReturn(false);
        when(alertRepository.save(any())).thenReturn(alert);

        // When
        AlertDTO result = alertReceiverService.receiveAlert(alert);

        // Then
        assertThat(result.getStatus()).isEqualTo(AlertStatus.PENDING);
        verify(alertRepository).save(argThat(saved ->
            saved.getReceivedAt() != null
        ));
        verify(kafkaTemplate).send(eq("alerts.raw"), any(AlertEvent.class));
    }

    @Test
    @DisplayName("接收告警-被降噪-不应发送事件")
    void receiveAlert_SuppressedByNoiseRule_ShouldNotPublish() {
        // Given
        AlertDTO alert = AlertFixture.createSuppressedAlert();
        when(noiseReducerService.shouldSuppress(any())).thenReturn(true);

        // When
        AlertDTO result = alertReceiverService.receiveAlert(alert);

        // Then
        assertThat(result.getStatus()).isEqualTo(AlertStatus.SUPPRESSED);
        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    @DisplayName("高并发接收-应正确处理并发请求")
    void receiveAlert_HighConcurrency_ShouldHandleSafely() throws InterruptedException {
        // Given
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<AlertDTO> alerts = IntStream.range(0, threadCount)
            .mapToObj(i -> AlertFixture.createAlertWithName("alert-" + i))
            .toList();

        // When
        ExecutorService executor = Executors.newFixedThreadPool(20);
        alerts.forEach(alert -> executor.submit(() -> {
            try {
                alertReceiverService.receiveAlert(alert);
            } finally {
                latch.countDown();
            }
        }));

        // Then
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        verify(alertRepository, times(threadCount)).save(any());
    }
}
```

#### 降噪规则引擎测试

```java
// NoiseReducerServiceTest.java
class NoiseReducerServiceTest {

    @Test
    @DisplayName("时间窗口降噪-5分钟内相同告警应被抑制")
    void shouldSuppress_SameAlertWithin5Minutes_ReturnsTrue() {
        // Given
        String alertKey = "service-db-connect-fail";
        NoiseRule timeWindowRule = NoiseRuleFixture.createTimeWindowRule(Duration.ofMinutes(5));

        // 第一次接收
        AlertDTO firstAlert = createAlert(alertKey, Instant.now().minusSeconds(60));
        noiseReducerService.recordAlert(firstAlert);

        // When - 2分钟后再次接收相同告警
        AlertDTO secondAlert = createAlert(alertKey, Instant.now());
        boolean shouldSuppress = noiseReducerService.shouldSuppress(secondAlert);

        // Then
        assertThat(shouldSuppress).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "INFO, 100, true",    // 低级别且频率高，应抑制
        "CRITICAL, 100, false", // 高级别不应抑制
        "WARNING, 10, false"   // 频率不高，不应抑制
    })
    @DisplayName("频率降噪-根据级别和频率决策")
    void shouldSuppress_ByFrequency(String level, int countPerHour, boolean expected) {
        // Given
        AlertDTO alert = AlertFixture.createAlertWithLevel(Level.valueOf(level));
        when(metricsService.getAlertFrequency(any(), any())).thenReturn(countPerHour);

        // When
        boolean result = noiseReducerService.shouldSuppress(alert);

        // Then
        assertThat(result).isEqualTo(expected);
    }
}
```

### 2.2 Python AI 引擎单元测试

#### 测试框架配置

```python
# pyproject.toml
[tool.pytest.ini_options]
testpaths = ["tests"]
python_files = ["test_*.py", "*_test.py"]
python_classes = ["Test*"]
python_functions = ["test_*"]
addopts = "-v --tb=short --cov=app --cov-report=term-missing --cov-report=html"
markers = [
    "slow: marks tests as slow (deselect with '-m \"not slow\"')",
    "integration: marks tests as integration tests",
    "unit: marks tests as unit tests",
]
```

#### 测试目录结构

```
aiops-ai-engine/tests/
├── unit/
│   ├── test_react_orchestrator.py
│   ├── test_glm5_adapter.py
│   ├── test_tool_registry.py
│   ├── test_log_analyzer.py
│   └── test_stream_handler.py
├── integration/
│   ├── test_vllm_connection.py
│   └── test_doris_query.py
├── e2e/
│   └── test_full_analysis_flow.py
├── fixtures/
│   ├── sample_alerts.py
│   ├── sample_logs.py
│   └── mock_responses.py
└── conftest.py
```

#### ReAct 编排器测试

```python
# tests/unit/test_react_orchestrator.py
import pytest
from unittest.mock import Mock, AsyncMock, patch
from app.core.orchestrator import ReActOrchestrator
from app.core.types import StreamEvent, AnalysisResult


class TestReActOrchestrator:

    @pytest.fixture
    def orchestrator(self):
        return ReActOrchestrator()

    @pytest.fixture
    def mock_llm(self):
        return AsyncMock()

    @pytest.mark.asyncio
    async def test_single_step_resolution(self, orchestrator, mock_llm):
        """测试单步即可解决的简单场景"""
        # Given
        alert = {
            "title": "CPU 使用率过高",
            "service": "order-service",
            "metric_value": 95.5
        }
        mock_llm.generate.return_value = {
            "thought": "根据告警信息，CPU 使用率 95.5% 已经严重超标...",
            "action": "final_answer",
            "action_input": {"root_cause": " Pod 资源限制过低", "confidence": 0.92}
        }

        # When
        events = []
        async for event in orchestrator.analyze(alert, llm=mock_llm):
            events.append(event)

        # Then
        assert len(events) == 3  # thought + final_answer + complete
        assert events[0].type == "thought"
        assert events[1].type == "final_answer"
        assert events[2].type == "complete"
        assert events[1].data["confidence"] > 0.9

    @pytest.mark.asyncio
    async def test_multi_step_analysis(self, orchestrator, mock_llm):
        """测试需要多步工具调用的复杂场景"""
        # Given
        alert = {"title": "服务响应慢", "service": "payment-service"}

        # 模拟两步推理：先查日志，再查指标
        responses = [
            {
                "thought": "需要查看相关服务日志来了解问题",
                "action": "search_logs",
                "action_input": {"service": "payment-service", "level": "ERROR"}
            },
            {
                "thought": "日志中发现大量连接超时，需要查看连接池指标",
                "action": "query_metrics",
                "action_input": {"metric": "db_connection_pool_active"}
            },
            {
                "thought": "连接池已耗尽，确定根因为连接池配置不当",
                "action": "final_answer",
                "action_input": {
                    "root_cause": "数据库连接池配置过小",
                    "confidence": 0.88
                }
            }
        ]
        mock_llm.generate.side_effect = responses

        # Mock 工具执行
        with patch.object(orchestrator.tool_executor, 'execute') as mock_exec:
            mock_exec.side_effect = [
                {"logs": ["Connection timeout", "Pool exhausted"]},
                {"value": 100, "max": 100}
            ]

            # When
            events = []
            async for event in orchestrator.analyze(alert, llm=mock_llm):
                events.append(event)

            # Then
            tool_calls = [e for e in events if e.type == "tool_call"]
            assert len(tool_calls) == 2
            assert mock_exec.call_count == 2

    @pytest.mark.asyncio
    async def test_max_iterations_protection(self, orchestrator, mock_llm):
        """测试最大迭代次数保护，防止无限循环"""
        # Given - LLM 一直不给出 final_answer
        mock_llm.generate.return_value = {
            "thought": "继续思考...",
            "action": "search_logs",
            "action_input": {}
        }

        orchestrator.max_iterations = 3

        # When / Then
        with pytest.raises(AnalysisTimeoutError) as exc_info:
            async for _ in orchestrator.analyze({"title": "test"}, llm=mock_llm):
                pass

        assert "超过最大迭代次数" in str(exc_info.value)


class TestToolRegistry:

    @pytest.fixture
    def registry(self):
        from app.core.tools import ToolRegistry
        return ToolRegistry()

    def test_tool_registration(self, registry):
        """测试工具注册"""
        @registry.register(
            name="test_tool",
            description="用于测试的工具",
            parameters={"query": {"type": "string"}}
        )
        def test_tool(query: str) -> dict:
            return {"result": query}

        assert "test_tool" in registry.list_tools()
        assert registry.get_tool("test_tool") is not None

    def test_tool_parameter_validation(self, registry):
        """测试工具参数校验"""
        @registry.register(
            name="validate_test",
            parameters={
                "count": {"type": "integer", "minimum": 0},
                "name": {"type": "string", "minLength": 1}
            }
        )
        def validate_test(count: int, name: str):
            pass

        tool = registry.get_tool("validate_test")

        # 合法参数
        assert tool.validate_params({"count": 10, "name": "test"}) is True

        # 非法参数 - 负数
        with pytest.raises(ValidationError):
            tool.validate_params({"count": -1, "name": "test"})

        # 缺少必填参数
        with pytest.raises(ValidationError):
            tool.validate_params({"count": 10})
```

---

## 3. 集成测试规范

### 3.1 TestContainers 配置

```java
// BaseIntegrationTest.java
@SpringBootTest
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("aiops_test")
        .withUsername("test")
        .withPassword("test")
        .withInitScript("db/init_mysql_test.sql");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}

// AlertProcessingIntegrationTest.java
class AlertProcessingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private KafkaTemplate<String, AlertEvent> kafkaTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void fullAlertProcessingFlow() {
        // 1. 发送告警到 Kafka
        AlertEvent event = AlertFixture.createKafkaEvent();
        kafkaTemplate.send("alerts.raw", event).get();

        // 2. 等待处理完成
        await()
            .atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> {
                Optional<Alert> saved = alertRepository.findByTraceId(event.getTraceId());
                assertThat(saved).isPresent();
                assertThat(saved.get().getStatus()).isIn(
                    AlertStatus.PENDING,
                    AlertStatus.ANALYZING
                );
            });

        // 3. 查询告警详情 API
        ResponseEntity<AlertDTO> response = restTemplate.getForEntity(
            "/api/alerts/{id}",
            AlertDTO.class,
            event.getAlertId()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTraceId()).isEqualTo(event.getTraceId());
    }
}
```

### 3.2 Python 集成测试

```python
# tests/integration/test_ai_pipeline.py
import pytest
import asyncio
from httpx import AsyncClient
from testcontainers.mysql import MySqlContainer
from testcontainers.redis import RedisContainer


@pytest.fixture(scope="module")
def mysql_container():
    with MySqlContainer("mysql:8.0") as mysql:
        yield mysql


@pytest.fixture(scope="module")
def redis_container():
    with RedisContainer("redis:7-alpine") as redis:
        yield redis


@pytest.fixture
async def test_client(mysql_container, redis_container):
    # 配置测试环境
    import app.config as config
    config.DATABASE_URL = mysql_container.get_connection_url()
    config.REDIS_URL = f"redis://{redis_container.get_container_host_ip()}:{redis_container.get_exposed_port(6379)}"

    from app.main import app
    async with AsyncClient(app=app, base_url="http://test") as client:
        yield client


@pytest.mark.asyncio
class TestAIPipeline:

    async def test_end_to_end_analysis(self, test_client):
        """端到端 AI 分析流程测试"""
        # Given - 提交告警分析任务
        alert_payload = {
            "alert_id": "test-alert-001",
            "title": "MySQL 连接超时",
            "service": "order-service",
            "severity": "critical",
            "timestamp": "2024-01-15T10:30:00Z"
        }

        # When - 提交分析任务
        submit_response = await test_client.post(
            "/api/ai/analyze",
            json=alert_payload
        )
        assert submit_response.status_code == 202
        task_id = submit_response.json()["task_id"]

        # Then - 轮询等待结果
        for _ in range(30):  # 最多等待 30 秒
            await asyncio.sleep(1)
            status_response = await test_client.get(f"/api/ai/tasks/{task_id}")
            status = status_response.json()

            if status["state"] == "completed":
                result = status["result"]
                assert "root_cause" in result
                assert "confidence" in result
                assert "recommended_actions" in result
                assert result["confidence"] > 0.6  # 最低可信度要求
                break
            elif status["state"] == "failed":
                pytest.fail(f"Task failed: {status.get('error')}")
        else:
            pytest.fail("Analysis task timeout")


@pytest.mark.slow
@pytest.mark.asyncio
class TestVLLMIntegration:
    """vLLM 模型服务集成测试 - 标记为慢测试"""

    async def test_glm5_tool_calling(self):
        """测试 GLM5 工具调用能力"""
        from app.adapters.glm5 import GLM5Adapter

        adapter = GLM5Adapter(base_url="http://localhost:8000/v1")

        messages = [
            {"role": "system", "content": "你是一个运维助手，可以使用工具查询信息"},
            {"role": "user", "content": "查询 order-service 最近 1 小时的错误日志"}
        ]

        tools = [{
            "type": "function",
            "function": {
                "name": "search_logs",
                "description": "搜索日志",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "service": {"type": "string"},
                        "duration": {"type": "string"}
                    }
                }
            }
        }]

        response = await adapter.chat_completion(
            messages=messages,
            tools=tools,
            temperature=0.0
        )

        # 验证响应包含工具调用
        assert "choices" in response
        message = response["choices"][0]["message"]

        # GLM5 应该识别出需要调用工具
        if "tool_calls" in message:
            tool_call = message["tool_calls"][0]
            assert tool_call["function"]["name"] == "search_logs"
            args = json.loads(tool_call["function"]["arguments"])
            assert args["service"] == "order-service"
```

---

## 4. E2E 测试规范

### 4.1 Playwright 测试配置

```javascript
// playwright.config.ts
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ['html'],
    ['junit', { outputFile: 'test-results/e2e-junit.xml' }]
  ],
  use: {
    baseURL: process.env.TEST_BASE_URL || 'http://localhost:3000',
    trace: 'on-first-retry',
    video: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
    // 4K 大屏测试配置
    {
      name: '4k-display',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 3840, height: 2160 },
        deviceScaleFactor: 1,
      },
    },
  ],
  webServer: {
    command: 'npm run dev:test',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
  },
});
```

### 4.2 核心业务流程 E2E 测试

```typescript
// e2e/alert-handling.spec.ts
import { test, expect } from '@playwright/test';
import { AlertHelper } from './helpers/alert-helper';
import { DashboardPage } from './pages/dashboard-page';

test.describe('告警处理全流程', () => {
  let dashboard: DashboardPage;
  let alertHelper: AlertHelper;

  test.beforeEach(async ({ page }) => {
    dashboard = new DashboardPage(page);
    alertHelper = new AlertHelper();
    await dashboard.goto();
    await dashboard.login('test-ops', 'test-password');
  });

  test('告警接收 -> AI分析 -> 根因展示 完整流程', async ({ page }) => {
    // 1. 模拟发送告警
    const alertData = await alertHelper.sendWebhookAlert({
      title: '数据库连接池耗尽',
      service: 'payment-service',
      severity: 'critical',
      metric: { name: 'db_pool_active', value: 100, threshold: 80 }
    });

    // 2. 等待告警卡片出现在大屏上
    await expect(
      page.locator(`[data-alert-id="${alertData.alertId}"]`)
    ).toBeVisible({ timeout: 5000 });

    // 3. 点击告警进入详情
    await page.click(`[data-alert-id="${alertData.alertId}"]`);

    // 4. 触发 AI 分析
    await page.click('[data-testid="ai-analysis-btn"]');

    // 5. 验证分析过程可视化
    await expect(
      page.locator('[data-testid="thinking-process"]')
    ).toBeVisible();

    // 等待打字机效果完成
    await page.waitForFunction(() => {
      const el = document.querySelector('[data-testid="thinking-text"]');
      return el?.textContent?.includes('分析完成');
    }, { timeout: 30000 });

    // 6. 验证分析结果展示
    const rootCause = page.locator('[data-testid="root-cause"]').textContent();
    await expect(rootCause).toContain('数据库');

    // 7. 验证推荐措施
    const actions = page.locator('[data-testid="recommended-actions"] li');
    await expect(actions).toHaveCount({ gte: 1 });

    // 8. 生成报告
    await page.click('[data-testid="generate-report-btn"]');
    await expect(page.locator('[data-testid="report-modal"]')).toBeVisible();
  });

  test('大屏多租户数据隔离', async ({ browser }) => {
    // 租户 A 的上下文
    const contextA = await browser.newContext();
    const pageA = await contextA.newPage();
    const dashboardA = new DashboardPage(pageA);

    // 租户 B 的上下文
    const contextB = await browser.newContext();
    const pageB = await contextB.newPage();
    const dashboardB = new DashboardPage(pageB);

    // 分别登录不同租户
    await dashboardA.goto();
    await dashboardA.login('tenant-a-ops', 'password');

    await dashboardB.goto();
    await dashboardB.login('tenant-b-ops', 'password');

    // 发送仅属于租户 A 的告警
    const alertA = await alertHelper.sendWebhookAlert(
      { title: '租户A告警', service: 'svc-a' },
      { tenantId: 'tenant-a', apiKey: 'key-a' }
    );

    // 发送仅属于租户 B 的告警
    const alertB = await alertHelper.sendWebhookAlert(
      { title: '租户B告警', service: 'svc-b' },
      { tenantId: 'tenant-b', apiKey: 'key-b' }
    );

    // 验证隔离 - 租户 A 看不到租户 B 的告警
    await expect(
      pageA.locator(`[data-alert-id="${alertB.alertId}"]`)
    ).not.toBeVisible();

    // 租户 B 看不到租户 A 的告警
    await expect(
      pageB.locator(`[data-alert-id="${alertA.alertId}"]`)
    ).not.toBeVisible();

    await contextA.close();
    await contextB.close();
  });
});

// e2e/pages/dashboard-page.ts
export class DashboardPage {
  constructor(private page: Page) {}

  async goto() {
    await this.page.goto('/dashboard');
  }

  async login(username: string, password: string) {
    await this.page.fill('[data-testid="username"]', username);
    await this.page.fill('[data-testid="password"]', password);
    await this.page.click('[data-testid="login-btn"]');

    // 等待大屏加载完成
    await this.page.waitForSelector('[data-testid="aiops-dashboard"]', {
      state: 'visible',
      timeout: 10000
    });
  }

  async getTopologyData() {
    return this.page.evaluate(() => {
      const canvas = document.querySelector('.g6-canvas');
      // @ts-ignore
      return canvas?.__g6_graph?.save();
    });
  }

  async waitForSSEConnection() {
    await this.page.waitForEvent('console', msg =>
      msg.text().includes('SSE connected')
    );
  }
}
```

---

## 5. AI 推理准确性测试

### 5.1 基准测试集

```python
# tests/accuracy/test_analysis_accuracy.py
import json
import pytest
from dataclasses import dataclass
from typing import List
from app.core.orchestrator import ReActOrchestrator


@dataclass
class TestCase:
    """AI 分析准确性测试用例"""
    name: str
    alert: dict
    expected_root_cause: str
    expected_actions: List[str]
    min_confidence: float = 0.75


# 基准测试数据集
BENCHMARK_CASES = [
    TestCase(
        name="数据库连接池耗尽",
        alert={
            "title": "数据库连接超时",
            "service": "order-service",
            "symptoms": ["连接超时", "响应慢"],
            "metrics": {"db_pool_wait_count": 150, "db_pool_active": 20}
        },
        expected_root_cause="数据库连接池配置过小",
        expected_actions=["增加连接池大小", "检查连接泄漏", "启用连接池监控"],
        min_confidence=0.8
    ),
    TestCase(
        name="JVM OOM",
        alert={
            "title": "Java Heap Space OOM",
            "service": "inventory-service",
            "symptoms": ["OutOfMemoryError", "GC频繁"],
            "logs": ["java.lang.OutOfMemoryError: Java heap space"]
        },
        expected_root_cause="堆内存不足",
        expected_actions=["增加堆内存", "分析内存泄漏", "优化大对象处理"],
        min_confidence=0.85
    ),
    TestCase(
        name="磁盘空间不足",
        alert={
            "title": "磁盘使用率超过 90%",
            "service": "log-collector",
            "metrics": {"disk_usage_percent": 95, "path": "/var/log"}
        },
        expected_root_cause="日志文件占用过多磁盘空间",
        expected_actions=["清理旧日志", "配置日志轮转", "扩容磁盘"],
        min_confidence=0.75
    ),
    TestCase(
        name="K8s Pod 频繁重启",
        alert={
            "title": "Pod restart count high",
            "service": "payment-gateway",
            "kubernetes": {
                "namespace": "production",
                "restarts": 15,
                "exit_code": 137
            }
        },
        expected_root_cause="内存限制导致 OOMKilled",
        expected_actions=["增加内存 limit", "检查内存泄漏", "优化资源请求"],
        min_confidence=0.8
    ),
    TestCase(
        name="网络分区",
        alert={
            "title": "服务间调用失败",
            "service": "api-gateway",
            "symptoms": ["连接超时", "部分下游服务不可达"],
            "network": {"packet_loss": 0.15, "latency_ms": 2000}
        },
        expected_root_cause="网络分区或网络质量下降",
        expected_actions=["检查网络配置", "启用熔断", "增加超时配置"],
        min_confidence=0.7
    ),
]


@pytest.mark.slow
@pytest.mark.asyncio
class TestAnalysisAccuracy:
    """AI 根因分析准确性测试"""

    @pytest.fixture(scope="class")
    async def orchestrator(self):
        return ReActOrchestrator()

    @pytest.mark.parametrize("test_case", BENCHMARK_CASES, ids=lambda c: c.name)
    async def test_root_cause_accuracy(self, orchestrator, test_case):
        """测试根因识别准确性"""
        result = await orchestrator.analyze_single(test_case.alert)

        # 验证置信度
        assert result["confidence"] >= test_case.min_confidence, \
            f"置信度 {result['confidence']} 低于阈值 {test_case.min_confidence}"

        # 验证根因包含预期关键词（语义匹配）
        root_cause = result["root_cause"].lower()
        expected_keywords = test_case.expected_root_cause.lower().split()
        keyword_match = any(kw in root_cause for kw in expected_keywords)

        assert keyword_match, \
            f"根因 \"{result['root_cause']}\" 未包含预期关键词 {expected_keywords}"

    async def test_action_completeness(self, orchestrator):
        """测试推荐措施的完整性"""
        test_case = BENCHMARK_CASES[0]  # 使用连接池案例
        result = await orchestrator.analyze_single(test_case.alert)

        actions = [a.lower() for a in result.get("recommended_actions", [])]

        # 至少有一项预期措施被提及
        matched = sum(
            1 for expected in test_case.expected_actions
            if any(expected.lower() in act for act in actions)
        )

        match_rate = matched / len(test_case.expected_actions)
        assert match_rate >= 0.5, \
            f"措施匹配率 {match_rate} 过低，实际推荐: {actions}"

    async def test_false_positive_rate(self, orchestrator):
        """测试误报率 - 正常情况不应产生告警"""
        normal_scenarios = [
            {"title": "CPU 使用率 50%", "service": "normal-svc", "metrics": {"cpu": 50}},
            {"title": "内存使用正常", "service": "normal-svc", "metrics": {"memory": 60}},
        ]

        false_positives = 0
        for scenario in normal_scenarios:
            result = await orchestrator.analyze_single(scenario)
            if result.get("severity") in ["critical", "high"]:
                false_positives += 1

        fp_rate = false_positives / len(normal_scenarios)
        assert fp_rate <= 0.1, f"误报率 {fp_rate} 过高"


# 准确率报告
class AccuracyReporter:
    """生成准确性测试报告"""

    def generate_report(self, results: List[dict]) -> dict:
        total = len(results)
        passed = sum(1 for r in results if r["passed"])

        return {
            "summary": {
                "total_cases": total,
                "passed": passed,
                "failed": total - passed,
                "accuracy": passed / total,
                "timestamp": datetime.now().isoformat()
            },
            "by_category": self._group_by_category(results),
            "recommendations": self._generate_recommendations(results)
        }
```

---

## 6. 性能测试规范

### 6.1 API 性能基准

```python
# tests/performance/test_api_performance.py
import asyncio
import time
import statistics
from typing import List
import httpx
import pytest


class TestAPIPerformance:
    """API 性能测试"""

    BASE_URL = "http://localhost:8080"

    async def _measure_latency(self, client: httpx.AsyncClient, endpoint: str, payload: dict = None) -> float:
        """测量单次请求延迟"""
        start = time.perf_counter()
        if payload:
            await client.post(endpoint, json=payload)
        else:
            await client.get(endpoint)
        return (time.perf_counter() - start) * 1000  # ms

    @pytest.mark.asyncio
    async def test_alert_submission_latency(self):
        """告警提交接口 P99 延迟 < 100ms"""
        async with httpx.AsyncClient(base_url=self.BASE_URL) as client:
            latencies: List[float] = []

            for i in range(100):
                latency = await self._measure_latency(
                    client,
                    "/api/alerts/webhook",
                    {
                        "alert_id": f"perf-test-{i}",
                        "title": "性能测试告警",
                        "service": "test-service"
                    }
                )
                latencies.append(latency)

            p99 = statistics.quantiles(latencies, n=100)[98]  # P99
            p95 = statistics.quantiles(latencies, n=100)[93]  # P95
            avg = statistics.mean(latencies)

            print(f"Alert submission - Avg: {avg:.2f}ms, P95: {p95:.2f}ms, P99: {p99:.2f}ms")

            assert p99 < 100, f"P99 延迟 {p99:.2f}ms 超过 100ms 阈值"
            assert p95 < 50, f"P95 延迟 {p95:.2f}ms 超过 50ms 阈值"

    @pytest.mark.asyncio
    async def test_dashboard_query_performance(self):
        """大屏数据查询 P99 < 200ms"""
        async with httpx.AsyncClient(base_url=self.BASE_URL) as client:
            endpoints = [
                "/api/dashboard/stats",
                "/api/dashboard/active-alerts",
                "/api/dashboard/topology",
            ]

            for endpoint in endpoints:
                latencies = []
                for _ in range(50):
                    latency = await self._measure_latency(client, endpoint)
                    latencies.append(latency)

                p99 = statistics.quantiles(latencies, n=100)[98]
                print(f"{endpoint} - P99: {p99:.2f}ms")
                assert p99 < 200, f"{endpoint} P99 {p99:.2f}ms 超过阈值"


# Locust 负载测试配置
# tests/performance/locustfile.py
from locust import HttpUser, task, between
import random


class AIOpsUser(HttpUser):
    """模拟运维人员操作"""
    wait_time = between(1, 5)

    def on_start(self):
        """登录获取 token"""
        response = self.client.post("/api/auth/login", json={
            "username": f"ops-{self.user_id}",
            "password": "test-password"
        })
        self.token = response.json()["access_token"]
        self.headers = {"Authorization": f"Bearer {self.token}"}

    @task(10)
    def view_dashboard(self):
        """查看大屏"""
        self.client.get("/api/dashboard/stats", headers=self.headers)

    @task(5)
    def view_active_alerts(self):
        """查看活跃告警"""
        self.client.get("/api/alerts?status=active&limit=20", headers=self.headers)

    @task(3)
    def search_cases(self):
        """搜索历史案例"""
        keywords = ["超时", "OOM", "连接池", "GC"]
        self.client.get(
            f"/api/cases/search?q={random.choice(keywords)}",
            headers=self.headers
        )

    @task(2)
    def trigger_ai_analysis(self):
        """触发 AI 分析"""
        self.client.post("/api/ai/analyze", json={
            "alert_id": f"load-test-{random.randint(1, 1000000)}",
            "title": random.choice([
                "CPU 使用率过高",
                "内存不足",
                "数据库连接超时",
                "服务响应慢"
            ]),
            "service": f"service-{random.randint(1, 50)}"
        }, headers=self.headers)


class WebhookSender(HttpUser):
    """模拟告警推送"""
    wait_time = between(0.1, 0.5)  # 高频告警写入

    @task
    def send_alert(self):
        """发送告警 webhook"""
        self.client.post("/api/alerts/webhook", json={
            "alert_id": f"webhook-{random.randint(1, 10000000)}",
            "source": random.choice(["prometheus", "zabbix", "elasticsearch"]),
            "severity": random.choice(["critical", "warning", "info"]),
            "title": "性能测试告警",
            "service": f"svc-{random.randint(1, 100)}"
        })
```

---

## 7. 测试执行与 CI/CD 集成

### 7.1 GitHub Actions 测试工作流

```yaml
# .github/workflows/test.yml
name: Test Suite

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  unit-test-java:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Cache Maven
        uses: actions/cache@v4
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}

      - name: Run Unit Tests
        run: mvn test -Dtest="*UnitTest,*Test" -DfailIfNoTests=false

      - name: Generate Coverage Report
        run: mvn jacoco:report

      - name: Upload Coverage
        uses: codecov/codecov-action@v3
        with:
          files: target/site/jacoco/jacoco.xml
          fail_ci_if_error: true

  unit-test-python:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Python
        uses: actions/setup-python@v5
        with:
          python-version: '3.11'

      - name: Install dependencies
        run: |
          pip install -r requirements.txt
          pip install pytest pytest-asyncio pytest-cov

      - name: Run Tests
        run: pytest tests/unit -v --cov=app --cov-report=xml

      - name: Upload Coverage
        uses: codecov/codecov-action@v3
        with:
          files: coverage.xml

  integration-test:
    runs-on: ubuntu-latest
    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: test
          MYSQL_DATABASE: aiops_test
      redis:
        image: redis:7-alpine
      kafka:
        image: confluentinc/cp-kafka:7.5.0
    steps:
      - uses: actions/checkout@v4

      - name: Setup JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run Integration Tests
        run: mvn test -Dtest="*IntegrationTest"
        env:
          SPRING_PROFILES_ACTIVE: test
          MYSQL_HOST: mysql
          REDIS_HOST: redis
          KAFKA_HOST: kafka

  e2e-test:
    runs-on: ubuntu-latest
    needs: [unit-test-java, unit-test-python]
    steps:
      - uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Install dependencies
        run: npm ci
        working-directory: aiops-dashboard

      - name: Install Playwright
        run: npx playwright install --with-deps
        working-directory: aiops-dashboard

      - name: Start services
        run: docker-compose -f docker-compose.test.yml up -d

      - name: Wait for services
        run: npx wait-on http://localhost:8080/actuator/health --timeout 120000

      - name: Run E2E Tests
        run: npx playwright test
        working-directory: aiops-dashboard

      - name: Upload Playwright Report
        uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: playwright-report
          path: aiops-dashboard/playwright-report/

  performance-test:
    runs-on: ubuntu-latest
    if: contains(github.event.pull_request.labels.*.name, 'performance')
    steps:
      - uses: actions/checkout@v4

      - name: Setup Locust
        run: pip install locust

      - name: Start services
        run: docker-compose -f docker-compose.test.yml up -d

      - name: Run Locust
        run: |
          locust -f tests/performance/locustfile.py \
            --headless \
            --users 100 \
            --spawn-rate 10 \
            --run-time 5m \
            --html=locust-report.html

      - name: Upload Report
        uses: actions/upload-artifact@v4
        with:
          name: performance-report
          path: locust-report.html
```

### 7.2 测试覆盖度要求

| 模块 | 单元测试覆盖率 | 集成测试 | E2E 覆盖 |
|------|--------------|----------|----------|
| Java 控制面 | ≥ 80% | 核心流程 | 告警全链路 |
| Python AI 引擎 | ≥ 75% | vLLM 连接 | AI 分析流程 |
| 前端大屏 | ≥ 70% | API Mock | 核心页面 |
| MCP 工具 | ≥ 85% | Doris/Redis | - |

---

*本文档定义了 AIOps 平台的测试策略、用例规范和 CI/CD 集成方案，确保代码质量和系统稳定性。*
