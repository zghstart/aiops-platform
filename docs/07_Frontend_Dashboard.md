# AIOps 智能运维平台 - 前端大屏开发指南

## 1. 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| React 18 | 18.2.x | UI框架 |
| TypeScript | 5.3.x | 类型安全 |
| Ant Design | 5.x | UI组件库 |
| AntV G6 | 5.x | 拓扑图 |
| AntV L7 | 2.x | 地理/热力图 |
| ECharts | 5.x | 时序图表 |
| Zustand | 4.x | 状态管理 |
| React Query | 5.x | 数据获取 |
| SSE/EventSource | - | 实时流 |

---

## 2. 项目结构

```
aiops-frontend/
├── src/
│   ├── components/           # 组件
│   │   ├── layout/
│   │   │   ├── Header.tsx   # 顶部导航
│   │   │   └── Sidebar.tsx  # 侧边栏
│   │   ├── dashboard/
│   │   │   ├── Topology.tsx # 拓扑图组件
│   │   │   ├── Typewriter.tsx # 打字机效果
│   │   │   ├── MetricCard.tsx # 指标卡片
│   │   │   ├── ConclusionCard.tsx # 结论卡片
│   │   │   └── AlertTrend.tsx # 告警趋势
│   │   ├── charts/
│   │   │   ├── LineChart.tsx
│   │   │   └── GaugeChart.tsx
│   │   └── common/
│   │       └── Loading.tsx
│   ├── pages/
│   │   ├── Dashboard/        # 大屏页面
│   │   │   ├── index.tsx
│   │   │   ├── index.module.css
│   │   │   └── hooks.ts
│   │   ├── Workbench/        # 运维工作台
│   │   └── Knowledge/        # 知识库
│   ├── hooks/
│   │   ├── useSSE.ts         # SSE Hook
│   │   ├── useDiagnosis.ts   # 诊断Hook
│   │   └── useTopology.ts    # 拓扑Hook
│   ├── api/
│   │   ├── client.ts         # API客户端
│   │   ├── alerts.ts         # 告警API
│   │   ├── ai.ts             # AI诊断API
│   │   └── dashboard.ts      # 大屏API
│   ├── store/
│   │   ├── dashboard.ts      # 大屏状态
│   │   └── incident.ts       # 故障状态
│   ├── utils/
│   │   ├── format.ts         # 格式化工具
│   │   └── throttle.ts       # 节流防抖
│   ├── styles/
│   │   ├── global.css
│   │   ├── variables.css
│   │   └── dashboard.css     # 大屏专用样式
│   ├── types/
│   │   ├── alert.ts
│   │   ├── analysis.ts
│   │   └── topology.ts
│   └── main.tsx
├── public/
│   └── favicon.ico
├── index.html
├── vite.config.ts
├── tsconfig.json
├── tailwind.config.js
└── package.json
```

---

## 3. 核心组件代码

### 3.1 大屏入口页面 (pages/Dashboard/index.tsx)

