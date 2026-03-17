import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { IncidentList } from '../pages/IncidentList'

// Mock the API
vi.mock('../services/api', () => ({
  incidentApi: {
    list: vi.fn(() => Promise.resolve({
      items: [],
      total: 0
    }))
  }
}))

describe('IncidentList', () => {
  it('renders incident list page', () => {
    render(
      <BrowserRouter>
        <IncidentList />
      </BrowserRouter>
    )

    // Add assertions based on component implementation
    expect(screen.getByText('事件列表')).toBeDefined()
  })
})
