import { useEffect, useCallback } from 'react'
import { useIncidentStore } from '../store/incidentStore'

export const useIncidents = (params: {
  page?: number
  size?: number
  status?: string
  serviceId?: string
} = {}) => {
  const {
    incidents,
    total,
    loading,
    error,
    fetchIncidents,
    selectIncident,
    fetchAnalysis,
    clearError
  } = useIncidentStore()

  useEffect(() => {
    fetchIncidents(params)
  }, [params.page, params.size, params.status, params.serviceId])

  const refetch = useCallback(() => {
    fetchIncidents(params)
  }, [params])

  return {
    incidents,
    total,
    loading,
    error,
    refetch,
    selectIncident,
    fetchAnalysis,
    clearError
  }
}
