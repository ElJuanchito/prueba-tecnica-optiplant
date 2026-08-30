import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiClientModule from '@/lib/api-client.ts'
import { saleService } from '../services/sale.service.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'
const VALID_UUID_3 = 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33'

describe('Sales Service Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  const mockDetailResponse = {
    externalId: VALID_UUID_1,
    invoiceNumber: 'VEN-2026-0001',
    status: 'COMPLETED',
    branch: { externalId: VALID_UUID_2, name: 'Sucursal Matriz' },
    soldBy: { externalId: VALID_UUID_3, username: 'vendedor1' },
    priceList: {
      externalId: VALID_UUID_2,
      code: 'RETAIL',
      maxDiscountPercent: 20,
    },
    customerName: 'Juan Pérez',
    customerTaxId: '1790012345001',
    subtotal: 100,
    discountAmount: 10,
    taxAmount: 13.5,
    totalAmount: 103.5,
    notes: 'Venta de mostrador',
    cancellationReason: null,
    createdAt: '2026-08-30T10:00:00Z',
    items: [
      {
        externalId: VALID_UUID_1,
        productExternalId: VALID_UUID_2,
        sku: 'FERT-01',
        name: 'Fertilizante NPK',
        quantity: 2,
        listUnitPrice: 50,
        unitPrice: 45,
        discountPercent: 10,
        subtotal: 90,
      },
    ],
  }

  it('calls POST /api/sales and registers a sale (CU-VEN-01)', async () => {
    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockDetailResponse)

    const result = await saleService.register({
      customerName: 'Juan Pérez',
      customerTaxId: '1790012345001',
      priceListExternalId: VALID_UUID_2,
      taxPercent: 15,
      notes: 'Venta de mostrador',
      items: [
        {
          productExternalId: VALID_UUID_2,
          quantity: 2,
          discountPercent: 10,
        },
      ],
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      '/api/sales',
      expect.objectContaining({
        method: 'POST',
      }),
    )
    expect(result.invoiceNumber).toBe('VEN-2026-0001')
    expect(result.totalAmount).toBe(103.5)
  })

  it('calls GET /api/sales with query parameters (CU-VEN-02)', async () => {
    const mockPageResponse = {
      content: [
        {
          externalId: VALID_UUID_1,
          invoiceNumber: 'VEN-2026-0001',
          status: 'COMPLETED',
          branch: { externalId: VALID_UUID_2, name: 'Sucursal Matriz' },
          soldBy: { externalId: VALID_UUID_3, username: 'vendedor1' },
          priceList: null,
          customerName: 'Juan Pérez',
          totalAmount: 103.5,
          createdAt: '2026-08-30T10:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 10,
      aggregates: {
        salesCount: 1,
        totalAmount: 103.5,
      },
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockPageResponse)

    const result = await saleService.list({
      status: 'COMPLETED',
      page: 0,
      size: 10,
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      '/api/sales?status=COMPLETED&page=0&size=10',
      { method: 'GET' },
    )
    expect(result.content).toHaveLength(1)
    expect(result.aggregates.salesCount).toBe(1)
  })

  it('calls GET /api/sales/{externalId} (CU-VEN-03)', async () => {
    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockDetailResponse)

    const result = await saleService.getDetail(VALID_UUID_1)

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/sales/${VALID_UUID_1}`,
      { method: 'GET' },
    )
    expect(result.externalId).toBe(VALID_UUID_1)
  })

  it('calls GET /api/sales/by-invoice/{invoiceNumber} (CU-VEN-03)', async () => {
    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockDetailResponse)

    const result = await saleService.getByInvoiceNumber('VEN-2026-0001')

    expect(apiClientSpy).toHaveBeenCalledWith(
      '/api/sales/by-invoice/VEN-2026-0001',
      { method: 'GET' },
    )
    expect(result.invoiceNumber).toBe('VEN-2026-0001')
  })

  it('calls POST /api/sales/{externalId}/cancellation (CU-VEN-04)', async () => {
    const cancelledResponse = {
      ...mockDetailResponse,
      status: 'CANCELLED',
      cancellationReason: 'Devolución solicitada',
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(cancelledResponse)

    const result = await saleService.cancel(VALID_UUID_1, {
      reason: 'Devolución solicitada',
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/sales/${VALID_UUID_1}/cancellation`,
      {
        method: 'POST',
        body: JSON.stringify({ reason: 'Devolución solicitada' }),
      },
    )
    expect(result.status).toBe('CANCELLED')
    expect(result.cancellationReason).toBe('Devolución solicitada')
  })
})