```tsx
// src/pages/Dashboard/index.tsx
import React, { useState, useEffect } from 'react';
import { Layout, Row, Col, Statistic } from 'antd';
import {
  WarningOutlined,
  CheckCircleOutlined,
  LineChartOutlined,
  DollarOutlined
} from '@ant-design/icons';

import TopologyMap from '../../components/dashboard/Topology';
import TypeWriter from '../../components/dashboard/Typewriter';
import ConclusionCard from '../../components/dashboard/ConclusionCard';
import AlertTrend from '../../components/dashboard/AlertTrend';
import MetricCard from '../../components/dashboard/MetricCard';

import { useDashboardSummary } from '../../hooks/useDashboard';
import { useDiagnosisStream } from '../../hooks/useDiagnosis';

import styles from './index.module.css';

const { Content, Header } = Layout;

const Dashboard: React.FC = () => {
  const [currentTime, setCurrentTime] = useState(new Date());
  const { data: summary, isLoading } = useDashboardSummary();
  const { steps, conclusion, currentIncidents } = useDiagnosisStream();

  // 更新时间
  useEffect(() => {
    const timer = setInterval(() => setCurrentTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  return (
    <Layout className={styles.dashboardLayout}>
      {/* 顶部标题栏 */}
      <Header className={styles.header}>
        <div className={styles.title}>
          <h1>🎯 AIOps智能运维中心</h1>
        </div>
        <div className={styles.headerInfo}>
          <span className={styles.time}>
            {currentTime.toLocaleString('zh-CN')}
          </span>
          <div className={styles.healthScore}>
            <CheckCircleOutlined className={styles.healthIcon} />
            <span>系统健康: {summary?.health_score || 87}分</span>
          </div>
        </div>
      </Header>

      <Content className={styles.content}>
        {/* 第一行：拓扑图 + AI诊断流 */}
        <Row gutter={[16, 16]} className={styles.mainRow}>
          <Col span={12}>
            <div className={styles.card}>
              <h3 className={styles.cardTitle}>🔥 服务拓扑热力图</h3>
              <TopologyMap
                data={summary?.topology_health}
                incidents={currentIncidents}
              />
            </div>
          </Col>
          <Col span={12}>
            <div className={styles.card}>
              <h3 className={styles.cardTitle}>🤖 AI诊断实时推理</h3>
              <TypeWriter steps={steps} />
            </div>
          </Col>
        </Row>

        {/* 第二行：指标卡片 */}
        <Row gutter={[16, 16]} className={styles.metricsRow}>
          <Col span={6}>
            <MetricCard
              title="今日告警"
              value={summary?.alert_stats?.today_total || 12}
              trend={summary?.alert_stats?.yoy_change}
              icon={<WarningOutlined />}
              color="#ff4d4f"
            />
          </Col>
          <Col span={6}>
            <MetricCard
              title="平均MTTR"
              value={summary?.mttr?.current_month_avg || 15}
              unit="min"
              target={summary?.mttr?.target}
              icon={<LineChartOutlined />}
              color="#1890ff"
            />
          </Col>
          <Col span={6}>
            <MetricCard
              title="AI准确率"
              value={(summary?.ai_accuracy?.current || 0.92) * 100}
              unit="%"
              trend={summary?.ai_accuracy?.trend?.slice(-2)}
              icon={<CheckCircleOutlined />}
              color="#52c41a"
            />
          </Col>
          <Col span={6}>
            <MetricCard
              title="本月成本"
              value={summary?.cost?.this_month || 1500}
              unit="$"
              trend={summary?.cost?.change_pct}
              icon={<DollarOutlined />}
              color="#faad14"
            />
          </Col>
        </Row>

        {/* 第三行：告警趋势 + 结论卡片 */}
        <Row gutter={[16, 16]} className={styles.bottomRow}>
          <Col span={8}>
            <div className={styles.card}>
              <h3 className={styles.cardTitle}>📈 告警趋势 (7天)</h3>
              <AlertTrend data={summary?.alert_stats?.week_trend} />
            </div>
          </Col>
          <Col span={16}>
            <div className={styles.card}>
              <h3 className={styles.cardTitle}>🚨 最新AI诊断结论</h3>
              <ConclusionCard
                conclusions={currentIncidents}
              />
            </div>
          </Col>
        </Row>
      </Content>
    </Layout>
  );
};

export default Dashboard;
```

### 3.2 打字机效果组件 (Typewriter.tsx)

