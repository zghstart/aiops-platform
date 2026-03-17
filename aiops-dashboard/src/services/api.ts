// API client for AIOps dashboard
import type {
  Alert,
  AlertListResponse,
  Incident,
  AnalysisResult,
  TopologyData,
  MetricsResponse,
  DashboardSummary,
  StreamEvent
} from '../types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'
const AI_ENGINE_URL = import.meta.env.VITE_AI_ENGINE_URL || 'http://localhost:8000/api/v1'

// Helper for fetch with timeout
async function fetchWithTimeout(url: string, options: RequestInit = {}, timeout = 10000): Promise<Response> {
  const controller = new AbortController()
  const id = setTimeout(() => controller.abort(), timeout)

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal
    })
    clearTimeout(id)
    return response
  } catch (error) {
    clearTimeout(id)
    throw error
  }
}

// Alert APIs
export const alertApi = {
  async list(params: { page?: number; size?: number; status?: string; severity?: string; serviceId?: string } = {}): Promise<AlertListResponse> {
    const query = new URLSearchParams()
    if (params.page) query.set('page', String(params.page))
    if (params.size) query.set('size', String(params.size))
    if (params.status) query.set('status', params.status)
    if (params.severity) query.set('severity', params.severity)
    if (params.serviceId) query.set('serviceId', params.serviceId)

    const response = await fetchWithTimeout(`${API_BASE_URL}/alerts?${query}`)
    if (!response.ok) throw new Error(`Failed to fetch alerts: ${response.statusText}`)
    return response.json()
  },

  async get(alertId: string): Promise<Alert> {
    const response = await fetchWithTimeout(`${API_BASE_URL}/alerts/${alertId}`)
    if (!response.ok) throw new Error(`Failed to fetch alert: ${response.statusText}`)
    return response.json()
  },

  async acknowledge(alertId: string, reason?: string): Promise<void> {
    const response = await fetchWithTimeout(`${API_BASE_URL}/alerts/${alertId}/acknowledge`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason })
    })
    if (!response.ok) throw new Error(`Failed to acknowledge alert: ${response.statusText}`)
  },

  async silence(alertId: string, durationMinutes: number, reason: string): Promise<void> {
    const response = await fetchWithTimeout(`${API_BASE_URL}/alerts/${alertId}/silence`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ durationMinutes, reason })
    })
    if (!response.ok) throw new Error(`Failed to silence alert: ${response.statusText}`)
  },

  async resolve(alertId: string, resolution: string): Promise<void> {
    const response = await fetchWithTimeout(`${API_BASE_URL}/alerts/${alertId}/resolve`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ resolution })
    })
    if (!response.ok) throw new Error(`Failed to resolve alert: ${response.statusText}`)
  }
}

// Incident APIs
export const incidentApi = {
  async list(params: { page?: number; size?: number; status?: string; serviceId?: string } = {}): Promise<{ items: Incident[]; total: number }> {
    const query = new URLSearchParams()
    if (params.page) query.set('page', String(params.page))
    if (params.size) query.set('size', String(params.size))
    if (params.status) query.set('status', params.status)
    if (params.serviceId) query.set('serviceId', params.serviceId)

    const response = await fetchWithTimeout(`${API_BASE_URL}/incidents?${query}`)
    if (!response.ok) throw new Error(`Failed to fetch incidents: ${response.statusText}`)
    return response.json()
  },

  async get(incidentId: string): Promise<Incident> {
    const response = await fetchWithTimeout(`${API_BASE_URL}/incidents/${incidentId}`)
    if (!response.ok) throw new Error(`Failed to fetch incident: ${response.statusText}`)
    return response.json()
  }
}

