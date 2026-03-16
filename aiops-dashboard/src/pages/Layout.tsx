import React from 'react'
import { Layout as AntLayout } from 'antd'
import { Outlet } from 'react-router-dom'
import { Header } from '../../components/Header'

const { Content, Sider } = AntLayout

export const Layout: React.FC = () => {
  return (
    <AntLayout style={{ minHeight: '100vh', background: '#0a0a0f' }}>
      <Header />
      <AntLayout style={{ background: '#0a0a0f' }}>
        <Content style={{ padding: '16px', overflow: 'hidden' }}>
          <Outlet />
        </Content>
      </AntLayout>
    </AntLayout>
  )
}
