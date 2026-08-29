import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/test-utils.tsx'
import { AlertCenter } from '../components/AlertCenter.tsx'
import { alertService } from '../services/alert.service.ts'
import type { AlertResponse } from '../types/index.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'

const mockAlerts: AlertResponse[] = [
  {
    externalId: VALID_UUID_1,
    alertType: 'STOCK_MINIMUM',
    severity: 'CRITICAL',
    title: 'STOCK_MINIMUM:a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    message: 'Stock fell below minimum threshold (0 remaining)',
    isResolved: false,
    resolvedAt: null,
    createdAt: '2026-08-29T00:00:00Z',
  },
  {
    externalId: VALID_UUID_2,
    alertType: 'LOGISTIC_DELAY',
    severity: 'WARNING',
    title: 'LOGISTIC_DELAY:TR-2026-004',
    message: 'Transfer from Norte delayed by 48 hours',
    isResolved: false,
    resolvedAt: null,
    createdAt: '2026-08-29T01:00:00Z',
  },
]

describe('AlertCenter Component Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('renders active operational alerts with severity badges and titles', async () => {
    vi.spyOn(alertService, 'listAlerts').mockResolvedValueOnce({
      content: mockAlerts,
      totalElements: 2,
      page: 0,
      size: 15,
    })

    renderWithProviders(<AlertCenter currentActorRole="BRANCH_MANAGER" />)

    expect(
      await screen.findByText('Operational Alert Management Center'),
    ).toBeInTheDocument()
    expect(
      await screen.findByText('Stock fell below minimum threshold (0 remaining)'),
    ).toBeInTheDocument()
    expect(
      screen.getByText('Transfer from Norte delayed by 48 hours'),
    ).toBeInTheDocument()
    expect(screen.getAllByText('CRITICAL').length).toBeGreaterThan(0)
    expect(screen.getAllByText('WARNING').length).toBeGreaterThan(0)
    expect(screen.getAllByRole('button', { name: /Mark Resolved/i })).toHaveLength(2)
  })

  it('allows BRANCH_MANAGER to resolve an alert', async () => {
    const user = userEvent.setup()

    vi.spyOn(alertService, 'listAlerts').mockResolvedValue({
      content: [mockAlerts[0]!],
      totalElements: 1,
      page: 0,
      size: 15,
    })

    const resolveSpy = vi.spyOn(alertService, 'resolveAlert').mockResolvedValueOnce({
      ...mockAlerts[0]!,
      isResolved: true,
      resolvedAt: '2026-08-29T02:00:00Z',
    })

    renderWithProviders(<AlertCenter currentActorRole="BRANCH_MANAGER" />)

    const resolveBtn = await screen.findByRole('button', { name: /Mark Resolved/i })
    await user.click(resolveBtn)

    await waitFor(() => {
      expect(resolveSpy).toHaveBeenCalledWith(VALID_UUID_1)
    })
  })

  it('displays all-clear empty state when no active alerts exist', async () => {
    vi.spyOn(alertService, 'listAlerts').mockResolvedValueOnce({
      content: [],
      totalElements: 0,
      page: 0,
      size: 15,
    })

    renderWithProviders(<AlertCenter currentActorRole="BRANCH_MANAGER" />)

    expect(
      await screen.findByText('All Clear — No Active Operational Alerts'),
    ).toBeInTheDocument()
  })
})
