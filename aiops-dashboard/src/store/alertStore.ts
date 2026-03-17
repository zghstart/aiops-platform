import { create } from 'zustand'
import type { Alert, AlertListResponse } from '../types'
import { alertApi } from '../services/api'

interface AlertState {
  alerts: Alert[]
  total: number
  loading: boolean
  error: string | null
  selectedAlert: Alert | null

  // Actions
  fetchAlerts: (params: {
    page?: number
    size?: number
    status?: string
    severity?: string
    serviceId?: string
  }) => Promise<void>
  selectAlert: (alert: Alert | null) => void
  acknowledgeAlert: (alertId: string, reason?: string) => Promise<void>
  silenceAlert: (alertId: string, durationMinutes: number, reason: string) => Promise<void>
  resolveAlert: (alertId: string, resolution: string) => Promise<void>
  clearError: () => void
}

export const useAlertStore = create<AlertState>((set, get) => ({
  alerts: [],
  total: 0,
  loading: false,
  error: null,
  selectedAlert: null,

  fetchAlerts: async (params) => {
    set({ loading: true, error: null })
    try {
      const response = await alertApi.list(params)
      set({
        alerts: response.items,
        total: response.total,
        loading: false
      })
    } catch (error) {
      set({
        error: error instanceof Error ? error.message : '获取告警列表失败',
        loading: false
      })
    }
  },

  selectAlert: (alert) => {
    set({ selectedAlert: alert })
  },

  acknowledgeAlert: async (alertId, reason) => {
    try {
      await alertApi.acknowledge(alertId, reason)
      // 更新本地状态
      const { alerts } = get()
      set({
        alerts: alerts.map(a =>
          a.alertId === alertId
            ? { ...a, status: 'acknowledged' as const }
            : a
        )
      })
    } catch (error) {
      set({
        error: error instanceof Error ? error.message : '确认告警失败'
      })
      throw error
    }
  },

  silenceAlert: async (alertId, durationMinutes, reason) => {
    try {
      await alertApi.silence(alertId, durationMinutes, reason)
      const { alerts } = get()
      set({
        alerts: alerts.map(a =>
          a.alertId === alertId
            ? { ...a, status: 'suppressed' as const }
            : a
        )
      })
    } catch (error) {
      set({
        error: error instanceof Error ? error.message : '静默告警失败'
      })
      throw error
    }
  },

  resolveAlert: async (alertId, resolution) => {
    try {
      await alertApi.resolve(alertId, resolution)
      const { alerts } = get()
      set({
        alerts: alerts.map(a =>
          a.alertId === alertId
            ? { ...a, status: 'resolved' as const }
            : a
        )
      })
    } catch (error) {
      set({
        error: error instanceof Error ? error.message : '解决告警失败'
      })
      throw error
    }
  },

  clearError: () => {
    set({ error: null })
  },
})))
