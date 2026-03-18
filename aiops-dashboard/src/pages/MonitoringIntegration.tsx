import React, { useState, useEffect } from 'react'
import { Card, Button, Table, Form, Input, Select, Switch, Modal, message, Space, Tag, Tooltip } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, TestTubeOutlined, ImportOutlined, ExportOutlined } from '@ant-design/icons'

interface MonitoringSystem {
  id: string
  name: string
  type: string
  config: Record<string, any>
  status: 'active' | 'inactive' | 'error'
  lastChecked: string
  created_at: string
  updated_at: string
}

const { Option } = Select
const { TextArea } = Input

const monitoringTypes = [
  { value: 'host', label: '主机监控' },
  { value: 'application', label: '应用监控' },
  { value: 'database', label: '数据库监控' },
  { value: 'network', label: '网络监控' },
  { value: 'custom', label: '自定义监控' },
]

export const MonitoringIntegration: React.FC = () => {
  const [form] = Form.useForm()
  const [systems, setSystems] = useState<MonitoringSystem[]>([])
  const [isModalVisible, setIsModalVisible] = useState(false)
  const [editingSystem, setEditingSystem] = useState<MonitoringSystem | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    // 模拟数据
    const mockSystems: MonitoringSystem[] = [
      {
        id: '1',
        name: '生产服务器',
        type: 'host',
        config: {
          host: '192.168.1.100',
          port: 22,
          username: 'admin',
          password: '********',
          interval: 60,
        },
        status: 'active',
        lastChecked: new Date().toLocaleString('zh-CN'),
        created_at: new Date().toLocaleString('zh-CN'),
        updated_at: new Date().toLocaleString('zh-CN'),
      },
      {
        id: '2',
        name: 'MySQL数据库',
        type: 'database',
        config: {
          host: '192.168.1.101',
          port: 3306,
          username: 'root',
          password: '********',
          interval: 30,
        },
        status: 'active',
        lastChecked: new Date().toLocaleString('zh-CN'),
        created_at: new Date().toLocaleString('zh-CN'),
        updated_at: new Date().toLocaleString('zh-CN'),
      },
      {
        id: '3',
        name: '应用服务器',
        type: 'application',
        config: {
          url: 'http://192.168.1.102:8080',
          endpoint: '/health',
          interval: 15,
        },
        status: 'error',
        lastChecked: new Date().toLocaleString('zh-CN'),
        created_at: new Date().toLocaleString('zh-CN'),
        updated_at: new Date().toLocaleString('zh-CN'),
      },
    ]
    setSystems(mockSystems)
  }, [])

  const handleAddSystem = () => {
    setEditingSystem(null)
    form.resetFields()
    setIsModalVisible(true)
  }

  const handleEditSystem = (system: MonitoringSystem) => {
    setEditingSystem(system)
    form.setFieldsValue({
      name: system.name,
      type: system.type,
      config: JSON.stringify(system.config, null, 2),
    })
    setIsModalVisible(true)
  }

  const handleDeleteSystem = (id: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这个监控系统吗？',
      onOk: () => {
        setSystems(systems.filter(system => system.id !== id))
        message.success('删除成功')
      },
    })
  }

  const handleTestConnection = (system: MonitoringSystem) => {
    setLoading(true)
    // 模拟测试连接
    setTimeout(() => {
      setLoading(false)
      message.success('连接测试成功')
    }, 1000)
  }

  const handleSubmit = () => {
    form.validateFields().then(values => {
      try {
        const config = JSON.parse(values.config)
        if (editingSystem) {
          // 编辑现有系统
          const updatedSystems = systems.map(system => 
            system.id === editingSystem.id 
              ? {
                  ...system,
                  name: values.name,
                  type: values.type,
                  config,
                  updated_at: new Date().toLocaleString('zh-CN'),
                }
              : system
          )
          setSystems(updatedSystems)
          message.success('更新成功')
        } else {
          // 添加新系统
          const newSystem: MonitoringSystem = {
            id: (systems.length + 1).toString(),
            name: values.name,
            type: values.type,
            config,
            status: 'inactive',
            lastChecked: '',
            created_at: new Date().toLocaleString('zh-CN'),
            updated_at: new Date().toLocaleString('zh-CN'),
          }
          setSystems([...systems, newSystem])
          message.success('添加成功')
        }
        setIsModalVisible(false)
      } catch (error) {
        message.error('配置格式错误，请检查 JSON 格式')
      }
    })
  }

  const getStatusTag = (status: string) => {
    switch (status) {
      case 'active':
        return <Tag color="green">正常</Tag>
      case 'inactive':
        return <Tag color="gray">未激活</Tag>
      case 'error':
        return <Tag color="red">错误</Tag>
      default:
        return <Tag color="default">未知</Tag>
    }
  }

  const getTypeLabel = (type: string) => {
    const item = monitoringTypes.find(t => t.value === type)
    return item ? item.label : type
  }

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => getTypeLabel(type),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => getStatusTag(status),
    },
    {
      title: '最后检查',
      dataIndex: 'lastChecked',
      key: 'lastChecked',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: MonitoringSystem) => (
        <Space size="middle">
          <Tooltip title="测试连接">
            <Button 
              icon={<TestTubeOutlined />} 
              onClick={() => handleTestConnection(record)} 
              loading={loading}
            />
          </Tooltip>
          <Tooltip title="编辑">
            <Button 
              icon={<EditOutlined />} 
              onClick={() => handleEditSystem(record)} 
            />
          </Tooltip>
          <Tooltip title="删除">
            <Button 
              danger 
              icon={<DeleteOutlined />} 
              onClick={() => handleDeleteSystem(record.id)} 
            />
          </Tooltip>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: '16px' }}>
      <Card
        title="监控系统接入"
        extra={
          <Space>
            <Button icon={<ImportOutlined />}>导入</Button>
            <Button icon={<ExportOutlined />}>导出</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAddSystem}>
              添加监控系统
            </Button>
          </Space>
        }
        styles={{
          header: {
            background: 'linear-gradient(90deg, rgba(24,144,255,0.1), rgba(72,209,204,0.1))',
            borderBottom: '1px solid rgba(255,255,255,0.08)',
          },
        }}
        style={{
          background: 'linear-gradient(145deg, rgba(20,20,30,0.8), rgba(10,10,15,0.9))',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: '8px',
          boxShadow: '0 4px 20px rgba(0,0,0,0.3)',
          marginBottom: 24,
        }}
      >
        <Table
          columns={columns}
          dataSource={systems}
          rowKey="id"
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
          }}
          style={{
            background: 'transparent',
          }}
        />
      </Card>

      <Modal
        title={editingSystem ? '编辑监控系统' : '添加监控系统'}
        open={isModalVisible}
        onOk={handleSubmit}
        onCancel={() => setIsModalVisible(false)}
        width={800}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="系统名称"
            rules={[{ required: true, message: '请输入系统名称' }]}
          >
            <Input placeholder="请输入系统名称" />
          </Form.Item>
          <Form.Item
            name="type"
            label="系统类型"
            rules={[{ required: true, message: '请选择系统类型' }]}
          >
            <Select placeholder="请选择系统类型">
              {monitoringTypes.map(type => (
                <Option key={type.value} value={type.value}>
                  {type.label}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="config"
            label="配置参数 (JSON 格式)"
            rules={[{ required: true, message: '请输入配置参数' }]}
          >
            <TextArea
              rows={8}
              placeholder="请输入 JSON 格式的配置参数"
              style={{ fontFamily: 'monospace' }}
            />
            <div style={{ marginTop: 8, fontSize: 12, color: 'rgba(255,255,255,0.6)' }}>
              示例配置：
              <pre style={{ marginTop: 4, background: 'rgba(0,0,0,0.3)', padding: 8, borderRadius: 4 }}>
                {`{
  "host": "192.168.1.100",
  "port": 22,
  "username": "admin",
  "password": "password",
  "interval": 60
}`}
              </pre>
            </div>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
