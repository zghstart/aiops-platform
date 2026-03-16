import React, { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Card, Descriptions, Tag, Button, Timeline, Row, Col, Typography, Spin, message, Steps } from 'antd'
import { ArrowLeftOutlined, PlayCircleOutlined, CheckCircleOutlined } from '@ant-design/icons'
import type { Incident, AnalysisResult, StreamEvent } from '../types'
import { aiApi } from '../services/api'

const { Title, Text, Paragraph } = Typography
const { Step } = Steps

export const IncidentDetail: React.FC = () => {
  const { incidentId } = useParams<{ incidentId: string }>()
  const navigate = useNavigate()
  const [incident, setIncident] = useState<Incident | null>(null)
  const [analysis, setAnalysis] = useState<AnalysisResult | null>(null)
  const [streaming, setStreaming] = useState(false)
  const [steps, setSteps] = useState<string[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (incidentId) {
      fetchIncidentDetail(incidentId)
    }
  }, [incidentId])

  const fetchIncidentDetail = async (id: string) => {
    setLoading(true)
    try {
      // Mock data - replace with actual API call
      setIncident({
        id: id,
        tenantId: 'default',
        clusterKey: 'payment-service|timeout',
        serviceId: 'payment-service',
        status: 'analyzing',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      })

      // Try to get existing analysis
      try {
        const analysisData = await aiApi.getResult(id)
        setAnalysis(analysisData.result || null)
      } catch (e) {
        // No analysis yet
      }
    } catch (error) {
      message.error('Failed to load incident details')
    } finally {
      setLoading(false)
    }
  }

  const startAnalysis = () => {
    if (!incidentId) return

    setStreaming(true)
    setSteps([])

    const cleanup = aiApi.streamAnalysis(
      incidentId,
      (event: StreamEvent) => {
        console.log('Received event:', event)

        switch (event.type) {
          case 'reasoning':
            setSteps(prev => [...prev, `Step ${event.round}: ${event.thought?.substring(0, 100)}...`])
            break
          case 'tool_call':
            setSteps(prev => [...prev, `Tool: ${event.action}`])
            break
          case 'conclusion':
            setAnalysis(event.result || null)
            break
          case 'complete':
          case 'timeout':
            setStreaming(false)
            break
        }
      },
      (error) => {
        console.error('Stream error:', error)
        setStreaming(false)
        message.error('Analysis stream failed')
      }
    )

    // Cleanup on unmount
    return () => cleanup()
  }

  const getStatusStep = () => {
    switch (incident?.status) {
      case 'analyzing': return 0
      case 'identified': return 1
      case 'mitigated': return 2
      case 'resolved': return 3
      default: return 0
    }
  }

  if (loading || !incident) {
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
          <Card title={<Title level={4} style={{ margin: 0 }}>Incident {incident.id}</Title>}>
            <Descriptions column={2}>
              <Descriptions.Item label="Service">{incident.serviceId}</Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={incident.status === 'resolved' ? 'success' : 'processing'}>
                  {incident.status.toUpperCase()}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Cluster Key">{incident.clusterKey}</Descriptions.Item>
              <Descriptions.Item label="Created">
                {new Date(incident.createdAt).toLocaleString()}
              </Descriptions.Item>
            </Descriptions>

            <Steps current={getStatusStep()} style={{ marginTop: 24 }}>
              <Step title="Analyzing" description="AI investigation" />
              <Step title="Identified" description="Root cause found" />
              <Step title="Mitigated" description="Issue contained" />
              <Step title="Resolved" description="Fully resolved" />
            </Steps>
          </Card>

          <Card
            title="AI Analysis"
            style={{ marginTop: 16 }}
            extra={
              !analysis && !streaming && (
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  onClick={startAnalysis}
                >
                  Start Analysis
                </Button>
              )
            }
          >
            {streaming && (
              <div style={{ marginBottom: 16 }}>
                <Spin style={{ marginRight: 8 }} />
                <Text>AI is analyzing the incident...</Text>
                <Timeline style={{ marginTop: 16 }}>
                  {steps.map((step, idx) => (
                    <Timeline.Item key={idx}>{step}</Timeline.Item>
                  ))}
                </Timeline>
              </div>
            )}

            {analysis ? (
              <div>
                <Descriptions column={1}>
                  <Descriptions.Item label="Root Cause">
                    <Paragraph>{analysis.rootCause}</Paragraph>
                  </Descriptions.Item>
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
                      <li key={idx}><Text>{item}</Text></li>
                    ))}
                  </ul>
                </div>

                <div style={{ marginTop: 16 }}>
                  <Text strong>Recommendations:</Text>
                  <ul style={{ marginTop: 8 }}>
                    {analysis.recommendations.map((item, idx) => (
                      <li key={idx}>
                        <Text><CheckCircleOutlined style={{ color: '#52c41a', marginRight: 8 }} />{item}</Text>
                      </li>
                    ))}
                  </ul>
                </div>

                <Text type="secondary" style={{ marginTop: 16, display: 'block' }}>
                  Analysis completed in {analysis.analysisTimeSec}s | Tokens used: {analysis.tokensUsed}
                </Text>
              </div>
            ) : (
              !streaming && (
                <div style={{ textAlign: 'center', padding: 40 }}>
                  <Text type="secondary">No AI analysis available yet</Text>
                  <br />
                  <Button
                    type="primary"
                    icon={<PlayCircleOutlined />}
                    onClick={startAnalysis}
                    style={{ marginTop: 16 }}
                  >
                    Start AI Analysis
                  </Button>
                </div>
              )
            )}
          </Card>
        </Col>

        <Col span={8}>
          <Card title="Timeline">
            <Timeline mode="left">
              <Timeline.Item label={new Date(incident.createdAt).toLocaleTimeString()}>
                Incident Created
              </Timeline.Item>
              <Timeline.Item label={new Date().toLocaleTimeString()}>
                AI Analysis Started
              </Timeline.Item>
              {analysis && (
                <Timeline.Item label={new Date(analysis.completedAt).toLocaleTimeString()}>
                  Root Cause Identified
                </Timeline.Item>
              )}
            </Timeline>
          </Card>
        </Col>
      </Row>
    </div>
  )
}
