import React, { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Card, Descriptions, Tag, Button, Timeline, Row, Col, Typography, Spin, message } from 'antd'
import { ArrowLeftOutlined, SyncOutlined, CheckCircleOutlined } from '@ant-design/icons'
import type { Alert, AnalysisResult } from '../types'
import { alertApi, aiApi } from '../services/api'

const { Title, Text } = Typography

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

export const AlertDetail: React.FC = () => {
  const { alertId } = useParams<{ alertId: string }>()
  const navigate = useNavigate()
  const [alert, setAlert] = useState<Alert | null>(null)
  const [analysis, setAnalysis] = useState<AnalysisResult | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (alertId) {
      fetchAlertDetail(alertId)
    }
  }, [alertId])

  const fetchAlertDetail = async (id: string) => {
    setLoading(true)
    try {
      const alertData = await alertApi.get(id)
      setAlert(alertData)

      // Fetch analysis if available
      if (alertData.incidentId) {
        const analysisData = await aiApi.getResult(alertData.incidentId)
        setAnalysis(analysisData.result || null)
      }
    } catch (error) {
      message.error('Failed to load alert details')
    } finally {
      setLoading(false)
    }
  }

  const handleAcknowledge = async () => {
    if (!alert) return
    try {
      await alertApi.acknowledge(alert.alertId, 'Acknowledged from detail page')
      message.success('Alert acknowledged')
      fetchAlertDetail(alert.alertId)
    } catch (error) {
      message.error('Failed to acknowledge')
    }
  }

  const handleResolve = async () => {
    if (!alert) return
    try {
      await alertApi.resolve(alert.alertId, 'Resolved from detail page')
      message.success('Alert resolved')
      fetchAlertDetail(alert.alertId)
    } catch (error) {
      message.error('Failed to resolve')
    }
  }

  if (loading || !alert) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 100 }}>
        <Spin size="large" />
      </div>
    )
  }

  return (
    <div>
      <Button
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate(-1)}
        style={{ marginBottom: 16 }}
      >
        Back
      </Button>

      <Row gutter={[16, 16]}>
        <Col span={16}>
          <Card
            title={
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <Tag color={severityColors[alert.severity]}>{alert.severity}</Tag>
                <Title level={4} style={{ margin: 0 }}>{alert.title}</Title>
              </div>
            }
            extra={
              <Tag color={statusColors[alert.status]}>
                {alert.status.toUpperCase()}
              </Tag>
            }
          >
            <Descriptions column={2}>
              <Descriptions.Item label="Alert ID">{alert.alertId}</Descriptions.Item>
              <Descriptions.Item label="Incident ID">{alert.incidentId}</Descriptions.Item>
              <Descriptions.Item label="Service">{alert.serviceId}</Descriptions.Item>
              <Descriptions.Item label="Source">{alert.source}</Descriptions.Item>
              <Descriptions.Item label="Started">{new Date(alert.startsAt).toLocaleString()}</Descriptions.Item>
              <Descriptions.Item label="Created">{new Date(alert.createdAt).toLocaleString()}</Descriptions.Item>
            </Descriptions>

            <div style={{ marginTop: 16 }}>
              <Text strong>Description:</Text>
              <p style={{ marginTop: 8 }}>{alert.description || 'No description'}</p>
            </div>

            {alert.labels && (
              <div style={{ marginTop: 16 }}>
                <Text strong>Labels:</Text>
                <div style={{ marginTop: 8 }}>
                  {Object.entries(alert.labels).map(([key, value]) => (
                    <Tag key={key}>{key}={value}</Tag>
                  ))}
                </div>
              </div>
            )}

            <div style={{ marginTop: 24, display: 'flex', gap: 12 }}>
              {alert.status === 'active' && (
                <Button type="primary" onClick={handleAcknowledge}>
                  Acknowledge
                </Button>
              )}
              {(alert.status === 'active' || alert.status === 'acknowledged') && (
                <Button type="default" onClick={handleResolve} icon={<CheckCircleOutlined />}>
                  Resolve
                </Button>
              )}
            </div>
          </Card>

          {analysis && (
            <Card title="AI Analysis Result" style={{ marginTop: 16 }}>
              <Descriptions column={1}>
                <Descriptions.Item label="Root Cause">{analysis.rootCause}</Descriptions.Item>
                <Descriptions.Item label="Confidence">
                  <Tag color={analysis.confidence > 0.7 ? 'success' : 'warning'}>
                    {(analysis.confidence * 100).toFixed(0)}%
                  </Tag>
                </Descriptions.Item>
              </Descriptions>

              <div style={{ marginTop: 16 }}>
                <Text strong>Evidence:</Text>
                <ul style={{ marginTop: 8 }}>
                  {analysis.evidence.map((item, idx) => (
                    <li key={idx}>{item}</li>
                  ))}
                </ul>
              </div>

              <div style={{ marginTop: 16 }}>
                <Text strong>Recommendations:</Text>
                <ul style={{ marginTop: 8 }}>
                  {analysis.recommendations.map((item, idx) => (
                    <li key={idx}>{item}</li>
                  ))}
                </ul>
              </div>

              <Text type="secondary" style={{ marginTop: 16, display: 'block' }}>
                Analysis Time: {analysis.analysisTimeSec}s | Tokens: {analysis.tokensUsed}
              </Text>
            </Card>
          )}
        </Col>

        <Col span={8}>
          <Card title="Timeline">
            <Timeline>
              <Timeline.Item color="green">Alert Created</Timeline.Item>
              <Timeline.Item color="blue">Noise Reduction Applied</Timeline.Item>
              <Timeline.Item color={alert.aiStatus === 'completed' ? 'green' : 'gray'}>
                AI Analysis {alert.aiStatus}
              </Timeline.Item>
            </Timeline>
          </Card>
        </Col>
      </Row>
    </div>
  )
}
