import React from 'react'
import { Layout, Badge, Space, Typography, theme } from 'antd'
import {
  DashboardOutlined,
  AlertOutlined,
  SettingOutlined,
  BellOutlined,
  FullscreenOutlined,
} from '@ant-design/icons'

const { Header: AntHeader } = Layout
const { Title, Text } = Typography

export const Header: React.FC = () => {
  const currentTime = new Date().toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })

  return (
    <AntHeader
      style={{
        background: 'linear-gradient(90deg, #0a0a0f 0%, #1a1a2e 50%, #0a0a0f 100%)',
        borderBottom: '1px solid rgba(0, 212, 255, 0.2)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 24px',
        height: '64px',
        position: 'relative',
      }}
    >
      {/* Logo & Title */}
      <Space align="center">
        <div
          style={{
            width: 40,
            height: 40,
            background: 'linear-gradient(135deg, #00d4ff, #00ff88)',
            borderRadius: '8px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 0 20px rgba(0, 212, 255, 0.3)',
          }}
        >
          <DashboardOutlined style={{ fontSize: 24, color: '#0a0a0f' }} />
        </div>
        <div>
          <Title
            level={4}
            style={{
              margin: 0,
              background: 'linear-gradient(90deg, #00d4ff, #00ff88)',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
              fontWeight: 700,
            }}
          >
            AIOps 智能运维中心
          </Title>
          <Text style={{ fontSize: 11, color: 'rgba(255,255,255,0.5)' }}>
            Intelligent Operations Platform
          </Text>
        </div>
      </Space>

      {/* Center: Time & Status */}
      <Space size="large" align="center">
        <div style={{ textAlign: 'center' }}>
          <Text style={{ fontSize: 18, fontWeight: 600, color: '#fff' }}>
            {currentTime}
          </Text>
        </div>
        <Badge
          status="success"
          text={<span style={{ color: '#00ff88' }}>系统健康</span>}
        />
      </Space>

      {/* Right: Actions */}
      <Space size="large">
        <Badge count={5} size="small">
          <BellOutlined style={{ fontSize: 20, color: '#fff', cursor: 'pointer' }} />
        </Badge>
        <FullscreenOutlined style={{ fontSize: 20, color: '#fff', cursor: 'pointer' }} />
        <AlertOutlined style={{ fontSize: 20, color: '#fff', cursor: 'pointer' }} />
        <SettingOutlined style={{ fontSize: 20, color: '#fff', cursor: 'pointer' }} />
      </Space>
    </AntHeader>
  )
}
