import React from 'react'
import { Card, Row, Col, Statistic, Typography, Progress } from 'antd'
import {
  AlertOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  RiseOutlined,
  DollarOutlined,
} from '@ant-design/icons'

const { Title, Text } = Typography

interface MetricCardProps {
  title: string
  value: string | number
  suffix?: string
  change?: number
  icon: React.ReactNode
  color: string
}

const MetricCard: React.FC<MetricCardProps> = ({
  title,
  value,
  suffix,
  change,
  icon,
  color,
}) => {
  return (
    <Card
      styles={{ body: { padding: '16px' } }}
      style={{
        background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,15,0.9))',
        border: '1px solid rgba(255,255,255,0.08)',
        height: '100%',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
        <div>
          <Text style={{ fontSize: 12, color: 'rgba(255,255,255,0.6)' }}>{title}</Text>
          <div style={{ marginTop: 8 }}>
            <Title level={3} style={{ margin: 0, color: '#fff' }}>
              {value}
              {suffix && <span style={{ fontSize: 14, marginLeft: 4 }}>{suffix}</span>}
            </Title>
          </div>
          {change !== undefined && (
            <div style={{ marginTop: 8 }}>
              <Text style={{ color: change >= 0 ? '#52c41a' : '#ff4d4f', fontSize: 12 }}>
                {change >= 0 ? '↑' : '↓'} {Math.abs(change)}% vs 上月
              </Text>
            </div>
          )}
        </div>
        <div
          style={{
            width: 48,
            height: 48,
            borderRadius: '12px',
            background: `linear-gradient(135deg, ${color}40, ${color}20)`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            border: `1px solid ${color}60`,
          }}
        >
          <span style={{ color, fontSize: 24 }}>{icon}</span>
        </div>
      </div>
    </Card>
  )
}

export const MetricsPanel: React.FC = () => {
  // Mock data - replace with API calls
  const metrics = [
    {
      title: '今日告警',
      value: 12,
      change: -40,
      icon: <AlertOutlined />,
      color: '#ff4d4f',
    },
    {
      title: 'MTTR平均',
      value: 15,
      suffix: 'min',
      change: 5,
      icon: <ClockCircleOutlined />,
      color: '#00d4ff',
    },
    {
      title: 'AI准确率',
      value: 92,
      suffix: '%',
      change: 5,
      icon: <CheckCircleOutlined />,
      color: '#52c41a',
    },
    {
      title: '成本分析',
      value: '+12%',
      change: 12,
      icon: <DollarOutlined />,
      color: '#faad14',
    },
  ]

  return (
    <Row gutter={[16, 16]}>
      {metrics.map((metric, index) => (
        <Col xs={24} sm={12} lg={6} key={index}>
          <MetricCard {...metric} />
        </Col>
      ))}
    </Row>
  )
}
