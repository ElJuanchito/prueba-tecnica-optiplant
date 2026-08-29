import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiClientModule from '@/lib/api-client.ts'
import { alertService } from '../services/alert.service.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'

describe('Notification Alert Service Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('calls GET /api/notifications/alerts and parses page response', async () => {
    const mockPage = {
      content: [
        {
          externalId: VALID_UUID_1,
          alertType: 'STOCK_MINIMUM',
          severity: 'CRITICAL',
          title: 'STOCK_MINIMUM:item-1',
          message: 'Zero stock remaining',
          isResolved: false,
          resolvedAt: null,
          createdAt: '2026-08-29T00:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 20,
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockPage)

    const result = await alertService.listAlerts({
      resolved: false,
      severity: 'CRITICAL',
      alertType: 'STOCK_MINIMUM',
      page: 0,
      size: 20,
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      '/api/notifications/alerts?resolved=false&alertType=STOCK_MINIMUM&severity=CRITICAL&page=0&size=20',
      { method: 'GET' },
    )
    expect(result.content[0]?.severity).toBe('CRITICAL')
  })

  it('calls PATCH /api/notifications/alerts/{externalId}/resolve and parses resolved alert', async () => {
    const mockResolved = {
      externalId: VALID_UUID_1,
      alertType: 'STOCK_MINIMUM',
      severity: 'CRITICAL',
      title: 'STOCK_MINIMUM:item-1',
      message: 'Zero stock remaining',
      isResolved: true,
      resolvedAt: '2026-08-29T01:00:00Z',
      createdAt: '2026-08-29T00:00:00Z',
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockResolved)

    const result = await alertService.resolveAlert(VALID_UUID_1)

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/notifications/alerts/${VALID_UUID_1}/resolve`,
      { method: 'PATCH' },
    )
    expect(result.isResolved).toBe(true)
    expect(result.resolvedAt).toBe('2026-08-29T01:00:00Z')
  })
})