```tsx
// src/components/dashboard/Typewriter.tsx
import React, { useState, useEffect, useRef } from 'react';
import { List, Tag } from 'antd';
import {
  RobotOutlined,
  SearchOutlined,
  ToolOutlined,
  CheckCircleOutlined
} from '@ant-design/icons';

import styles from './Typewriter.module.css';

interface ReasoningStep {
  round: number;
  type: string;
  thought?: string;
  action?: string;
  status?: string;
  result_summary?: string;
}

interface TypeWriterProps {
  steps: ReasoningStep[];
  typingSpeed?: number;
}

const TypeWriter: React.FC<TypeWriterProps> = ({
  steps,
  typingSpeed = 30
}) => {
  const [displayedSteps, setDisplayedSteps] = useState<ReasoningStep[]>([]);
  const [currentText, setCurrentText] = useState('');
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [isTyping, setIsTyping] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  // 自动滚动到底部
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [displayedSteps, currentText]);

  // 处理打字机效果
  useEffect(() => {
    if (currentStepIndex >= steps.length) return;

    const step = steps[currentStepIndex];
    const text = step.thought || step.result_summary || '';

    if (!text) {
      setDisplayedSteps(prev => [...prev, step]);
      setCurrentStepIndex(prev => prev + 1);
      return;
    }

    setIsTyping(true);
    let charIndex = 0;

    const typeInterval = setInterval(() => {
      if (charIndex <= text.length) {
        setCurrentText(text.slice(0, charIndex));
        charIndex++;
      } else {
        clearInterval(typeInterval);
        setDisplayedSteps(prev => [...prev, { ...step, displayedText: text }]);
        setCurrentText('');
        setCurrentStepIndex(prev => prev + 1);
        setIsTyping(false);
      }
    }, typingSpeed);

    return () => clearInterval(typeInterval);
  }, [steps, currentStepIndex, typingSpeed]);

  const getStepIcon = (type: string) => {
    switch (type) {
      case 'reasoning':
        return <RobotOutlined className={styles.iconReasoning} />;
      case 'tool_call':
        return <SearchOutlined className={styles.iconTool} />;
      case 'tool_result':
        return <ToolOutlined className={styles.iconResult} />;
      case 'conclusion':
        return <CheckCircleOutlined className={styles.iconConclusion} />;
      default:
        return <RobotOutlined />;
    }
  };

  const getStatusColor = (status?: string) => {
    switch (status) {
      case 'calling': return 'processing';
      case 'completed': return 'success';
      case 'failed': return 'error';
      default: return 'default';
    }
  };

  return (
    <div className={styles.typewriterContainer} ref={scrollRef}>
      <List
        className={styles.stepList}
        dataSource={displayedSteps}
        renderItem={(step, index) => (
          <List.Item className={styles.stepItem}>
            <div className={styles.stepContent}>
              <div className={styles.stepHeader}>
                {getStepIcon(step.type)}
                <span className={styles.stepRound}>Step {step.round}</span>
                {step.action && (
                  <Tag color="blue" className={styles.actionTag}>
                    {step.action}
                  </Tag>
                )}
                {step.status && (
                  <Tag color={getStatusColor(step.status)}>
                    {step.status}
                  </Tag>
                )}
              </div>
              <div className={styles.stepBody}>
                <p className={styles.thoughtText}>
                  {step.thought || step.result_summary}
                </p>
              </div>
            </div>
          </List.Item>
        )}
      />

      {/* 正在输入的文本 */}
      {isTyping && (
        <div className={styles.typingIndicator}>
          <RobotOutlined className={styles.pulsingIcon} />
          <span className={styles.typingText}>{currentText}</span>
          <span className={styles.cursor}>▌</span>
        </div>
      )}

      {!isTyping && steps.length === 0 && (
        <div className={styles.emptyState}>
          <RobotOutlined className={styles.emptyIcon} />
          <p>等待新的诊断任务...</p>
        </div>
      )}
    </div>
  );
};

export default TypeWriter;
```

```css
/* src/components/dashboard/Typewriter.module.css */
.typewriterContainer {
  height: 400px;
  overflow-y: auto;
  padding: 16px;
  background: linear-gradient(180deg, #1a1a2e 0%, #16213e 100%);
  border-radius: 8px;
}

.stepList {
  background: transparent;
}

.stepItem {
  border-bottom: 1px solid rgba(255, 255, 255, 0.1) !important;
  padding: 12px 0 !important;
}

.stepContent {
  width: 100%;
}

.stepHeader {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.stepRound {
  font-size: 12px;
  color: #888;
}

.iconReasoning {
  color: #52c41a;
}

.iconTool {
  color: #1890ff;
}

.iconResult {
  color: #faad14;
}

.iconConclusion {
  color: #ff4d4f;
}

.thoughtText {
  color: #e8e8e8;
  margin: 0;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  line-height: 1.6;
}

.typingIndicator {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 0;
  animation: fadeIn 0.3s ease;
}

.pulsingIcon {
  color: #52c41a;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.typingText {
  color: #52c41a;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
}

.cursor {
  color: #52c41a;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.emptyState {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: #666;
}

.emptyIcon {
  font-size: 48px;
  margin-bottom: 16px;
}
```

