import React from 'react'
import { Card, List, Tag, Typography, Space, Badge } from 'antd'
import { AlertTriangle, AlertCircle, Info } from 'lucide-react'

const { Title, Text } = Typography

interface Alert {
  id: string
  level: 'P1' | 'P2' | 'P3' | 'P4'
  name: string
  service: string
  component: string
  confidence: number
  rootCause: string
  impact: string
  recommendations: string[]
  timestamp: string
  status: 'analyzing' | 'identified' | 'resolved'
}

export const RecentAlerts: React.FC = () => {
  // Mock data
  const alerts: Alert[] = [
    {
      id: 'alert-001',
      level: 'P1',
      name: 'disk-io-throttling',
      service: '数据库主库',
      component: '磁盘 I/O',
      confidence: 87,
      rootCause: 'SSD 健康度下降导致写入延迟飙升',
      impact: '支付服务延迟>5s，订单服务部分超时',
      recommendations: [
        '切换只读副本',
        '联系厂商更换 SSD',
        '降低写入频率',
      ],
      timestamp: '2026-03-16 14:32:05',
      status: 'identified',
    },
    {
      id: 'alert-002',
      level: 'P2',
      name: 'connection-pool-exhausted',
      service: '用户服务',
      component: '数据库连接池',
      confidence: 92,
      rootCause: '连接池配置过小，并发突增导致',
      impact: '部分用户登录失败',
      recommendations: [
        '增加 max_connections',
        '添加连接池监控',
        '实现熔断降级',
      ],
      timestamp: '2026-03-16 14:28:30',
      status: 'analyzing',
    },
    {
      id: 'alert-003',
      level: 'P2',
      name: 'cache-invalidation-storm',
      service: '订单服务',
      component: 'Redis 缓存',
      confidence: 85,
      rootCause: '缓存击穿导致大量请求直达数据库',
      impact: '数据库 CPU 飙升至 95%',
      recommendations: [
        '实施缓存预热',
        '添加布隆过滤器',
        '限流降级',
      ],
      timestamp: '2026-03-16 14:15:00',
      status: 'resolved',
    },
  ]

  const getLevelConfig = (level: string) => {
    switch (level) {
      case 'P1':
        return { color: '#ff4d4f', icon: <AlertCircle size={16} /> }
      case 'P2':
        return { color: '#faad14', icon: <AlertTriangle size={16} /> }
      default:
        return { color: '#00d4ff', icon: <Info size={16} /> }
    }
  }

  return (
    <Card
      title={
        <Space>
          <span>最新 AI 诊断结论</span>
          <Text type="secondary" style={{ fontSize: 12 }}>
            最后更新: 2026-03-16 14:32:05
          </Text>
        </Space>
      }
      style={{
        background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,15,0.9))',
        border: '1px solid rgba(255,255,255,0.08)',
      }}
    >
      <List
        dataSource={alerts}
        renderItem={(alert) => {
          const levelConfig = getLevelConfig(alert.level)
          return (
            <List.Item
              style={{
                borderBottom: '1px solid rgba(255,255,255,0.05)',
                padding: '16px 0',
              }}
            >
              <div style={{ width: '100%' }}>
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'flex-start',
                    marginBottom: 12,
                  }}
                >
                  <Space>
                    <Tag
                      color={levelConfig.color}
                      style={{ fontWeight: 'bold', margin: 0 }}
                    >
                      {levelConfig.icon}
                      <span style={{ marginLeft: 4 }}>{alert.level}</span>
                    </Tag>
                    <Text strong style={{ color: '#fff', fontSize: 14 }}>
                      {alert.name}
                    </Text>
                    <Tag color="default">{alert.service}</Tag>
                    <Badge
                      status={
                        alert.status === 'analyzing'
                          ? 'processing'
                          : alert.status === 'identified'
                          ? 'warning'
                          : 'success'
                      }
                      text={
                        alert.status === 'analyzing'
                          ? '分析中'
                          : alert.status === 'identified'
                          ? '已识别'
                          : '已解决'
                      }
                    />
                  </Space>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    置信度: {alert.confidence}%
                  </Text>
                </div>

                <div style={{ marginLeft: 50 }}>
                  <div style={{ marginBottom: 8 }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      根因:
                    </Text>
                    <Text style={{ color: '#fff', marginLeft: 8 }}>
                      {alert.rootCause}
                    </Text>
                  </div>

                  <div style={{ marginBottom: 8 }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      影响:
                    </Text>
                    <Text style={{ color: '#ff4d4f', marginLeft: 8 }}>
                      {alert.impact}
                    </Text>
                  </div>

                  <div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      建议:
                    </Text>
                    <Space size={8} style={{ marginLeft: 8 }}>
                      {alert.recommendations.map((rec, idx) => (
                        <Tag key={idx} color="processing">
                          {idx + 1}) {rec}
                        </Tag>
                      ))}
                    </Space>
                  </div>
                </div>
              </div>
            </List.Item>
          )
        }}
      />
    </Card>
  )
}
