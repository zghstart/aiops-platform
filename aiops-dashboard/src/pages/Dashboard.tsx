import React from 'react'
import { Row, Col, Card, Typography } from 'antd'
import { AIStreamLog } from '../../components/AIStreamLog'
import { TopologyGraph } from '../../components/Topology'
import { MetricsPanel } from '../../components/Charts/MetricsPanel'
import { RecentAlerts } from '../../components/Alerts/RecentAlerts'

const { Title } = Typography

// Mock topology data
const mockTopologyData = {
  nodes: [
    { id: 'gateway', name: 'API Gateway', type: 'gateway', status: 'healthy' },
    { id: 'payment-service', name: 'Payment Service', type: 'service', status: 'critical' },
    { id: 'user-service', name: 'User Service', type: 'service', status: 'healthy' },
    { id: 'order-service', name: 'Order Service', type: 'service', status: 'healthy' },
    { id: 'db-primary', name: 'DB Primary', type: 'database', status: 'critical' },
    { id: 'db-replica', name: 'DB Replica', type: 'database', status: 'healthy' },
    { id: 'redis-cache', name: 'Redis Cache', type: 'cache', status: 'healthy' },
  ],
  edges: [
    { source: 'gateway', target: 'payment-service', type: 'calls' },
    { source: 'gateway', target: 'user-service', type: 'calls' },
    { source: 'gateway', target: 'order-service', type: 'calls' },
    { source: 'payment-service', target: 'db-primary', type: 'depends', status: 'error' },
    { source: 'payment-service', target: 'db-replica', type: 'depends' },
    { source: 'payment-service', target: 'redis-cache', type: 'depends' },
    { source: 'user-service', target: 'db-replica', type: 'depends' },
    { source: 'order-service', target: 'db-replica', type: 'depends' },
  ],
}

export const Dashboard: React.FC = () => {
  const handleNodeClick = (node: any) => {
    console.log('Node clicked:', node)
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
            <TopologyGraph data={mockTopologyData} onNodeClick={handleNodeClick} />
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