### 3.3 拓扑图组件 (Topology.tsx)

```tsx
// src/components/dashboard/Topology.tsx
import React, { useEffect, useRef } from 'react';
import { Graph } from '@antv/g6';
import { Empty } from 'antd';

import styles from './Topology.module.css';

interface Node {
  id: string;
  name: string;
  type: 'service' | 'database' | 'cache' | 'gateway';
  status: 'healthy' | 'warning' | 'critical';
  metrics?: {
    qps?: number;
    latency?: number;
    error_rate?: number;
  };
  alerts?: number;
}

interface Edge {
  source: string;
  target: string;
  status: string;
  traffic?: {
    rps?: number;
    latency?: number;
  };
}

interface TopologyMapProps {
  data?: {
    nodes: Node[];
    edges: Edge[];
  };
  incidents?: any[];
}

const TopologyMap: React.FC<TopologyMapProps> = ({ data, incidents }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    // 初始化图
    const graph = new Graph({
      container: containerRef.current,
      width: containerRef.current.offsetWidth,
      height: 400,
      modes: {
        default: ['drag-canvas', 'zoom-canvas', 'drag-node'],
      },
      defaultNode: {
        type: 'circle',
        size: 60,
        style: {
          stroke: '#1890ff',
          lineWidth: 2,
        },
        labelCfg: {
          style: {
            fill: '#fff',
            fontSize: 12,
          },
          position: 'bottom',
        },
      },
      defaultEdge: {
        type: 'line',
        style: {
          stroke: '#888',
          lineWidth: 2,
          endArrow: {
            path: 'M 0,0 L 8,4 L 8,-4 Z',
            fill: '#888',
          },
        },
      },
      layout: {
        type: 'dagre',
        rankdir: 'TB',
        align: 'DL',
        nodesep: 80,
        ranksep: 100,
      },
    });

    graphRef.current = graph;

    // 加载数据
    if (data) {
      const nodeData = data.nodes.map(node => ({
        id: node.id,
        label: node.name,
        type: getNodeType(node.type),
        style: {
          fill: getNodeColor(node.status),
          stroke: getNodeBorder(node.alerts),
          lineWidth: node.alerts && node.alerts > 0 ? 3 : 2,
        },
        icon: {
          show: true,
          img: getNodeIcon(node.type),
          width: 30,
          height: 30,
        },
        // 存储完整数据用于tooltip
        data: node,
      }));

      const edgeData = data.edges.map(edge => ({
        source: edge.source,
        target: edge.target,
        style: {
          stroke: getEdgeColor(edge.status),
          lineWidth: edge.traffic?.rps ? Math.min(edge.traffic.rps / 100, 5) : 2,
          endArrow: true,
        },
        data: edge,
      }));

      graph.data({
        nodes: nodeData,
        edges: edgeData,
      });

      graph.render();

      // 高亮故障节点
      if (incidents) {
        incidents.forEach(incident => {
          highlightNode(graph, incident.service_id);
        });
      }
    }

    // 响应式调整
    const handleResize = () => {
      if (containerRef.current && graphRef.current) {
        graphRef.current.changeSize(
          containerRef.current.offsetWidth,
          400
        );
      }
    };

    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      graph.destroy();
    };
  }, [data, incidents]);

  const getNodeType = (type: string) => {
    const typeMap: Record<string, string> = {
      service: 'circle',
      database: 'rect',
      cache: 'diamond',
      gateway: 'triangle',
    };
    return typeMap[type] || 'circle';
  };

  const getNodeColor = (status: string) => {
    const colorMap: Record<string, string> = {
      healthy: '#52c41a',
      warning: '#faad14',
      critical: '#ff4d4f',
    };
    return colorMap[status] || '#888';
  };

  const getNodeBorder = (alerts?: number) => {
    return alerts && alerts > 0 ? '#ff4d4f' : '#1890ff';
  };

  const getNodeIcon = (type: string) => {
    const iconMap: Record<string, string> = {
      service: '/icons/service.svg',
      database: '/icons/database.svg',
      cache: '/icons/cache.svg',
      gateway: '/icons/gateway.svg',
    };
    return iconMap[type] || '/icons/service.svg';
  };

  const getEdgeColor = (status: string) => {
    const colorMap: Record<string, string> = {
      healthy: '#52c41a',
      warning: '#faad14',
      critical: '#ff4d4f',
    };
    return colorMap[status] || '#888';
  };

  const highlightNode = (graph: Graph, nodeId: string) => {
    const node = graph.findById(nodeId);
    if (node) {
      graph.setItemState(node, 'highlight', true);
      // 添加脉冲动画
      node.get('group').animate(
        (ratio: number) => {
          const stroke = ratio > 0.5 ? '#ff4d4f' : '#ff7875';
          return { stroke };
        },
        {
          duration: 1000,
          repeat: true,
        }
      );
    }
  };

  if (!data || data.nodes.length === 0) {
    return <Empty description="暂无拓扑数据" />;
  }

  return (
    <div ref={containerRef} className={styles.topologyContainer}>
      <div className={styles.legend}>
        <span className={styles.legendItem}>
          <i className={styles.dotHealthy} /> 健康
        </span>
        <span className={styles.legendItem}>
          <i className={styles.dotWarning} /> 警告
        </span>
        <span className={styles.legendItem}>
          <i className={styles.dotCritical} /> 故障
        </span>
      </div>
    </div>
  );
};

export default TopologyMap;
```

