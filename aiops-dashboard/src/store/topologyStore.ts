import { create } from 'zustand'
import type { TopologyData } from '../types'
import { topologyApi } from '../services/api'

interface TopologyState {
  topologyData: TopologyData | null
  loading: boolean
  error: string | null
  selectedNodeId: string | null

  // Actions
  fetchTopology: (serviceId: string, params?: {
    depth?: number
    direction?: string
  }) => Promise<void>
  selectNode: (nodeId: string | null) => void
  clearError: () => void
}

export const useTopologyStore = create<TopologyState>((set) => ({
  topologyData: null,
  loading: false,
  error: null,
  selectedNodeId: null,

  fetchTopology: async (serviceId, params = {}) => {
    set({ loading: true, error: null })
    try {
      const data = await topologyApi.get(serviceId, params)
      set({
        topologyData: data,
        loading: false
      })
    } catch (error) {
      set({
        error: error instanceof Error ? error.message : '获取拓扑数据失败',
        loading: false
      })
    }
  },

  selectNode: (nodeId) => {
    set({ selectedNodeId: nodeId })
  },

  clearError: () => {
    set({ error: null })
  },
})))
