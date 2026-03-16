import React, { useEffect, useState, useRef } from 'react'
import { Card, Typography, Space, Tag } from 'antd'
import {
  RobotOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons'

const { Text, Title } = Typography

interface AIStreamLogProps {
  incidentId?: string
}

interface LogEntry {
  id: string
  type: 'thought' | 'tool_call' | 'tool_result' | 'conclusion' | 'error'
  content: string
  timestamp: Date
  round?: number
}

export const AIStreamLog: React.FC<AIStreamLogProps> = ({ incidentId }) => {
  const [logs, setLogs] = useState<LogEntry[]>([])
  const [isConnected, setIsConnected] = useState(false)
  const scrollRef = useRef<HTMLDivElement>(null)
  const eventSourceRef = useRef<EventSource | null>(null)

  useEffect(() => {
    if (!incidentId) {
      // Demo mode - add sample logs
      setLogs([
        {
          id: '1',
          type: 'thought',
          content: '检测到数据库连接超时异常，开始分析...',
          timestamp: new Date(),
          round: 1,
        },
        {
          id: '2',
          type: 'tool_call',
          content: '正在检索 ERROR 级别日志...',
          timestamp: new Date(),
          round: 1,
        },
        {
          id: '3',
          type: 'tool_result',
          content: '发现 45 条 ERROR 日志，主要集中在 connection timeout',
          timestamp: new Date(),
          round: 1,
        },
      ])
      return
    }

    // Connect to SSE
    const connectSSE = () => {
      const es = new EventSource(`/api/ai/analyze/stream?incident_id=${incidentId}`)
      eventSourceRef.current = es

      es.onopen = () => setIsConnected(true)

      es.onmessage = (event) => {
        const data = JSON.parse(event.data)
        const newLog: LogEntry = {
          id: Date.now().toString(),
          type: data.type,
          content: getLogContent(data),
          timestamp: new Date(),
          round: data.round,
        }
        setLogs((prev) => [...prev, newLog])
      }

      es.onerror = () => {
        setIsConnected(false)
        es.close()
      }
    }

    connectSSE()

    return () => {
      eventSourceRef.current?.close()
    }
  }, [incidentId])

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [logs])

  const getLogContent = (data: any): string => {
    switch (data.type) {
      case 'reasoning':
        return data.data?.thought || '思考中...'
      case 'tool_call':
        return `调用工具: ${data.data?.action}`
      case 'tool_result':
        return data.data?.result_summary || '工具执行完成'
      case 'conclusion':
        return `分析完成: ${data.data?.root_cause || '已生成结论'}`
      default:
        return JSON.stringify(data.data)
    }
  }

  return (
    <Card
      title={
        <Space>
          <RobotOutlined style={{ color: '#00d4ff' }} />
          <span>AI 诊断实时对话</span>
          {isConnected ? (
            <Tag color="success" icon={<CheckCircleOutlined />}>连接中</Tag>
          ) : incidentId ? (
            <Tag color="error" icon={<ExclamationCircleOutlined />}>断线</Tag>
          ) : (
            <Tag color="processing" icon={<LoadingOutlined />}>演示模式</Tag>
          )}
        </Space>
      }
      styles={{
        body: {
          height: 400,
          padding: 0,
          overflow: 'hidden',
        },
      }}
      style={{
        background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,15,0.9))',
        border: '1px solid rgba(255,255,255,0.08)',
      }}
    >
      <div
        ref={scrollRef}
        style={{
          height: '100%',
          overflowY: 'auto',
          padding: '16px',
          fontFamily: 'monospace',
          fontSize: 13,
        }}
      >
        {logs.map((log, index) => (
          <div
            key={log.id}
            style={{
              marginBottom: 12,
              animation: 'slideIn 0.3s ease-out',
              animationDelay: `${index * 0.05}s`,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
              <span style={{ color: 'rgba(255,255,255,0.5)', fontSize: 11 }}>
                {log.timestamp.toLocaleTimeString('zh-CN')}
              </span>
              {log.round && (
                <Tag size="small" color="blue">Round {log.round}</Tag>
              )}
              {getTypeIcon(log.type)}
            </div>
            <div
              style={{
                marginLeft: 52,
                marginTop: 4,
                color: getTypeColor(log.type),
                lineHeight: 1.6,
              }}
            >
              {log.type === 'thought' && '> '}
              {log.content}
            </div>
          </div>
        ))}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 16 }}>
          <div
            style={{
              width: 8,
              height: 8,
              background: '#00ff88',
              borderRadius: '50%',
              animation: 'pulse 1s infinite',
            }}
          />
          <Text style={{ color: 'rgba(255,255,255,0.5)', fontSize: 12 }}>
            等待输入...
          </Text>
        </div>
      </div>
    </Card>
  )
}

function getTypeIcon(type: LogEntry['type']) {
  switch (type) {
    case 'thought':
      return <span style={{ color: '#00d4ff' }}>💡</span>
    case 'tool_call':
      return <span style={{ color: '#faad14' }}>🔧</span>
    case 'tool_result':
      return <span style={{ color: '#52c41a' }}>✓</span>
    case 'conclusion':
      return <span style={{ color: '#00ff88' }}>🎯</span>
    case 'error':
      return <span style={{ color: '#ff4d4f' }}>✗</span>
    default:
      return null
  }
}

function getTypeColor(type: LogEntry['type']): string {
  switch (type) {
    case 'thought':
      return '#00d4ff'
    case 'tool_call':
      return '#faad14'
    case 'tool_result':
      return '#52c41a'
    case 'conclusion':
      return '#00ff88'
    case 'error':
      return '#ff4d4f'
    default:
      return '#fff'
  }
}
