import { create } from 'zustand'
import type { Incident, AnalysisResult } from '../types'
import { incidentApi, aiApi } from '../services/api'

interface IncidentState {
  incidents: Incident[]
  total: number
  loading: boolean
  error: string | null
  selectedIncident: Incident | null
  analysisResult: AnalysisResult | null

  // Actions
  fetchIncidents: (params: {
    page?: number
    size?: number
    status?: string
    serviceId?: string
  }) => Promise<void>
  selectIncident: (incident: Incident | null) => Promise<void>
  fetchAnalysis: (incidentId: string) => Promise<void>
  clearError: () => void
}

export const useIncidentStore = create<IncidentState>((set, get) => ({
  incidents: [],
  total: 0,
  loading: false,
  error: null,
  selectedIncident: null,
  analysisResult: null,

  fetchIncidents: async (params) => {
    set({ loading: true, error: null })
    try {
      const response = await incidentApi.list(params)
      set({
        incidents: response.items,
        total: response.total,
        loading: false
      })
    } catch (error) {
      set({
        error: error instanceof Error ? error.message : '获取事件列表失败',
        loading: false
      })
    }
  },

  selectIncident: async (incident) => {
    set({ selectedIncident: incident, analysisResult: null })
    if (incident?.id) {
      // 自动获取分析结果
      try {
        const result = await aiApi.getResult(incident.id)
        set({ analysisResult: result })
      } catch (error) {
        console.error('Failed to fetch analysis result:', error)
      }
    }
  },

  fetchAnalysis: async (incidentId) => {
    try {
      const result = await aiApi.getResult(incidentId)
      set({ analysisResult: result })
    } catch (error) {
      set({
        error: error instanceof Error ? error.message : '获取分析结果失败'
      })
    }
  },

  clearError: () => {
    set({ error: null })
  },
})))
