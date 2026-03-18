import React from 'react'
import { Table, Tag, Space, Button, Tooltip } from 'antd'
import { CheckCircleOutlined, WarningOutlined, CloseCircleOutlined, BarChartOutlined } from '@ant-design/icons'

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

interface ServiceListProps {
  services: ServiceItem[]
  onServiceClick: (service: ServiceItem) => void
}

export const ServiceList: React.FC<ServiceListProps> = ({ services, onServiceClick }) => {
  const getHealthIcon = (health: string) => {
    switch (health) {
      case 'healthy':
        return <CheckCircleOutlined style={{ color: '#52c41a' }} />
      case 'warning':
        return <WarningOutlined style={{ color: '#faad14' }} />
      case 'critical':
        return <CloseCircleOutlined style={{ color: '#f5222d' }} />
      default:
        return null
    }
  }

  const getHealthColor = (health: string) => {
    switch (health) {
      case 'healthy':
        return 'green'
      case 'warning':
        return 'orange'
      case 'critical':
        return 'red'
      default:
        return 'default'
    }
  }

  const columns = [
    {
      title: '服务名称',
      dataIndex: 'name',
      key: 'name',
      render: (text: string, record: ServiceItem) => (
        <Space>
          <BarChartOutlined style={{ color: '#1890ff' }} />
          <span>{text}</span>
        </Space>
      ),
    },
    {
      title: '健康状态',
      dataIndex: 'health',
      key: 'health',
      render: (text: string) => (
        <Tag color={getHealthColor(text)} icon={getHealthIcon(text)}>
          {text === 'healthy' ? '健康' : text === 'warning' ? '警告' : '严重'}
        </Tag>
      ),
    },
    {
      title: '运行状态',
      dataIndex: 'status',
      key: 'status',
      render: (text: string) => (
        <Tag color={text === 'running' ? 'green' : text === 'stopped' ? 'red' : 'default'}>
          {text === 'running' ? '运行中' : text === 'stopped' ? '已停止' : '未知'}
        </Tag>
      ),
    },
    {
      title: '延迟 (ms)',
      dataIndex: 'latency',
      key: 'latency',
      sorter: (a: ServiceItem, b: ServiceItem) => a.latency - b.latency,
    },
    {
      title: '错误率 (%)',
      dataIndex: 'errorRate',
      key: 'errorRate',
      sorter: (a: ServiceItem, b: ServiceItem) => a.errorRate - b.errorRate,
    },
    {
      title: 'QPS',
      dataIndex: 'qps',
      key: 'qps',
      sorter: (a: ServiceItem, b: ServiceItem) => a.qps - b.qps,
    },
    {
      title: '最后更新',
      dataIndex: 'lastUpdated',
      key: 'lastUpdated',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: ServiceItem) => (
        <Space size="middle">
          <Button type="link" onClick={() => onServiceClick(record)}>
            详情
          </Button>
          <Button type="link" style={{ color: '#1890ff' }}>
            监控
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <Table
      columns={columns}
      dataSource={services}
      rowKey="id"
      pagination={{
        pageSize: 5,
        showSizeChanger: true,
        showQuickJumper: true,
      }}
      style={{
        background: 'transparent',
      }}
      rowClassName={() => 'hover:bg-blue-500/5'}
    />
  )
}
