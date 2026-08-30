import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiClientModule from '@/lib/api-client.ts'
import { customerService } from '../services/customer.service.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'
const VALID_UUID_3 = 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33'

describe('Customer Service Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  const mockCustomerResponse = {
    externalId: VALID_UUID_1,
    name: 'Agrícola San Pedro S.A.',
    taxId: '1790012345001',
    email: 'contacto@sanpedro.com',
    phone: '+593 99 123 4567',
    address: 'Km 14.5 Vía a Daule',
    active: true,
    createdAt: '2026-08-30T10:00:00Z',
    updatedAt: '2026-08-30T10:00:00Z',
  }

  it('calls POST /api/sales/customers and creates a customer (CU-VEN-05)', async () => {
    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockCustomerResponse)

    const result = await customerService.create({
      name: 'Agrícola San Pedro S.A.',
      taxId: '1790012345001',
      email: 'contacto@sanpedro.com',
      phone: '+593 99 123 4567',
      address: 'Km 14.5 Vía a Daule',
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      '/api/sales/customers',
      expect.objectContaining({
        method: 'POST',
      }),
    )
    expect(result.externalId).toBe(VALID_UUID_1)
    expect(result.name).toBe('Agrícola San Pedro S.A.')
    expect(result.active).toBe(true)
  })

  it('calls GET /api/sales/customers with search and pagination (CU-VEN-05)', async () => {
    const mockPageResponse = {
      content: [mockCustomerResponse],
      totalElements: 1,
      page: 0,
      size: 15,
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockPageResponse)

    const result = await customerService.list({
      search: 'San Pedro',
      active: true,
      page: 0,
      size: 15,
      sort: 'name,asc',
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      '/api/sales/customers?search=San+Pedro&active=true&sort=name%2Casc&page=0&size=15',
      { method: 'GET' },
    )
    expect(result.content).toHaveLength(1)
    expect(result.totalElements).toBe(1)
  })

  it('calls GET /api/sales/customers/{externalId} (CU-VEN-05)', async () => {
    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockCustomerResponse)

    const result = await customerService.get(VALID_UUID_1)

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/sales/customers/${VALID_UUID_1}`,
      { method: 'GET' },
    )
    expect(result.externalId).toBe(VALID_UUID_1)
  })

  it('calls PUT /api/sales/customers/{externalId} (CU-VEN-05)', async () => {
    const updatedResponse = {
      ...mockCustomerResponse,
      name: 'Agrícola San Pedro Updated S.A.',
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(updatedResponse)

    const result = await customerService.edit(VALID_UUID_1, {
      name: 'Agrícola San Pedro Updated S.A.',
      taxId: '1790012345001',
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/sales/customers/${VALID_UUID_1}`,
      expect.objectContaining({
        method: 'PUT',
      }),
    )
    expect(result.name).toBe('Agrícola San Pedro Updated S.A.')
  })

  it('calls PATCH /api/sales/customers/{externalId}/disable (R-C4)', async () => {
    const disabledResponse = {
      ...mockCustomerResponse,
      active: false,
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(disabledResponse)

    const result = await customerService.disable(VALID_UUID_1)

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/sales/customers/${VALID_UUID_1}/disable`,
      { method: 'PATCH' },
    )
    expect(result.active).toBe(false)
  })

  it('calls PATCH /api/sales/customers/{externalId}/enable (R-C4)', async () => {
    const enabledResponse = {
      ...mockCustomerResponse,
      active: true,
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(enabledResponse)

    const result = await customerService.enable(VALID_UUID_1)

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/sales/customers/${VALID_UUID_1}/enable`,
      { method: 'PATCH' },
    )
    expect(result.active).toBe(true)
  })

  it('calls GET /api/sales/customers/{externalId}/sales (CU-VEN-06)', async () => {
    const mockHistoryResponse = {
      content: [
        {
          externalId: VALID_UUID_2,
          invoiceNumber: 'VEN-2026-0001',
          status: 'COMPLETED',
          branch: { externalId: VALID_UUID_3, name: 'Sucursal Matriz' },
          soldBy: { externalId: VALID_UUID_3, username: 'vendedor1' },
          priceList: null,
          customerName: 'Agrícola San Pedro S.A.',
          totalAmount: 250.0,
          createdAt: '2026-08-30T10:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 20,
      aggregates: {
        salesCount: 1,
        totalAmount: 250.0,
      },
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockHistoryResponse)

    const result = await customerService.getSalesHistory(VALID_UUID_1, {
      status: 'COMPLETED',
      page: 0,
      size: 20,
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/sales/customers/${VALID_UUID_1}/sales?status=COMPLETED&page=0&size=20`,
      { method: 'GET' },
    )
    expect(result.content).toHaveLength(1)
    expect(result.aggregates.salesCount).toBe(1)
    expect(result.aggregates.totalAmount).toBe(250.0)
  })
})
