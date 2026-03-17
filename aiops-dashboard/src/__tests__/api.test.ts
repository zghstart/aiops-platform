import { describe, it, expect, vi, beforeEach } from 'vitest'
import { alertApi, incidentApi, topologyApi, dashboardApi } from '../services/api'

// Mock fetch
global.fetch = vi.fn()

describe('API Services', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('alertApi', () => {
    it('should fetch alerts list', async () => {
      const mockResponse = {
        items: [],
        total: 0,
        page: 1,
        size: 20,
        totalPages: 0
      }

      ;(global.fetch as any).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse)
      })

      const result = await alertApi.list({ page: 1, size: 20 })

      expect(result).toEqual(mockResponse)
      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/alerts'),
        expect.any(Object)
      )
    })

    it('should fetch single alert', async () => {
      const mockAlert = {
        alertId: 'test-001',
        incidentId: 'inc-001',
        title: 'Test Alert',
        severity: 'P1' as const,
        status: 'active' as const,
        aiStatus: 'pending' as const,
        serviceId: 'test-service',
        source: 'test',
        startsAt: '2026-03-16T00:00:00Z',
        createdAt: '2026-03-16T00:00:00Z'
      }

      ;(global.fetch as any).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockAlert)
      })

      const result = await alertApi.get('test-001')

      expect(result).toEqual(mockAlert)
    })

    it('should acknowledge alert', async () => {
      ;(global.fetch as any).mockResolvedValueOnce({
        ok: true
      })

      await alertApi.acknowledge('test-001', 'Test reason')

      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/alerts/test-001/acknowledge'),
        expect.objectContaining({
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ reason: 'Test reason' })
        })
      )
    })
  })

  describe('incidentApi', () => {
    it('should fetch incidents list', async () => {
      const mockResponse = {
        items: [],
        total: 0
      }

      ;(global.fetch as any).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse)
      })

      const result = await incidentApi.list({ page: 1, size: 20 })

      expect(result).toEqual(mockResponse)
    })
  })

  describe('topologyApi', () => {
    it('should fetch topology data', async () => {
      const mockTopology = {
        serviceId: 'root',
        nodes: [],
        edges: [],
        depth: 2,
        direction: 'both' as const,
        impactAnalysis: {
          directDependencies: [],
          dependentServices: [],
          blastRadius: 0,
          unhealthyDependencies: [],
          riskLevel: 'low' as const
        },
        metadata: { totalNodes: 0, totalEdges: 0, timestamp: new Date().toISOString() }
      }

      ;(global.fetch as any).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockTopology)
      })

      const result = await topologyApi.get('root', { depth: 2 })

      expect(result).toEqual(mockTopology)
    })
  })

  describe('dashboardApi', () => {
    it('should fetch dashboard summary', async () => {
      const mockSummary = {
        activeAlerts: 5,
        p1Count: 1,
        p2Count: 2,
        analyzingCount: 3,
        analyzedToday: 10,
        avgAnalysisTime: 15.5,
        noiseReductionRate: 0.75
      }

      ;(global.fetch as any).mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockSummary)
      })

      const result = await dashboardApi.getSummary()

      expect(result).toEqual(mockSummary)
    })
  })
})
