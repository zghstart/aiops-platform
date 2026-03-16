import React, { useEffect, useRef } from 'react'
import { Graph } from '@antv/g6'

interface TopologyGraphProps {
  data: {
    nodes: Array<{
      id: string
      name: string
      type: string
      status?: string
      metrics?: {
        cpu?: number
        memory?: number
      }
    }>
    edges: Array<{
      source: string
      target: string
      type?: string
      status?: string
    }>
  }
  onNodeClick?: (node: any) => void
}

export const TopologyGraph: React.FC<TopologyGraphProps> = ({ data, onNodeClick }) => {
  const containerRef = useRef<HTMLDivElement>(null)
  const graphRef = useRef<Graph | null>(null)

  useEffect(() => {
    if (!containerRef.current) return

    const graph = new Graph({
      container: containerRef.current,
      width: containerRef.current.offsetWidth,
      height: 400,
      modes: {
        default: ['drag-canvas', 'zoom-canvas', 'drag-node'],
      },
      layout: {
        type: 'force',
        preventOverlap: true,
        linkDistance: 100,
        nodeStrength: -50,
      },
      nodeStateStyles: {
        hover: {
          stroke: '#00d4ff',
          lineWidth: 2,
        },
        selected: {
          stroke: '#00ff88',
          lineWidth: 3,
        },
      },
      edgeStateStyles: {
        hover: {
          stroke: '#00d4ff',
          lineWidth: 2,
        },
      },
    })

    graphRef.current = graph

    // Transform data
    const nodes = data.nodes.map((node) => ({
      id: node.id,
      label: node.name,
      type: node.type,
      size: node.type === 'service' ? 40 : 30,
      style: {
        fill: getNodeColor(node.status, node.type),
        stroke: getNodeStroke(node.status),
        lineWidth: 2,
      },
      labelCfg: {
        style: {
          fill: '#fff',
          fontSize: 12,
        },
      },
    }))

    const edges = data.edges.map((edge) => ({
      source: edge.source,
      target: edge.target,
      style: {
        stroke: edge.status === 'error' ? '#ff4d4f' : 'rgba(255,255,255,0.3)',
        lineWidth: edge.status === 'error' ? 3 : 1,
        endArrow: true,
      },
    }))

    graph.data({ nodes, edges })
    graph.render()

    graph.on('node:click', (evt) => {
      onNodeClick?.(evt.item?.getModel())
    })

    return () => {
      graph.destroy()
    }
  }, [data, onNodeClick])

  return <div ref={containerRef} style={{ width: '100%', height: 400 }} />
}

function getNodeColor(status?: string, type?: string): string {
  if (status === 'critical') return '#ff4d4f'
  if (status === 'warning') return '#faad14'
  if (status === 'healthy') return '#52c41a'

  switch (type) {
    case 'service': return '#00d4ff'
    case 'database': return '#722ed1'
    case 'cache': return '#fa8c16'
    case 'gateway': return '#52c41a'
    default: return '#00d4ff'
  }
}

function getNodeStroke(status?: string): string {
  if (status === 'critical') return '#ff7875'
  if (status === 'warning') return '#ffd666'
  return '#00d4ff'
}