// AI Analysis APIs
export const aiApi = {
  async getResult(analysisId: string): Promise<AnalysisResult> {
    const response = await fetchWithTimeout(`${AI_ENGINE_URL}/ai/analysis/${analysisId}`)
    if (!response.ok) throw new Error(`Failed to fetch analysis result: ${response.statusText}`)
    return response.json()
  },

  async analyze(incidentId: string): Promise<{ analysisId: string }> {
    const response = await fetchWithTimeout(`${AI_ENGINE_URL}/ai/analyze`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ incidentId })
    })
    if (!response.ok) throw new Error(`Failed to start analysis: ${response.statusText}`)
    return response.json()
  },

  // SSE streaming analysis
  streamAnalysis(incidentId: string, onEvent: (event: StreamEvent) => void, onError?: (error: Error) => void): () => void {
    const eventSource = new EventSource(`${AI_ENGINE_URL}/ai/analyze/stream?incidentId=${incidentId}`)

    eventSource.onopen = () => {
      console.log('SSE connection opened')
    }

    eventSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        onEvent(data)

        // Close connection on completion
        if (data.type === 'complete' || data.type === 'timeout') {
          eventSource.close()
        }
      } catch (error) {
        console.error('Failed to parse SSE data:', error)
      }
    }

    eventSource.onerror = (error) => {
      console.error('SSE error:', error)
      if (onError) onError(new Error('SSE connection error'))
      eventSource.close()
    }

    // Return cleanup function
    return () => eventSource.close()
  },

  async submitFeedback(analysisId: string, feedback: { helpful: boolean; comment?: string }): Promise<void> {
    const response = await fetchWithTimeout(`${AI_ENGINE_URL}/ai/analysis/${analysisId}/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(feedback)
    })
    if (!response.ok) throw new Error(`Failed to submit feedback: ${response.statusText}`)
  }
}

// Default tenant ID for development
const DEFAULT_TENANT_ID = 'default'

// API response wrapper
interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp?: string
}

// Helper to unwrap API response
function unwrapResponse<T>(response: ApiResponse<T>): T {
  return response.data
}

// Topology APIs
export const topologyApi = {
  async get(serviceId: string, params: { depth?: number; direction?: string; tenantId?: string } = {}): Promise<TopologyData> {
    const query = new URLSearchParams()
    query.set('tenantId', params.tenantId || DEFAULT_TENANT_ID)
    if (params.depth) query.set('depth', String(params.depth))
    if (params.direction) query.set('direction', params.direction)

    const response = await fetchWithTimeout(`${API_BASE_URL}/dashboard/topology?${query}`)
    if (!response.ok) throw new Error(`Failed to fetch topology: ${response.statusText}`)
    const json: ApiResponse<TopologyData> = await response.json()
    return unwrapResponse(json)
  }
}

// Metrics APIs
export const metricsApi = {
  async query(query: string, params: { serviceId?: string; instance?: string; timeRange?: { start?: string; end?: string; step?: string } } = {}): Promise<MetricsResponse> {
    const body = { query, ...params }
    const response = await fetchWithTimeout(`${API_BASE_URL}/metrics/query`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
    if (!response.ok) throw new Error(`Failed to query metrics: ${response.statusText}`)
    return response.json()
  }
}

// Dashboard APIs
export const dashboardApi = {
  async getSummary(tenantId?: string): Promise<DashboardSummary> {
    const query = new URLSearchParams()
    query.set('tenantId', tenantId || DEFAULT_TENANT_ID)
    const response = await fetchWithTimeout(`${API_BASE_URL}/dashboard/summary?${query}`)
    if (!response.ok) throw new Error(`Failed to fetch dashboard summary: ${response.statusText}`)
    const json: ApiResponse<DashboardSummary> = await response.json()
    return unwrapResponse(json)
  },

  async getTrend(timeRange: string = '1h', tenantId?: string): Promise<{ timestamp: string; alerts: number; incidents: number }[]> {
    const query = new URLSearchParams()
    query.set('tenantId', tenantId || DEFAULT_TENANT_ID)
    query.set('range', timeRange)
    const response = await fetchWithTimeout(`${API_BASE_URL}/dashboard/trend?${query}`)
    if (!response.ok) throw new Error(`Failed to fetch trend: ${response.statusText}`)
    const json = await response.json()
    return json.data || json
  },

  async getServiceHealth(tenantId?: string): Promise<{ serviceId: string; health: string; latencyP99: number; errorRate: number }[]> {
    const query = new URLSearchParams()
    query.set('tenantId', tenantId || DEFAULT_TENANT_ID)
    const response = await fetchWithTimeout(`${API_BASE_URL}/dashboard/services?${query}`)
    if (!response.ok) throw new Error(`Failed to fetch service health: ${response.statusText}`)
    const json = await response.json()
    return json.data || json
  }
}

// MCP Tools APIs (for debugging)
export const mcpApi = {
  async listTools(): Promise<{ name: string; description: string; parameters: unknown }[]> {
    const response = await fetchWithTimeout(`${AI_ENGINE_URL}/tools`)
    if (!response.ok) throw new Error(`Failed to list tools: ${response.statusText}`)
    const data = await response.json()
    return data.tools
  }
}
