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
    try {
      const query = new URLSearchParams()
      query.set('tenantId', params.tenantId || DEFAULT_TENANT_ID)
      if (params.depth) query.set('depth', String(params.depth))
      if (params.direction) query.set('direction', params.direction)

      const response = await fetchWithTimeout(`${API_BASE_URL}/dashboard/topology?${query}`)
      if (!response.ok) throw new Error(`Failed to fetch topology: ${response.statusText}`)
      const json: ApiResponse<TopologyData> = await response.json()
      return unwrapResponse(json)
    } catch (error) {
      console.warn('Failed to fetch topology, using mock data:', error)
      // Return mock data
      return {
        serviceId: 'root',
        nodes: [
          { id: 'service-1', name: 'API Gateway', type: 'gateway', health: 'healthy', latencyP99: 100, errorRate: 0.1, qps: 1000 },
          { id: 'service-2', name: 'User Service', type: 'service', health: 'healthy', latencyP99: 50, errorRate: 0.05, qps: 500 },
          { id: 'service-3', name: 'Order Service', type: 'service', health: 'warning', latencyP99: 200, errorRate: 1.0, qps: 300 },
          { id: 'service-4', name: 'Product Service', type: 'service', health: 'healthy', latencyP99: 80, errorRate: 0.02, qps: 800 },
          { id: 'service-5', name: 'Database', type: 'database', health: 'healthy', latencyP99: 30, errorRate: 0, qps: 1200 },
          { id: 'service-6', name: 'Cache', type: 'cache', health: 'healthy', latencyP99: 5, errorRate: 0, qps: 2000 },
        ],
        edges: [
          { source: 'service-1', target: 'service-2', type: 'calls' },
          { source: 'service-1', target: 'service-3', type: 'calls' },
          { source: 'service-1', target: 'service-4', type: 'calls' },
          { source: 'service-2', target: 'service-5', type: 'uses_database' },
          { source: 'service-3', target: 'service-5', type: 'uses_database' },
          { source: 'service-4', target: 'service-5', type: 'uses_database' },
          { source: 'service-2', target: 'service-6', type: 'uses_cache' },
          { source: 'service-3', target: 'service-6', type: 'uses_cache' },
          { source: 'service-4', target: 'service-6', type: 'uses_cache' },
        ],
        depth: 2,
        direction: 'both' as const,
        impactAnalysis: {
          directDependencies: ['service-2', 'service-3', 'service-4'],
          dependentServices: [],
          blastRadius: 3,
          unhealthyDependencies: ['service-3'],
          riskLevel: 'medium',
        },
        metadata: {
          totalNodes: 6,
          totalEdges: 9,
          elapsedMs: 100,
          timestamp: new Date().toISOString(),
        },
      }
    }
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
    try {
      const query = new URLSearchParams()
      query.set('tenantId', tenantId || DEFAULT_TENANT_ID)
      const response = await fetchWithTimeout(`${API_BASE_URL}/dashboard/summary?${query}`)
      if (!response.ok) throw new Error(`Failed to fetch dashboard summary: ${response.statusText}`)
      const json: ApiResponse<DashboardSummary> = await response.json()
      const data = unwrapResponse(json)
      // If data is empty, use mock data
      if (data.activeAlerts === 0 && Object.keys(data.alertBySeverity).length === 0) {
        throw new Error('Empty data, using mock data')
      }
      return data
    } catch (error) {
      console.warn('Failed to fetch dashboard summary, using mock data:', error)
      // Return mock data
      return {
        activeAlerts: 5,
        alertBySeverity: {
          P1: 1,
          P2: 2,
          P3: 2,
          P4: 0,
          P5: 0
        },
        resolvedToday: 12,
        averageMTTR: 15.5,
        systemHealth: 'warning',
        timestamp: new Date().toISOString(),
      }
    }
  },

  async getTrend(timeRange: string = '1h', tenantId?: string): Promise<{ timestamp: string; alerts: number; incidents: number }[]> {
    try {
      const query = new URLSearchParams()
      query.set('tenantId', tenantId || DEFAULT_TENANT_ID)
      query.set('range', timeRange)
      const response = await fetchWithTimeout(`${API_BASE_URL}/dashboard/trend?${query}`)
      if (!response.ok) throw new Error(`Failed to fetch trend: ${response.statusText}`)
      const json = await response.json()
      const data = json.data || json
      if (Array.isArray(data) && data.length > 0) {
        return data
      }
      throw new Error('Empty data, using mock data')
    } catch (error) {
      console.warn('Failed to fetch trend, using mock data:', error)
      // Return mock data
      const now = new Date()
      const data = []
      for (let i = 59; i >= 0; i--) {
        const timestamp = new Date(now.getTime() - i * 60000).toISOString()
        data.push({
          timestamp,
          alerts: Math.floor(Math.random() * 5),
          incidents: Math.floor(Math.random() * 2)
        })
      }
      return data
    }
  },

  async getServiceHealth(tenantId?: string): Promise<{ serviceId: string; health: string; latencyP99: number; errorRate: number }[]> {
    try {
      const query = new URLSearchParams()
      query.set('tenantId', tenantId || DEFAULT_TENANT_ID)
      const response = await fetchWithTimeout(`${API_BASE_URL}/dashboard/services?${query}`)
      if (!response.ok) throw new Error(`Failed to fetch service health: ${response.statusText}`)
      const json = await response.json()
      const data = json.data || json
      if (Array.isArray(data) && data.length > 0) {
        return data
      }
      throw new Error('Empty data, using mock data')
    } catch (error) {
      console.warn('Failed to fetch service health, using mock data:', error)
      // Return mock data
      return [
        { serviceId: 'service-1', health: 'healthy', latencyP99: 100, errorRate: 0.1 },
        { serviceId: 'service-2', health: 'healthy', latencyP99: 50, errorRate: 0.05 },
        { serviceId: 'service-3', health: 'warning', latencyP99: 200, errorRate: 1.0 },
        { serviceId: 'service-4', health: 'healthy', latencyP99: 80, errorRate: 0.02 },
        { serviceId: 'service-5', health: 'healthy', latencyP99: 30, errorRate: 0 },
        { serviceId: 'service-6', health: 'healthy', latencyP99: 5, errorRate: 0 },
      ]
    }
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
