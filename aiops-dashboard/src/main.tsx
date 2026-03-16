import React from 'react'
import ReactDOM from 'react-dom/client'
import { ConfigProvider } from 'antd'
import { RouterProvider } from 'react-router-dom'
import { router } from './routes'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#00d4ff',
          colorBgBase: '#0a0a0f',
          colorTextBase: '#ffffff',
          colorBorder: 'rgba(255,255,255,0.1)',
          borderRadius: 8,
        },
        components: {
          Table: {
            headerBg: 'rgba(255,255,255,0.05)',
            headerColor: '#fff',
            rowHoverBg: 'rgba(0,212,255,0.05)',
          },
          Card: {
            colorBgContainer: 'rgba(20,20,30,0.8)',
          },
        },
      }}
    >
      <RouterProvider router={router} />
    </ConfigProvider>
  </React.StrictMode>,
)