### 3.4 SSE Hook (hooks/useSSE.ts)

```tsx
// src/hooks/useSSE.ts
import { useState, useEffect, useRef, useCallback } from 'react';

interface SSEOptions {
  url: string;
  onMessage?: (data: any) => void;
  onError?: (error: Event) => void;
  onOpen?: () => void;
  reconnect?: boolean;
  reconnectInterval?: number;
}

export function useSSE<T = any>({
  url,
  onMessage,
  onError,
  onOpen,
  reconnect = true,
  reconnectInterval = 5000,
}: SSEOptions) {
  const [data, setData] = useState<T[]>([]);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<Event | null>(null);

  const eventSourceRef = useRef<EventSource | null>(null);
  const reconnectTimerRef = useRef<NodeJS.Timeout | null>(null);

  const connect = useCallback(() => {
    if (eventSourceRef.current?.readyState === EventSource.OPEN) {
      return;
    }

    const es = new EventSource(url);
    eventSourceRef.current = es;

    es.onopen = () => {
      setConnected(true);
      setError(null);
      onOpen?.();
    };

    es.onmessage = (event) => {
      try {
        const parsed = JSON.parse(event.data);
        setData(prev => [...prev, parsed]);
        onMessage?.(parsed);
      } catch (e) {
        console.error('Failed to parse SSE message:', e);
      }
    };

    es.onerror = (err) => {
      setConnected(false);
      setError(err);
      onError?.(err);
      es.close();

      if (reconnect) {
        reconnectTimerRef.current = setTimeout(() => {
          connect();
        }, reconnectInterval);
      }
    };
  }, [url, onMessage, onError, onOpen, reconnect, reconnectInterval]);

  const disconnect = useCallback(() => {
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
    }
    eventSourceRef.current?.close();
    eventSourceRef.current = null;
    setConnected(false);
  }, []);

  useEffect(() => {
    connect();
    return disconnect;
  }, [connect, disconnect]);

  return {
    data,
    connected,
    error,
    connect,
    disconnect,
  };
}

// 诊断流Hook
export function useDiagnosisStream() {
  const [steps, setSteps] = useState<any[]>([]);
  const [conclusion, setConclusion] = useState<any>(null);
  const [currentIncidents, setCurrentIncidents] = useState<any[]>([]);

  const { connected } = useSSE({
    url: '/api/v1/dashboard/ai-stream',
    onMessage: (event) => {
      switch (event.type) {
        case 'new_incident':
          setCurrentIncidents(prev => [event.data, ...prev].slice(0, 10));
          break;
        case 'reasoning':
        case 'tool_call':
        case 'tool_result':
          setSteps(prev => {
            // 更新或添加步骤
            const existingIndex = prev.findIndex(
              s => s.round === event.round && s.type === event.type
            );
            if (existingIndex >= 0) {
              const next = [...prev];
              next[existingIndex] = { ...next[existingIndex], ...event };
              return next;
            }
            return [...prev, event];
          });
          break;
        case 'conclusion':
          setConclusion(event.data);
          break;
        case 'incident_complete':
          // 清理已完成的推理步骤
          setSteps([]);
          break;
      }
    },
  });

  return { steps, conclusion, currentIncidents, connected };
}
```

