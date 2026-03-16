import React, { useState, useEffect, useCallback } from 'react'
import { Table, Tag, Button, Space, Typography, Badge, Dropdown, message } from 'antd'
import type { TableProps } from 'antd'
import {
  BellOutlined,
  CheckCircleOutlined,
  EyeOutlined,
  DownOutlined,
  SyncOutlined,
  MoreOutlined
} from '@ant-design/icons'
import type { Alert } from '../../types'
import { alertApi } from '../../services/api'
import { useNavigate } from 'react-router-dom'

const { Text } = Typography

const severityColors: Record<string, string> = {
  P1: 'error',
  P2: 'warning',
  P3: 'processing',
  P4: 'default',
  P5: 'default'
}

const statusColors: Record<string, string> = {
  active: 'error',
  suppressed: 'default',
  acknowledged: 'warning',
  resolved: 'success'
}

const aiStatusLabels: Record<string, { text: string; color: string }> = {
  pending: { text: 'Pending', color: 'default' },
  in_progress: { text: 'Analyzing', color: 'processing' },
  completed: { text: 'Analyzed', color: 'success' },
  failed: { text: 'Failed', color: 'error' }
}

export const AlertList: React.FC = () => {
  const navigate = useNavigate()
  const [alerts, setAlerts] = useState<Alert[]>([])
  const [loading, setLoading] = useState(false)
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
    total: 0
  })

  const fetchAlerts = useCallback(async (page = 1, pageSize = 20) => {
    setLoading(true)
    try {
      const response = await alertApi.list({
        page,
        size: pageSize,
        status: 'active'
      })
      setAlerts(response.items)
      setPagination({
        current: response.page,
        pageSize: response.size,
        total: response.total
      })
    } catch (error) {
      console.error('Failed to fetch alerts:', error)
      message.error('Failed to load alerts')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchAlerts()
    // Auto-refresh every 30 seconds
    const interval = setInterval(() => fetchAlerts(pagination.current, pagination.pageSize), 30000)
    return () => clearInterval(interval)
  }, [fetchAlerts, pagination.current, pagination.pageSize])

  const handleAcknowledge = async (alertId: string) => {
    try {
      await alertApi.acknowledge(alertId, 'Acknowledged by user')
      message.success('Alert acknowledged')
      fetchAlerts(pagination.current, pagination.pageSize)
    } catch (error) {
      message.error('Failed to acknowledge alert')
    }
  }

  const handleSilence = async (alertId: string, duration: number) => {
    try {
      await alertApi.silence(alertId, duration, 'Silenced by user')
      message.success(`Alert silenced for ${duration} minutes`)
      fetchAlerts(pagination.current, pagination.pageSize)
    } catch (error) {
      message.error('Failed to silence alert')
    }
  }

  const handleResolve = async (alertId: string) => {
    try {
      await alertApi.resolve(alertId, 'Resolved by user')
      message.success('Alert resolved')
      fetchAlerts(pagination.current, pagination.pageSize)
    } catch (error) {
      message.error('Failed to resolve alert')
    }
  }

  const columns: TableProps<Alert>['columns'] = [
    {
      title: 'Severity',
      dataIndex: 'severity',
      key: 'severity',
      width: 80,
      render: (severity: string) => (
        <Tag color={severityColors[severity] || 'default'}>{severity}</Tag>
      ),
      sorter: (a, b) => a.severity.localeCompare(b.severity)
    },
    {
      title: 'Alert',
      key: 'alert',
      render: (record: Alert) => (
        <div>
          <div style={{ fontWeight: 500, marginBottom: 4 }}>
            <BellOutlined style={{ marginRight: 8, color: '#ff4d4f' }} />
            {record.title}
          </div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.description || 'No description'}
          </Text>
          <div style={{ marginTop: 4 }}>
            <Tag size="small">{record.serviceId}</Tag>
            {record.source && <Tag size="small">{record.source}</Tag>}
          </div>
        </div>
      )
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => (
        <Badge
          status={statusColors[status] as any}
          text={status.charAt(0).toUpperCase() + status.slice(1)}
        />
      )
    },
    {
      title: 'AI Analysis',
      dataIndex: 'aiStatus',
      key: 'aiStatus',
      width: 110,
      render: (status: string) => {
        const { text, color } = aiStatusLabels[status] || { text: status, color: 'default' }
        return <Tag color={color}>{text}</Tag>
      }
    },
    {
      title: 'Started',
      dataIndex: 'startsAt',
      key: 'startsAt',
      width: 150,
      render: (time: string) => (
        <Text type="secondary">{new Date(time).toLocaleString()}</Text>
      ),
      sorter: (a, b) => new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime()
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 150,
      render: (record: Alert) => (
        <Space>
          <Button
            size="small"
            type="primary"
            icon={<EyeOutlined />}
            onClick={() => navigate(`/alerts/${record.alertId}`)}
          >
            View
          </Button>

          <Dropdown
            menu={{
              items: [
                {
                  key: 'ack',
                  label: 'Acknowledge',
                  icon: <CheckCircleOutlined />,
                  onClick: () => handleAcknowledge(record.alertId)
                },
                {
                  key: 'silence5',
                  label: 'Silence 5min',
                  onClick: () => handleSilence(record.alertId, 5)
                },
                {
                  key: 'silence30',
                  label: 'Silence 30min',
                  onClick: () => handleSilence(record.alertId, 30)
                },
                {
                  key: 'silence60',
                  label: 'Silence 1h',
                  onClick: () => handleSilence(record.alertId, 60)
                },
                { type: 'divider' },
                {
                  key: 'resolve',
                  label: 'Resolve',
                  icon: <CheckCircleOutlined />,
                  onClick: () => handleResolve(record.alertId),
                  danger: true
                }
              ]
            }}
          >
            <Button size="small" icon={<DownOutlined />} />
          </Dropdown>
        </Space>
      )
    }
  ]

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Active Alerts
          <Text type="secondary" style={{ marginLeft: 12, fontSize: 14 }}>
            Total: {pagination.total}
          </Text>
        </Typography.Title>
        <Button
          icon={<SyncOutlined spin={loading} />}
          onClick={() => fetchAlerts(pagination.current, pagination.pageSize)}
        >
          Refresh
        </Button>
      </div>

      <Table
        columns={columns}
        dataSource={alerts}
        rowKey="alertId"
        loading={loading}
        pagination={{
          ...pagination,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: (total) => `Total ${total} items`
        }}
        onChange={(newPagination) => {
          fetchAlerts(newPagination.current || 1, newPagination.pageSize || 20)
        }}
        scroll={{ x: 1000 }}
      />
    </div>
  )
}
