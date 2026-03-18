import React, { useState, useEffect } from 'react'
import { Row, Col, Card, Typography, Spin, Empty, message, Statistic, Progress, Badge, Button } from 'antd'
import { AlertOutlined, CheckCircleOutlined, WarningOutlined, CloseCircleOutlined, ReloadOutlined, ClockCircleOutlined, BarChartOutlined } from '@ant-design/icons'
import { AIStreamLog } from '../components/AIStreamLog'
import { TopologyGraph } from '../components/Topology'
import { MetricsPanel } from '../components/Charts/MetricsPanel'
import { RecentAlerts } from '../components/Alerts/RecentAlerts'
import { ServiceList } from '../components/ServiceList'
import { topologyApi, dashboardApi } from '../services/api'
import type { TopologyData, DashboardSummary } from '../types'

interface ServiceItem {
  id: string
  name: string
  health: 'healthy' | 'warning' | 'critical'
  status: 'running' | 'stopped' | 'unknown'
  latency: number
  errorRate: number
  qps: number
  lastUpdated: string
}

const { Title, Text } = Typography

export const Dashboard: React.FC = () => {
  const [topologyData, setTopologyData] = useState<TopologyData | null>(null)
  const [dashboardSummary, setDashboardSummary] = useState<DashboardSummary | null>(null)
  const [services, setServices] = useState<ServiceItem[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchDashboardData()
  }, [])

  const fetchDashboardData = async () => {
    setLoading(true)
    try {
      // 并行获取拓扑数据和仪表板摘要
      const [topology, summary] = await Promise.all([
        topologyApi.get('root', { depth: 2, direction: 'both' }).catch(() => null),
        dashboardApi.getSummary().catch(() => null)
      ])

      if (topology) {
        setTopologyData(topology)
      }
      if (summary) {
        setDashboardSummary(summary)
      }

      // 模拟服务数据
      const mockServices: ServiceItem[] = [
        {
          id: 'payment-service',
          name: '支付服务',
          health: 'healthy',
          status: 'running',
          latency: 120,
          errorRate: 0.5,
          qps: 1200,
          lastUpdated: new Date().toLocaleString('zh-CN')
        },
        {
          id: 'user-service',
          name: '用户服务',
          health: 'warning',
          status: 'running',
          latency: 250,
          errorRate: 2.3,
          qps: 800,
          lastUpdated: new Date().toLocaleString('zh-CN')
        },
        {
          id: 'order-service',
          name: '订单服务',
          health: 'critical',
          status: 'running',
          latency: 500,
          errorRate: 15.7,
          qps: 500,
          lastUpdated: new Date().toLocaleString('zh-CN')
        },
        {
          id: 'inventory-service',
          name: '库存服务',
          health: 'healthy',
          status: 'running',
          latency: 80,
          errorRate: 0.2,
          qps: 1500,
          lastUpdated: new Date().toLocaleString('zh-CN')
        },
        {
          id: 'shipping-service',
          name: '物流服务',
          health: 'healthy',
          status: 'running',
          latency: 180,
          errorRate: 0.8,
          qps: 600,
          lastUpdated: new Date().toLocaleString('zh-CN')
        },
        {
          id: 'notification-service',
          name: '通知服务',
          health: 'warning',
          status: 'running',
          latency: 320,
          errorRate: 3.1,
          qps: 300,
          lastUpdated: new Date().toLocaleString('zh-CN')
        }
      ]
      setServices(mockServices)
    } catch (error) {
      message.error('加载数据失败')
      console.error('Failed to fetch dashboard data:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleNodeClick = (node: any) => {
    console.log('Node clicked:', node)
    // 可以跳转到服务详情页或打开详情弹窗
  }

  const handleServiceClick = (service: ServiceItem) => {
    console.log('Service clicked:', service)
    // 可以跳转到服务详情页或打开详情弹窗
  }

  const handleRefresh = () => {
    fetchDashboardData()
  }

  const getHealthIcon = (health: string) => {
    switch (health) {
      case 'healthy':
        return <CheckCircleOutlined style={{ color: '#52c41a' }} />
      case 'warning':
        return <WarningOutlined style={{ color: '#faad14' }} />
      case 'error':
      case 'critical':
        return <CloseCircleOutlined style={{ color: '#f5222d' }} />
      default:
        return <ClockCircleOutlined style={{ color: '#1890ff' }} />
    }
  }

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <Spin size="large" tip="加载中..." />
      </div>
    )
  }

  return (
    <div style={{ height: 'calc(100vh - 96px)', overflow: 'auto', padding: '16px' }}>
      {/* Dashboard Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div>
          <Title level={3} style={{ margin: 0, color: '#fff' }}>AIOps 智能运维中心</Title>
          <Text style={{ color: 'rgba(255,255,255,0.6)' }}>实时监控系统状态，智能分析故障根因</Text>
        </div>
        <Button 
          type="primary" 
          icon={<ReloadOutlined />} 
          onClick={handleRefresh}
          style={{ 
            background: 'linear-gradient(90deg, #1890ff, #096dd9)',
            border: 'none',
            boxShadow: '0 2px 8px rgba(24, 144, 255, 0.3)'
          }}
        >
          刷新数据
        </Button>
      </div>

      {/* Summary Cards */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} md={6}>
          <Card
            styles={{
              body: { padding: '16px' },
              header: { 
                background: 'linear-gradient(90deg, rgba(24,144,255,0.1), rgba(9,109,217,0.1))',
                borderBottom: '1px solid rgba(255,255,255,0.08)'
              }
            }}
            style={{
              background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,15,0.9))',
              border: '1px solid rgba(255,255,255,0.08)',
              borderRadius: '8px',
              boxShadow: '0 4px 20px rgba(0,0,0,0.3)'
            }}
          >
            <Statistic 
              title="活跃告警" 
              value={dashboardSummary?.activeAlerts || 0} 
              prefix={<AlertOutlined style={{ color: '#faad14' }} />}
              valueStyle={{ color: '#faad14' }}
            />
            <div style={{ marginTop: 8 }}>
              <Text style={{ color: 'rgba(255,255,255,0.6)' }}>今日已解决: {dashboardSummary?.resolvedToday || 0}</Text>
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card
            styles={{
              body: { padding: '16px' },
              header: { 
                background: 'linear-gradient(90deg, rgba(82,196,26,0.1), rgba(52,168,83,0.1))',
                borderBottom: '1px solid rgba(255,255,255,0.08)'
              }
            }}
            style={{
              background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,15,0.9))',
              border: '1px solid rgba(255,255,255,0.08)',
              borderRadius: '8px',
              boxShadow: '0 4px 20px rgba(0,0,0,0.3)'
            }}
          >
            <Statistic 
              title="系统健康度" 
              value={dashboardSummary?.systemHealth === 'healthy' ? 95 : dashboardSummary?.systemHealth === 'warning' ? 75 : 40} 
              suffix="%"
              valueStyle={{ 
                color: dashboardSummary?.systemHealth === 'healthy' ? '#52c41a' : 
                       dashboardSummary?.systemHealth === 'warning' ? '#faad14' : '#f5222d'
              }}
            />
            <div style={{ marginTop: 8 }}>
              <Progress 
                percent={dashboardSummary?.systemHealth === 'healthy' ? 95 : dashboardSummary?.systemHealth === 'warning' ? 75 : 40} 
                size="small" 
                strokeColor={
                  dashboardSummary?.systemHealth === 'healthy' ? '#52c41a' : 
                  dashboardSummary?.systemHealth === 'warning' ? '#faad14' : '#f5222d'
                }
              />
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card
            styles={{
              body: { padding: '16px' },
              header: { 
                background: 'linear-gradient(90deg, rgba(250,173,20,0.1), rgba(251,113,133,0.1))',
                borderBottom: '1px solid rgba(255,255,255,0.08)'
              }
            }}
            style={{
              background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,15,0.9))',
              border: '1px solid rgba(255,255,255,0.08)',
              borderRadius: '8px',
              boxShadow: '0 4px 20px rgba(0,0,0,0.3)'
            }}
          >
            <Statistic 
              title="平均修复时间" 
              value={dashboardSummary?.averageMTTR || 0} 
              suffix="分钟"
              valueStyle={{ color: '#faad14' }}
            />
            <div style={{ marginTop: 8 }}>
              <Text style={{ color: 'rgba(255,255,255,0.6)' }}>基于历史数据统计</Text>
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card
            styles={{
              body: { padding: '16px' },
              header: { 
                background: 'linear-gradient(90deg, rgba(24,144,255,0.1), rgba(72,209,204,0.1))',
                borderBottom: '1px solid rgba(255,255,255,0.08)'
              }
            }}
            style={{
              background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,15,0.9))',
              border: '1px solid rgba(255,255,255,0.08)',
              borderRadius: '8px',
              boxShadow: '0 4px 20px rgba(0,0,0,0.3)'
            }}
          >
            <Statistic 
              title="服务数量" 
              value={topologyData?.nodes?.length || 0} 
              prefix={<BarChartOutlined style={{ color: '#1890ff' }} />}
              valueStyle={{ color: '#1890ff' }}
            />
            <div style={{ marginTop: 8 }}>
              <Text style={{ color: 'rgba(255,255,255,0.6)' }}>其中 {topologyData?.impactAnalysis?.unhealthyDependencies?.length || 0} 个服务异常</Text>
            </div>
          </Card>
        </Col>
      </Row>

      {/* Top Section: Topology + AI Chat */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} lg={14}>
          <Card
            title="服务拓扑热力图"
            styles={{ 
              body: { padding: 0 },
              header: { 
                background: 'linear-gradient(90deg, rgba(0,212,255,0.1), rgba(0,100,255,0.1))',
                borderBottom: '1px solid rgba(255,255,255,0.08)'
              }
            }}
            style={{
              background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,15,0.9))',
              border: '1px solid rgba(255,255,255,0.08)',
              borderRadius: '8px',
              boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
              height: 500,
            }}
          >
            {topologyData ? (
              <TopologyGraph data={topologyData} onNodeClick={handleNodeClick} />
            ) : (
              <Empty description="暂无拓扑数据" style={{ paddingTop: 100, color: 'rgba(255,255,255,0.6)' }} />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card
            title="AI 故障分析"
            styles={{ 
              body: { padding: 0 },
              header: { 
                background: 'linear-gradient(90deg, rgba(120,119,198,0.1), rgba(255,64,129,0.1))',
                borderBottom: '1px solid rgba(255,255,255,0.08)'
              }
            }}
            style={{
              background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,15,0.9))',
              border: '1px solid rgba(255,255,255,0.08)',
              borderRadius: '8px',
              boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
              height: 500,
            }}
          >
            <AIStreamLog incidentId="demo-incident" />
          </Card>
        </Col>
      </Row>

      {/* Metrics Panel */}
      <Card
        title="系统指标监控"
        styles={{ 
          header: { 
            background: 'linear-gradient(90deg, rgba(82,196,26,0.1), rgba(52,168,83,0.1))',
            borderBottom: '1px solid rgba(255,255,255,0.08)'
          }
        }}
        style={{
          background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,15,0.9))',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: '8px',
          boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
          marginBottom: 24,
        }}
      >
        <MetricsPanel />
      </Card>

      {/* Service List */}
      <Card
        title="服务列表"
        styles={{ 
          header: { 
            background: 'linear-gradient(90deg, rgba(24,144,255,0.1), rgba(72,209,204,0.1))',
            borderBottom: '1px solid rgba(255,255,255,0.08)'
          }
        }}
        style={{
          background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,15,0.9))',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: '8px',
          boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
          marginBottom: 24,
        }}
      >
        <ServiceList services={services} onServiceClick={handleServiceClick} />
      </Card>

      {/* Recent Alerts */}
      <Card
        title="最近告警"
        styles={{ 
          header: { 
            background: 'linear-gradient(90deg, rgba(250,173,20,0.1), rgba(251,113,133,0.1))',
            borderBottom: '1px solid rgba(255,255,255,0.08)'
          }
        }}
        style={{
          background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,15,0.9))',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: '8px',
          boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
        }}
      >
        <RecentAlerts />
      </Card>
    </div>
  )
}
