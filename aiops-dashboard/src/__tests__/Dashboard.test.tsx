import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { Dashboard } from '../pages/Dashboard'

// Mock the API calls
vi.mock('../services/api', () => ({
  topologyApi: {
    get: vi.fn(() => Promise.resolve({
      serviceId: 'root',
      nodes: [],
      edges: [],
      depth: 2,
      direction: 'both',
      impactAnalysis: {
        directDependencies: [],
        dependentServices: [],
        blastRadius: 0,
        unhealthyDependencies: [],
        riskLevel: 'low'
      },
      metadata: { totalNodes: 0, totalEdges: 0, timestamp: new Date().toISOString() }
    }))
  },
  dashboardApi: {
    getSummary: vi.fn(() => Promise.resolve({
      activeAlerts: 0,
      p1Count: 0,
      p2Count: 0,
      analyzingCount: 0,
      analyzedToday: 0,
      avgAnalysisTime: 0,
      noiseReductionRate: 0
    }))
  }
}))

describe('Dashboard', () => {
  it('renders dashboard with topology card', async () => {
    render(
      <BrowserRouter>
        <Dashboard />
      </BrowserRouter>
    )

    // Check if loading state is shown initially
    expect(screen.getByText('加载中...')).toBeDefined()
  })
})
