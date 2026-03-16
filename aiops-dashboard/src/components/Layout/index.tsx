import React from 'react'
import { Outlet } from 'react-router-dom'
import { Layout as AntLayout } from 'antd'
import { Header } from './Header'

const { Content } = AntLayout

export const Layout: React.FC = () => {
  return (
    <AntLayout style={{ minHeight: '100vh', background: '#0a0a0f' }}>
      <Header />
      <Content style={{ padding: '16px', overflow: 'hidden' }}>
        <Outlet />
      </Content>
    </AntLayout>
  )
}
