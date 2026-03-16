import React, { useState, useEffect, useCallback } from 'react'
import { Card, Select, Button, Row, Col, Tag, Statistic, message } from 'antd'
import { ReloadOutlined, FullscreenOutlined } from '@ant-design/icons'
import { Graph, Node, Edge, Layout } from '@antv/g6'
import type { TopologyData, TopologyNode, TopologyEdge } from '../types'

// Mock topology API
const mockGetTopology = async (serviceId: string): Promise<TopologyData> => {
  return {
    serviceId,
    nodes: [
      { id: 'gateway', name: 'API Gateway', type: 'gateway', health: 'healthy' },
      { id: serviceId, name: serviceId, type: 'service', health: 'warning', isRoot: true },
      { id: `${serviceId}-db`, name: 'Database', type: 'database', health: 'healthy' },
      { id: `${serviceId}-cache`, name: 'Cache', type: 'cache', health: 'healthy' },
      { id: 'downstream-1', name: 'Order Service', type: 'service', health: 'healthy' },
    ],
    edges: [
      { source: 'gateway', target: serviceId, type: 'calls' },
      { source: serviceId, target: `${serviceId}-db`, type: 'uses_database' },
      { source: serviceId, target: `${serviceId}-cache`, type: 'uses_cache' },
      { source: serviceId, target: 'downstream-1', type: 'depends' },
    ],
    depth: 2,
    direction: 'both',
    impactAnalysis: {
      directDependencies: [`${serviceId}-db`, `${serviceId}-cache`],
      dependentServices: ['gateway'],
      blastRadius: 5,
      unhealthyDependencies: [],
      riskLevel: 'medium',
    },
    metadata: {
      totalNodes: 5,
      totalEdges: 4,
      elapsedMs: 100,
      timestamp: new Date().toISOString(),
    },
    cacheHit: false,
  }
}

export const TopologyPage: React.FC = () => {
  const [serviceId, setServiceId] = useState('payment-service')
  const [topology, setTopology] = useState<TopologyData | null>(null)
  const [loading, setLoading] = useState(false)
  const graphRef = React.useRef<Graph | null>(null)
  const containerRef = React.useRef<HTMLDivElement>(null)

  const fetchTopology = useCallback(async () => {
    setLoading(true)
    try {
      const data = await mockGetTopology(serviceId)
      setTopology(data)
      renderGraph(data)
    } catch (error) {
      message.error('Failed to load topology')
    } finally {
      setLoading(false)
    }
  }, [serviceId])

  useEffect(() => {
    fetchTopology()
    return () => {
      if (graphRef.current) {
        graphRef.current.destroy()
      }
    }
  }, [fetchTopology])

  const renderGraph = (data: TopologyData) => {
    if (!containerRef.current) return

    if (graphRef.current) {
      graphRef.current.destroy()
    }

    const graph = new Graph({
      container: containerRef.current,
      width: containerRef.current.clientWidth,
      height: 600,
      modes: {
        default: ['drag-canvas', 'zoom-canvas', 'drag-node'],
      },
      layout: {
        type: 'dagre',
        rankdir: 'LR',
        align: 'UL',
        nodesep: 50,
        ranksep: 100,
      },
      defaultNode: {
        type: 'circle',
        size: 50,
        style: {
          stroke: '#666',
          lineWidth: 2,
        },
        labelCfg: {
          position: 'bottom',
          style: {
            fill: '#fff',
          },
        },
      },
      defaultEdge: {
        type: 'line',
        style: {
          stroke: '#888',
          lineWidth: 2,
        },
        labelCfg: {
          autoRotate: true,
          style: {
            fill: '#fff',
          },
        },
      },
    })

    // Transform data for G6
    const nodes = data.nodes.map((node: TopologyNode) => ({
      id: node.id,
      label: node.name,
      type: getNodeType(node.type),
      style: {
        fill: getNodeColor(node.health),
        stroke: node.isRoot ? '#00d4ff' : '#666',
        lineWidth: node.isRoot ? 4 : 2,
      },
    }))

    const edges = data.edges.map((edge: TopologyEdge) => ({
      source: edge.source,
      target: edge.target,
      label: edge.type.replace('_', ' '),
      style: {
        stroke: edge.type.includes('error') ? '#ff4d4f' : '#888',
      },
    }))

    graph.data({ nodes, edges })
    graph.render()

    // Fit to view
    graph.fitView()

    graphRef.current = graph
  }

  const getNodeType = (type: string): string => {
    switch (type) {
      case 'gateway': return 'diamond'
      case 'database': return 'rect'
      case 'cache': return 'triangle'
      default: return 'circle'
    }
  }

  const getNodeColor = (health: string): string => {
    switch (health) {
      case 'healthy': return '#52c41a'
      case 'warning': return '#faad14'
      case 'error': return '#ff4d4f'
      case 'critical': return '#ff0000'
      default: return '#d9d9d9'
    }
  }

  return (
    <div>
      <Card
        title="Service Topology"
        extra={
          <Row gutter={16} align="middle">
            <Col>
              <Select
                value={serviceId}
                onChange={setServiceId}
                style={{ width: 200 }}
                options={[
                  { value: 'payment-service', label: 'Payment Service' },
                  { value: 'user-service', label: 'User Service' },
                  { value: 'order-service', label: 'Order Service' },
                ]}
              />
            </Col>
            <Col>
              <Button
                icon={<ReloadOutlined spin={loading} />}
                onClick={fetchTopology}
              >
                Refresh
              </Button>
            </Col>
            <Col>
              <Button icon={<FullscreenOutlined />}>Full Screen</Button>
            </Col>
          </Row>
        }
      >
        {topology && (
          <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
            <Col span={6}>
              <Statistic
                title="Total Nodes"
                value={topology.metadata.totalNodes}
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="Direct Dependencies"
                value={topology.impactAnalysis.directDependencies.length}
              />
            </Col>
            <Col span={6}>
              <Statistic
                title="Blast Radius"
                value={topology.impactAnalysis.blastRadius}
              />
            </Col>
            <Col span={6}>
              <div>
                <div style={{ marginBottom: 8 }}>Risk Level</div>
                <Tag
                  color={
                    topology.impactAnalysis.riskLevel === 'high' ? 'error' :
                    topology.impactAnalysis.riskLevel === 'medium' ? 'warning' : 'success'
                  }
                >
                  {topology.impactAnalysis.riskLevel.toUpperCase()}
                </Tag>
              </div>
            </Col>
          </Row>
        )}

        <div
          ref={containerRef}
          style={{
            width: '100%',
            height: 600,
            background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,20,0.9))',
            borderRadius: 8,
          }}
        />
      </Card>
    </div>
  )
}
