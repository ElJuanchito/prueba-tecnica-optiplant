import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiClientModule from '@/lib/api-client.ts'
import { logisticsService } from '../services/logistics.service.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'

describe('Logistics Service Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  const mockRouteResponse = {
    externalId: VALID_UUID_1,
    originBranch: { externalId: VALID_UUID_1, name: 'Sucursal Norte' },
    destinationBranch: { externalId: VALID_UUID_2, name: 'Sucursal Sur' },
    estimatedDurationHours: 12.5,
    transportCost: 150.0,
    priorityLevel: 'STANDARD',
    active: true,
    createdAt: '2026-08-29T00:00:00Z',
  }

  it('calls POST /api/logistics/routes (CU-LOG-01)', async () => {
    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockRouteResponse)

    const result = await logisticsService.createRoute({
      originBranchExternalId: VALID_UUID_1,
      destinationBranchExternalId: VALID_UUID_2,
      estimatedDurationHours: 12.5,
      transportCost: 150.0,
      priorityLevel: 'STANDARD',
    })

    expect(apiClientSpy).toHaveBeenCalledWith('/api/logistics/routes', {
      method: 'POST',
      body: JSON.stringify({
        originBranchExternalId: VALID_UUID_1,
        destinationBranchExternalId: VALID_UUID_2,
        estimatedDurationHours: 12.5,
        transportCost: 150.0,
        priorityLevel: 'STANDARD',
      }),
    })
    expect(result.estimatedDurationHours).toBe(12.5)
  })

  it('calls GET /api/logistics/routes with query parameters', async () => {
    const mockPageResponse = {
      content: [mockRouteResponse],
      totalElements: 1,
      page: 0,
      size: 10,
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockPageResponse)

    const result = await logisticsService.listRoutes({
      active: true,
      page: 0,
      size: 10,
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      '/api/logistics/routes?active=true&page=0&size=10',
      { method: 'GET' },
    )
    expect(result.content[0]?.active).toBe(true)
  })

  it('calls PUT /api/logistics/routes/{externalId}', async () => {
    const updated = {
      ...mockRouteResponse,
      estimatedDurationHours: 15.0,
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(updated)

    const result = await logisticsService.updateRoute(VALID_UUID_1, {
      estimatedDurationHours: 15.0,
      transportCost: 180.0,
      priorityLevel: 'URGENT',
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/logistics/routes/${VALID_UUID_1}`,
      {
        method: 'PUT',
        body: JSON.stringify({
          estimatedDurationHours: 15.0,
          transportCost: 180.0,
          priorityLevel: 'URGENT',
        }),
      },
    )
    expect(result.estimatedDurationHours).toBe(15.0)
  })

  it('calls PATCH /api/logistics/routes/{externalId}/deactivation', async () => {
    const deactivated = {
      ...mockRouteResponse,
      active: false,
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(deactivated)

    const result = await logisticsService.deactivateRoute(VALID_UUID_1)

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/logistics/routes/${VALID_UUID_1}/deactivation`,
      { method: 'PATCH' },
    )
    expect(result.active).toBe(false)
  })

  it('calls GET /api/logistics/transfers/active (CU-LOG-02)', async () => {
    const mockMonitorPage = {
      content: [
        {
          transferExternalId: VALID_UUID_1,
          transferNumber: 'TRF-2026-0001',
          status: 'IN_TRANSIT',
          originBranch: { externalId: VALID_UUID_1, name: 'Norte' },
          destinationBranch: { externalId: VALID_UUID_2, name: 'Sur' },
          priority: 'URGENT',
          itemCount: 2,
          totalQuantity: 40,
          estimatedArrivalAt: '2026-08-29T18:00:00Z',
          isDelayed: true,
        },
      ],
      totalElements: 1,
      page: 0,
      size: 10,
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockMonitorPage)

    const result = await logisticsService.listActiveTransfers({
      status: 'IN_TRANSIT',
      delayed: true,
      page: 0,
      size: 10,
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      '/api/logistics/transfers/active?status=IN_TRANSIT&delayed=true&page=0&size=10',
      { method: 'GET' },
    )
    expect(result.content[0]?.isDelayed).toBe(true)
  })

  it('calls GET /api/logistics/compliance (CU-LOG-03)', async () => {
    const mockCompliancePage = {
      content: [
        {
          key: 'ROUTE_1_2',
          label: 'Sucursal Norte → Sucursal Sur',
          deliveredCount: 10,
          onTimeCount: 9,
          onTimePercentage: 90.0,
          averageDeviationHours: 0.5,
          unmeasuredCount: 1,
        },
      ],
      totalElements: 1,
      page: 0,
      size: 10,
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockCompliancePage)

    const result = await logisticsService.getComplianceReport({
      from: '2026-08-01T00:00:00Z',
      to: '2026-08-29T23:59:59Z',
      groupBy: 'ROUTE',
      page: 0,
      size: 10,
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      '/api/logistics/compliance?from=2026-08-01T00%3A00%3A00Z&to=2026-08-29T23%3A59%3A59Z&groupBy=ROUTE&page=0&size=10',
      { method: 'GET' },
    )
    expect(result.content[0]?.onTimePercentage).toBe(90.0)
  })
})
