import { useEffect, useCallback } from 'react'
import { useAlertStore } from '../store/alertStore'

export const useAlerts = (params: {
  page?: number
  size?: number
  status?: string
  severity?: string
  serviceId?: string
} = {}) => {
  const {
    alerts,
    total,
    loading,
    error,
    fetchAlerts,
    acknowledgeAlert,
    silenceAlert,
    resolveAlert,
    clearError
  } = useAlertStore()

  useEffect(() => {
    fetchAlerts(params)
  }, [params.page, params.size, params.status, params.severity, params.serviceId])

  const refetch = useCallback(() => {
    fetchAlerts(params)
  }, [params])

  return {
    alerts,
    total,
    loading,
    error,
    refetch,
    acknowledgeAlert,
    silenceAlert,
    resolveAlert,
    clearError
  }
}