### 3.5 大屏样式 (index.module.css)

```css
/* src/pages/Dashboard/index.module.css */
.dashboardLayout {
  min-height: 100vh;
  background: linear-gradient(135deg, #0f0c29 0%, #302b63 50%, #24243e 100%);
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding: 0 24px;
}

.title {
  h1 {
    color: #fff;
    margin: 0;
    font-size: 24px;
    font-weight: 600;
    text-shadow: 0 0 20px rgba(82, 196, 26, 0.5);
  }
}

.headerInfo {
  display: flex;
  align-items: center;
  gap: 24px;
}

.time {
  color: #8c8c8c;
  font-size: 14px;
  font-family: 'JetBrains Mono', monospace;
}

.healthScore {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #52c41a;
  font-size: 14px;
}

.healthIcon {
  font-size: 18px;
}

.content {
  padding: 16px;
}

.mainRow {
  height: 440px;
}

.card {
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 16px;
  height: 100%;
  transition: all 0.3s ease;
}

.card:hover {
  border-color: rgba(24, 144, 255, 0.5);
  box-shadow: 0 0 30px rgba(24, 144, 255, 0.2);
}

.cardTitle {
  color: #fff;
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 500;
  border-left: 3px solid #1890ff;
  padding-left: 12px;
}

.metricsRow {
  margin-top: 16px;
}

.bottomRow {
  margin-top: 16px;
  height: 300px;
}

/* 响应式 */
@media (max-width: 1920px) {
  .mainRow {
    height: 400px;
  }
}

@media (max-width: 1366px) {
  .mainRow {
    height: 350px;
  }
}
```

---

## 4. 监控系统接入功能

### 4.1 功能概述
监控系统接入功能允许用户配置和管理被监控的系统或主机服务器，支持多种类型的监控系统，包括主机、应用、数据库、网络和自定义监控。

### 4.2 页面设计
- **监控系统列表**：显示已配置的监控系统，包括名称、类型、状态和最后检查时间
- **添加/编辑监控系统**：通过表单配置监控系统参数
- **连接测试**：测试监控系统的连接状态
- **导入/导出**：支持批量导入和导出监控系统配置

### 4.3 监控系统类型
- **主机监控**：支持 Linux、Windows 等操作系统
- **应用监控**：支持 Java、Python、Node.js 等应用
- **数据库监控**：支持 MySQL、PostgreSQL、MongoDB 等数据库
- **网络监控**：支持网络设备和服务的监控
- **自定义监控**：支持用户自定义监控指标和采集方式

### 4.4 配置参数
监控系统配置参数以 JSON 格式存储，包含以下字段：
- **主机监控**：host, port, username, password, interval
- **应用监控**：url, endpoint, interval
- **数据库监控**：host, port, username, password, interval
- **网络监控**：ip, port, protocol, interval
- **自定义监控**：根据用户需求自定义参数

### 4.5 核心组件代码

#### 4.5.1 监控系统接入页面 (MonitoringIntegration.tsx)

