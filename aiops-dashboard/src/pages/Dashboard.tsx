import React, { useState, useEffect } from 'react'
import { Row, Col, Card, Typography, Spin, Empty, message } from 'antd'
import { AIStreamLog } from '../components/AIStreamLog'
import { TopologyGraph } from '../components/Topology'
import { MetricsPanel } from '../components/Charts/MetricsPanel'
import { RecentAlerts } from '../components/Alerts/RecentAlerts'
import { topologyApi, dashboardApi } from '../services/api'
import type { TopologyData, DashboardSummary } from '../types'

const { Title } = Typography

export const Dashboard: React.FC = () => {
  const [topologyData, setTopologyData] = useState<TopologyData | null>(null)
  const [dashboardSummary, setDashboardSummary] = useState<DashboardSummary | null>(null)
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

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <Spin size="large" tip="加载中..." />
      </div>
    )
  }

  return (
    <div style={{ height: 'calc(100vh - 96px)', overflow: 'auto' }}>
      {/* Top Section: Topology + AI Chat */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} lg={14}>
          <Card
            title="服务拓扑热力图"
            styles={{ body: { padding: 0 } }}
            style={{
              background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,15,0.9))',
              border: '1px solid rgba(255,255,255,0.08)',
              height: 456,
            }}
          >
            {topologyData ? (
              <TopologyGraph data={topologyData} onNodeClick={handleNodeClick} />
            ) : (
              <Empty description="暂无拓扑数据" style={{ paddingTop: 100 }} />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <AIStreamLog incidentId="demo-incident" />
        </Col>
      </Row>

      {/* Metrics Panel */}
      <div style={{ marginBottom: 16 }}>
        <MetricsPanel />
      </div>

      {/* Recent AI Analysis Results */}
      <Row>
        <Col span={24}>
          <RecentAlerts />
        </Col>
      </Row>
    </div>
  )
}
