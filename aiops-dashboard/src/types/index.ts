// API types shared across the application

// Alert types
export interface Alert {
  alertId: string
  incidentId: string
  title: string
  description?: string
  severity: 'P1' | 'P2' | 'P3' | 'P4' | 'P5'
  status: 'active' | 'suppressed' | 'acknowledged' | 'resolved'
  aiStatus: 'pending' | 'in_progress' | 'completed' | 'failed'
  serviceId: string
  source: string
  startsAt: string
  createdAt: string
  labels?: Record<string, string>
}

export interface AlertListResponse {
  items: Alert[]
  total: number
  page: number
  size: number
  totalPages: number
}

// Incident types
export interface Incident {
  id: string
  tenantId: string
  clusterKey: string
  serviceId: string
  status: 'analyzing' | 'identified' | 'mitigated' | 'resolved'
  createdAt: string
  updatedAt: string
  alerts?: Alert[]
  analysisResult?: AnalysisResult
}

// Analysis result types
export interface ReasoningStep {
  step: number
  thought: string
  action: string
  actionInput: string
  observation: string
  timestamp: string
}

export interface AnalysisResult {
  incidentId: string
  rootCause: string
  confidence: number
  evidence: string[]
  recommendations: string[]
  reasoningChain: ReasoningStep[]
  completedAt: string
  tokensUsed: number
  analysisTimeSec: number
}

export interface AnalysisResponse {
  status: 'success' | 'error'
  query: string
  result?: AnalysisResult
  error?: string
}

// Stream event types
export type StreamEventType =
  | 'start'
  | 'reasoning'
  | 'token_warning'
  | 'tool_call'
  | 'tool_result'
  | 'conclusion'
  | 'complete'
  | 'timeout'

export interface StreamEvent {
  type: StreamEventType
  incidentId: string
  round?: number
  thought?: string
  action?: string
  status?: string
  resultSummary?: string
  result?: AnalysisResult
  totalSteps?: number
  message?: string
  tokensUsed?: number
  totalTokens?: number
}

// Topology types
export interface TopologyNode {
  id: string
  name: string
  type: 'service' | 'database' | 'cache' | 'gateway' | 'queue' | 'unknown'
  health: 'healthy' | 'warning' | 'error' | 'critical' | 'unknown'
  isRoot?: boolean
  latencyP99?: number
  errorRate?: number
  qps?: number
  availability?: number
  infraType?: string
}

export interface TopologyEdge {
  source: string
  target: string
  type: 'depends' | 'calls' | 'uses_database' | 'uses_cache'
}

export interface TopologyImpact {
  directDependencies: string[]
  dependentServices: string[]
  blastRadius: number
  unhealthyDependencies: string[]
  riskLevel: 'high' | 'medium' | 'low'
}

export interface TopologyData {
  serviceId: string
  nodes: TopologyNode[]
  edges: TopologyEdge[]
  depth: number
  direction: 'upstream' | 'downstream' | 'both'
  impactAnalysis: TopologyImpact
  metadata: {
    totalNodes: number
    totalEdges: number
    elapsedMs: number
    timestamp: string
  }
  cacheHit?: boolean
}

// Metric types
export interface MetricDataPoint {
  timestamp?: string
  value: number
}

export interface MetricsResponse {
  status: string
  query: string
  data: MetricDataPoint[]
  summary: {
    avg: number
    max: number
    min: number
    current?: number
    count: number
  }
}

// Dashboard summary
export interface DashboardSummary {
  activeAlerts: number
  p1Count: number
  p2Count: number
  analyzingCount: number
  analyzedToday: number
  avgAnalysisTime: number
  noiseReductionRate: number
}
