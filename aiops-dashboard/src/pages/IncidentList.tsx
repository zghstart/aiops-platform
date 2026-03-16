import React, { useState, useEffect, useCallback } from 'react'
import { Table, Tag, Button, Space, Typography, Badge, Row, Col, Card, DatePicker } from 'antd'
import type { TableProps } from 'antd'
import { useNavigate } from 'react-router-dom'
import { AlertOutlined, ClockCircleOutlined, SyncOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { Incident } from '../types'

// Mock incident service (to be implemented in api.ts)
const mockIncidentList = async (params: any) => {
  // Mock data
  return {
    items: [
      {
        id: 'inc-001',
        clusterKey: 'payment-service|timeout',
        serviceId: 'payment-service',
        status: 'analyzing',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
      {
        id: 'inc-002',
        clusterKey: 'user-service|db-error',
        serviceId: 'user-service',
        status: 'identified',
        createdAt: new Date(Date.now() - 3600000).toISOString(),
        updatedAt: new Date().toISOString(),
      },
    ] as Incident[],
    total: 2,
    page: 1,
    size: 20,
    totalPages: 1,
  }
}

const { Title, Text } = Typography
const { RangePicker } = DatePicker

const statusColors: Record<string, string> = {
  analyzing: 'processing',
  identified: 'warning',
  mitigated: 'success',
  resolved: 'success',
}

export const IncidentList: React.FC = () => {
  const navigate = useNavigate()
  const [incidents, setIncidents] = useState<Incident[]>([])
  const [loading, setLoading] = useState(false)
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
    total: 0,
  })

  const fetchIncidents = useCallback(async (page = 1, pageSize = 20) => {
    setLoading(true)
    try {
      const response = await mockIncidentList({ page, size: pageSize })
      setIncidents(response.items)
      setPagination({
        current: response.page,
        pageSize: response.size,
        total: response.total,
      })
    } catch (error) {
      console.error('Failed to fetch incidents:', error)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchIncidents()
    const interval = setInterval(() => fetchIncidents(pagination.current, pagination.pageSize), 30000)
    return () => clearInterval(interval)
  }, [fetchIncidents, pagination.current, pagination.pageSize])

  const columns: TableProps<Incident>['columns'] = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      render: (id: string) => <Text code>{id}</Text>,
    },
    {
      title: 'Service',
      dataIndex: 'serviceId',
      key: 'serviceId',
      render: (service: string) => <Tag color="blue">{service}</Tag>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Badge status={statusColors[status] as any} text={status.toUpperCase()} />
      ),
    },
    {
      title: 'Cluster Key',
      dataIndex: 'clusterKey',
      key: 'clusterKey',
      ellipsis: true,
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (time: string) => (
        <Text type="secondary"><ClockCircleOutlined /> {dayjs(time).fromNow()}</Text>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (record: Incident) => (
        <Space>
          <Button
            type="primary"
            size="small"
            icon={<AlertOutlined />}
            onClick={() => navigate(`/incidents/${record.id}`)}
          >
            View
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={4} style={{ margin: 0 }}>
            Incidents
            <Text type="secondary" style={{ marginLeft: 12, fontSize: 14 }}>
              Total: {pagination.total}
            </Text>
          </Title>
        </Col>
        <Col>
          <Space>
            <RangePicker showTime />
            <Button
              icon={<SyncOutlined spin={loading} />}
              onClick={() => fetchIncidents(pagination.current, pagination.pageSize)}
            >
              Refresh
            </Button>
          </Space>
        </Col>
      </Row>

      <Card>
        <Table
          columns={columns}
          dataSource={incidents}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `Total ${total} items`,
          }}
          onChange={(newPagination) => {
            fetchIncidents(newPagination.current || 1, newPagination.pageSize || 20)
          }}
        />
      </Card>
    </div>
  )
}