```tsx
import React, { useState, useEffect } from 'react'
import { Card, Button, Table, Form, Input, Select, Switch, Modal, message, Space, Tag, Tooltip } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, TestTubeOutlined, ImportOutlined, ExportOutlined } from '@ant-design/icons'

interface MonitoringSystem {
  id: string
  name: string
  type: string
  config: Record<string, any>
  status: 'active' | 'inactive' | 'error'
  lastChecked: string
  created_at: string
  updated_at: string
}

const { Option } = Select
const { TextArea } = Input

const monitoringTypes = [
  { value: 'host', label: '主机监控' },
  { value: 'application', label: '应用监控' },
  { value: 'database', label: '数据库监控' },
  { value: 'network', label: '网络监控' },
  { value: 'custom', label: '自定义监控' },
]

export const MonitoringIntegration: React.FC = () => {
  const [form] = Form.useForm()
  const [systems, setSystems] = useState<MonitoringSystem[]>([])
  const [isModalVisible, setIsModalVisible] = useState(false)
  const [editingSystem, setEditingSystem] = useState<MonitoringSystem | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    // 模拟数据
    const mockSystems: MonitoringSystem[] = [
      {
        id: '1',
        name: '生产服务器',
        type: 'host',
        config: {
          host: '192.168.1.100',
          port: 22,
          username: 'admin',
          password: '********',
          interval: 60,
        },
        status: 'active',
        lastChecked: new Date().toLocaleString('zh-CN'),
        created_at: new Date().toLocaleString('zh-CN'),
        updated_at: new Date().toLocaleString('zh-CN'),
      },
      {
        id: '2',
        name: 'MySQL数据库',
        type: 'database',
        config: {
          host: '192.168.1.101',
          port: 3306,
          username: 'root',
          password: '********',
          interval: 30,
        },
        status: 'active',
        lastChecked: new Date().toLocaleString('zh-CN'),
        created_at: new Date().toLocaleString('zh-CN'),
        updated_at: new Date().toLocaleString('zh-CN'),
      },
      {
        id: '3',
        name: '应用服务器',
        type: 'application',
        config: {
          url: 'http://192.168.1.102:8080',
          endpoint: '/health',
          interval: 15,
        },
        status: 'error',
        lastChecked: new Date().toLocaleString('zh-CN'),
        created_at: new Date().toLocaleString('zh-CN'),
        updated_at: new Date().toLocaleString('zh-CN'),
      },
    ]
    setSystems(mockSystems)
  }, [])

  const handleAddSystem = () => {
    setEditingSystem(null)
    form.resetFields()
    setIsModalVisible(true)
  }

  const handleEditSystem = (system: MonitoringSystem) => {
    setEditingSystem(system)
    form.setFieldsValue({
      name: system.name,
      type: system.type,
      config: JSON.stringify(system.config, null, 2),
    })
    setIsModalVisible(true)
  }

  const handleDeleteSystem = (id: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这个监控系统吗？',
      onOk: () => {
        setSystems(systems.filter(system => system.id !== id))
        message.success('删除成功')
      },
    })
  }

  const handleTestConnection = (system: MonitoringSystem) => {
    setLoading(true)
    // 模拟测试连接
    setTimeout(() => {
      setLoading(false)
      message.success('连接测试成功')
    }, 1000)
  }

  const handleSubmit = () => {
    form.validateFields().then(values => {
      try {
        const config = JSON.parse(values.config)
        if (editingSystem) {
          // 编辑现有系统
          const updatedSystems = systems.map(system => 
            system.id === editingSystem.id 
              ? {
                  ...system,
                  name: values.name,
                  type: values.type,
                  config,
                  updated_at: new Date().toLocaleString('zh-CN'),
                }
              : system
          )
          setSystems(updatedSystems)
          message.success('更新成功')
        } else {
          // 添加新系统
          const newSystem: MonitoringSystem = {
            id: (systems.length + 1).toString(),
            name: values.name,
            type: values.type,
            config,
            status: 'inactive',
            lastChecked: '',
            created_at: new Date().toLocaleString('zh-CN'),
            updated_at: new Date().toLocaleString('zh-CN'),
          }
          setSystems([...systems, newSystem])
          message.success('添加成功')
        }
        setIsModalVisible(false)
      } catch (error) {
        message.error('配置格式错误，请检查 JSON 格式')
      }
    })
  }

  const getStatusTag = (status: string) => {
    switch (status) {
      case 'active':
        return <Tag color="green">正常</Tag>
      case 'inactive':
        return <Tag color="gray">未激活</Tag>
      case 'error':
        return <Tag color="red">错误</Tag>
      default:
        return <Tag color="default">未知</Tag>
    }
  }

  const getTypeLabel = (type: string) => {
    const item = monitoringTypes.find(t => t.value === type)
    return item ? item.label : type
  }

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => getTypeLabel(type),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => getStatusTag(status),
    },
    {
      title: '最后检查',
      dataIndex: 'lastChecked',
      key: 'lastChecked',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: MonitoringSystem) => (
        <Space size="middle">
          <Tooltip title="测试连接">
            <Button 
              icon={<TestTubeOutlined />} 
              onClick={() => handleTestConnection(record)} 
              loading={loading}
            />
          </Tooltip>
          <Tooltip title="编辑">
            <Button 
              icon={<EditOutlined />} 
              onClick={() => handleEditSystem(record)} 
            />
          </Tooltip>
          <Tooltip title="删除">
            <Button 
              danger 
              icon={<DeleteOutlined />} 
              onClick={() => handleDeleteSystem(record.id)} 
            />
          </Tooltip>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: '16px' }}>
      <Card
        title="监控系统接入"
        extra={
          <Space>
            <Button icon={<ImportOutlined />}>导入</Button>
            <Button icon={<ExportOutlined />}>导出</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAddSystem}>
              添加监控系统
            </Button>
          </Space>
        }
        styles={{
          header: {
            background: 'linear-gradient(90deg, rgba(24,144,255,0.1), rgba(72,209,204,0.1))',
            borderBottom: '1px solid rgba(255,255,255,0.08)',
          },
        }}
        style={{
          background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,15,0.9))',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: '8px',
          boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
          marginBottom: 24,
        }}
      >
        <Table
          columns={columns}
          dataSource={systems}
          rowKey="id"
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
          }}
          style={{
            background: 'transparent',
          }}
        />
      </Card>

      <Modal
        title={editingSystem ? '编辑监控系统' : '添加监控系统'}
        open={isModalVisible}
        onOk={handleSubmit}
        onCancel={() => setIsModalVisible(false)}
        width={800}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="系统名称"
            rules={[{ required: true, message: '请输入系统名称' }]}
          >
            <Input placeholder="请输入系统名称" />
          </Form.Item>
          <Form.Item
            name="type"
            label="系统类型"
            rules={[{ required: true, message: '请选择系统类型' }]}
          >
            <Select placeholder="请选择系统类型">
              {monitoringTypes.map(type => (
                <Option key={type.value} value={type.value}>
                  {type.label}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="config"
            label="配置参数 (JSON 格式)"
            rules={[{ required: true, message: '请输入配置参数' }]}
          >
            <TextArea
              rows={8}
              placeholder="请输入 JSON 格式的配置参数"
              style={{ fontFamily: 'monospace' }}
            />
            <div style={{ marginTop: 8, fontSize: 12, color: 'rgba(255,255,255,0.6)' }}>
              示例配置：
              <pre style={{ marginTop: 4, background: 'rgba(0,0,0,0.3)', padding: 8, borderRadius: 4 }}>
                {`{
  "host": "192.168.1.100",
  "port": 22,
  "username": "admin",
  "password": "password",
  "interval": 60
}`}
              </pre>
            </div>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
```

### 5. 配置

#### 5.1 package.json

```json
{
  "name": "aiops-frontend",
  "version": "1.0.0",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview",
    "lint": "eslint src --ext ts,tsx"
  },
  "dependencies": {
    "react": "^18.2.0",
    "react-dom": "^18.2.0",
    "antd": "^5.15.0",
    "@ant-design/icons": "^5.3.0",
    "@antv/g6": "^5.0.0",
    "@antv/l7": "^2.21.0",
    "echarts": "^5.5.0",
    "react-echarts": "^0.1.1",
    "zustand": "^4.5.0",
    "@tanstack/react-query": "^5.24.0",
    "axios": "^1.6.7",
    "dayjs": "^1.11.10"
  },
  "devDependencies": {
    "@types/react": "^18.2.0",
    "@types/react-dom": "^18.2.0",
    "@vitejs/plugin-react": "^4.2.0",
    "typescript": "^5.3.0",
    "vite": "^5.1.0",
    "tailwindcss": "^3.4.0"
  }
}
```

#### 5.2 vite.config.ts

```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    target: 'esnext',
    outDir: 'dist',
    assetsDir: 'assets',
  },
});
```

---

*本文档定义了前端大屏的开发规范，开发者应以此为准。*
