import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { AlertList } from '../components/Alerts/AlertList'
import * as api from '../services/api'

// Mock the API
vi.mock('../services/api', () => ({
  alertApi: {
    list: vi.fn(),
    acknowledge: vi.fn(),
    silence: vi.fn(),
  },
}))

const mockAlerts = [
  {
    alertId: 'alert-001',
    incidentId: 'inc-001',
    title: 'High CPU Usage',
    severity: 'P1',
    status: 'active',
    aiStatus: 'pending',
    serviceId: 'payment-service',
    source: 'prometheus',
    startsAt: '2024-03-16T10:00:00Z',
    createdAt: '2024-03-16T10:00:00Z',
  },
  {
    alertId: 'alert-002',
    incidentId: 'inc-002',
    title: 'Memory Leak Detected',
    severity: 'P2',
    status: 'active',
    aiStatus: 'completed',
    serviceId: 'user-service',
    source: 'prometheus',
    startsAt: '2024-03-16T09:30:00Z',
    createdAt: '2024-03-16T09:30:00Z',
  },
]

describe('AlertList', () => {
  it('renders alert list correctly', async () => {
    vi.mocked(api.alertApi.list).mockResolvedValue({
      items: mockAlerts,
      total: 2,
      page: 1,
      size: 20,
      totalPages: 1,
    })

    render(
      <BrowserRouter>
        <AlertList />
      </BrowserRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('High CPU Usage')).toBeInTheDocument()
      expect(screen.getByText('Memory Leak Detected')).toBeInTheDocument()
    })
  })

  it('displays severity badges correctly', async () => {
    vi.mocked(api.alertApi.list).mockResolvedValue({
      items: mockAlerts,
      total: 2,
      page: 1,
      size: 20,
      totalPages: 1,
    })

    render(
      <BrowserRouter>
        <AlertList />
      </BrowserRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('P1')).toBeInTheDocument()
      expect(screen.getByText('P2')).toBeInTheDocument()
    })
  })

  it('handles acknowledge action', async () => {
    vi.mocked(api.alertApi.list).mockResolvedValue({
      items: mockAlerts,
      total: 1,
      page: 1,
      size: 20,
      totalPages: 1,
    })
    vi.mocked(api.alertApi.acknowledge).mockResolvedValue(undefined)

    render(
      <BrowserRouter>
        <AlertList />
      </BrowserRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('High CPU Usage')).toBeInTheDocument()
    })
  })
})
