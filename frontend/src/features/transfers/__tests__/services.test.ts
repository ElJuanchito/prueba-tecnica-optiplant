import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiClientModule from '@/lib/api-client.ts'
import { transferService } from '../services/transfer.service.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'
const VALID_UUID_3 = 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33'

describe('Transfer Service Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  const mockDetailResponse = {
    externalId: VALID_UUID_1,
    transferNumber: 'TRF-2026-0001',
    status: 'REQUESTED',
    priority: 'STANDARD',
    originBranch: { externalId: VALID_UUID_2, name: 'Sucursal Norte' },
    destinationBranch: { externalId: VALID_UUID_3, name: 'Sucursal Sur' },
    carrierName: null,
    trackingNumber: null,
    dispatchedAt: null,
    estimatedArrivalAt: null,
    actualArrivalAt: null,
    deviationHours: null,
    observations: [],
    requestedBy: VALID_UUID_1,
    dispatchedBy: null,
    receivedBy: null,
    createdAt: '2026-08-29T10:00:00Z',
    updatedAt: null,
    items: [
      {
        externalId: VALID_UUID_1,
        productExternalId: VALID_UUID_2,
        sku: 'FERT-01',
        name: 'Fertilizante',
        requestedQuantity: 20,
        dispatchedQuantity: null,
        receivedQuantity: null,
        discrepancyQuantity: null,
        discrepancyReason: null,
      },
    ],
  }

  it('calls POST /api/transfers and parses transfer detail (CU-TRA-01)', async () => {
    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockDetailResponse)

    const result = await transferService.request({
      originBranchExternalId: VALID_UUID_2,
      priority: 'STANDARD',
      notes: 'Initial request',
      items: [{ productExternalId: VALID_UUID_2, requestedQuantity: 20 }],
    })

    expect(apiClientSpy).toHaveBeenCalledWith('/api/transfers', {
      method: 'POST',
      body: JSON.stringify({
        originBranchExternalId: VALID_UUID_2,
        priority: 'STANDARD',
        notes: 'Initial request',
        items: [{ productExternalId: VALID_UUID_2, requestedQuantity: 20 }],
      }),
    })
    expect(result.transferNumber).toBe('TRF-2026-0001')
    expect(result.status).toBe('REQUESTED')
  })

  it('calls GET /api/transfers with query parameters and parses list', async () => {
    const mockPageResponse = {
      content: [
        {
          externalId: VALID_UUID_1,
          transferNumber: 'TRF-2026-0001',
          status: 'REQUESTED',
          priority: 'STANDARD',
          originBranch: { externalId: VALID_UUID_2, name: 'Sucursal Norte' },
          destinationBranch: { externalId: VALID_UUID_3, name: 'Sucursal Sur' },
          createdAt: '2026-08-29T10:00:00Z',
          estimatedArrivalAt: null,
        },
      ],
      totalElements: 1,
      page: 0,
      size: 20,
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockPageResponse)

    const result = await transferService.list({
      status: 'REQUESTED',
      direction: 'INBOUND',
      page: 0,
      size: 20,
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      '/api/transfers?status=REQUESTED&direction=INBOUND&page=0&size=20',
      { method: 'GET' },
    )
    expect(result.content[0]?.transferNumber).toBe('TRF-2026-0001')
  })

  it('calls GET /api/transfers/{externalId} and parses detail', async () => {
    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockDetailResponse)

    const result = await transferService.getDetail(VALID_UUID_1)

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/transfers/${VALID_UUID_1}`,
      {
        method: 'GET',
      },
    )
    expect(result.externalId).toBe(VALID_UUID_1)
  })

  it('calls POST /api/transfers/{externalId}/approval (CU-TRA-02)', async () => {
    const approvedResponse = {
      ...mockDetailResponse,
      status: 'IN_PREPARATION',
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(approvedResponse)

    const result = await transferService.approve(VALID_UUID_1, {
      items: [{ itemExternalId: VALID_UUID_1, approvedQuantity: 15 }],
      notes: 'Approved 15 units',
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/transfers/${VALID_UUID_1}/approval`,
      {
        method: 'POST',
        body: JSON.stringify({
          items: [{ itemExternalId: VALID_UUID_1, approvedQuantity: 15 }],
          notes: 'Approved 15 units',
        }),
      },
    )
    expect(result.status).toBe('IN_PREPARATION')
  })

  it('calls POST /api/transfers/{externalId}/rejection (CU-TRA-02)', async () => {
    const rejectedResponse = {
      ...mockDetailResponse,
      status: 'CANCELLED',
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(rejectedResponse)

    const result = await transferService.reject(VALID_UUID_1, {
      reason: 'No stock available',
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/transfers/${VALID_UUID_1}/rejection`,
      {
        method: 'POST',
        body: JSON.stringify({ reason: 'No stock available' }),
      },
    )
    expect(result.status).toBe('CANCELLED')
  })

  it('calls POST /api/transfers/{externalId}/dispatch (CU-TRA-03)', async () => {
    const dispatchedResponse = {
      ...mockDetailResponse,
      status: 'IN_TRANSIT',
      carrierName: 'DHL Express',
      trackingNumber: 'DHL-12345',
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(dispatchedResponse)

    const result = await transferService.dispatch(VALID_UUID_1, {
      carrierName: 'DHL Express',
      trackingNumber: 'DHL-12345',
      items: [{ itemExternalId: VALID_UUID_1, dispatchedQuantity: 20 }],
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/transfers/${VALID_UUID_1}/dispatch`,
      {
        method: 'POST',
        body: JSON.stringify({
          carrierName: 'DHL Express',
          trackingNumber: 'DHL-12345',
          items: [{ itemExternalId: VALID_UUID_1, dispatchedQuantity: 20 }],
        }),
      },
    )
    expect(result.status).toBe('IN_TRANSIT')
  })

  it('calls POST /api/transfers/{externalId}/receipt (CU-TRA-04 & CU-TRA-05)', async () => {
    const receivedResponse = {
      ...mockDetailResponse,
      status: 'RECEIVED_WITH_DISCREPANCY',
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(receivedResponse)

    const result = await transferService.receive(VALID_UUID_1, {
      items: [
        {
          itemExternalId: VALID_UUID_1,
          receivedQuantity: 18,
          discrepancyReason: 'Damaged item in transport',
        },
      ],
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/transfers/${VALID_UUID_1}/receipt`,
      {
        method: 'POST',
        body: JSON.stringify({
          items: [
            {
              itemExternalId: VALID_UUID_1,
              receivedQuantity: 18,
              discrepancyReason: 'Damaged item in transport',
            },
          ],
        }),
      },
    )
    expect(result.status).toBe('RECEIVED_WITH_DISCREPANCY')
  })

  it('calls POST /api/transfers/{externalId}/cancellation (CU-TRA-06)', async () => {
    const cancelledResponse = {
      ...mockDetailResponse,
      status: 'CANCELLED',
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(cancelledResponse)

    const result = await transferService.cancel(VALID_UUID_1, {
      reason: 'Requested by mistake',
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/transfers/${VALID_UUID_1}/cancellation`,
      {
        method: 'POST',
        body: JSON.stringify({ reason: 'Requested by mistake' }),
      },
    )
    expect(result.status).toBe('CANCELLED')
  })
})
