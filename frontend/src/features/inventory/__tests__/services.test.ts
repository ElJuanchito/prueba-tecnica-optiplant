import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiClientModule from '@/lib/api-client.ts'
import { inventoryService } from '../services/inventory.service.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'

describe('Inventory Service Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('calls GET /api/inventory/stock with query parameters and parses result', async () => {
    const mockResponse = {
      content: [
        {
          productExternalId: VALID_UUID_1,
          sku: 'FERT-01',
          name: 'Fertilizante',
          currentStock: 100,
          reservedStock: 10,
          inTransitStock: 5,
          availableStock: 90,
          minStockThreshold: 20,
          averageCost: 15.0,
          lastUpdatedAt: '2026-08-29T00:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 20,
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockResponse)

    const result = await inventoryService.listStock({
      belowThreshold: true,
      sort: 'currentStock',
      page: 0,
      size: 20,
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      '/api/inventory/stock?belowThreshold=true&sort=currentStock&page=0&size=20',
      { method: 'GET' },
    )
    expect(result.content[0]?.sku).toBe('FERT-01')
    expect(result.totalElements).toBe(1)
  })

  it('calls GET /api/inventory/stock/{productExternalId}/network and parses response', async () => {
    const mockResponse = {
      productExternalId: VALID_UUID_1,
      sku: 'FERT-01',
      name: 'Fertilizante',
      branches: [
        {
          branchExternalId: VALID_UUID_2,
          branchName: 'Norte',
          currentStock: 50,
          reservedStock: 0,
          inTransitStock: 0,
          availableStock: 50,
          isOwnBranch: true,
        },
      ],
      networkTotal: 50,
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockResponse)

    const result = await inventoryService.getNetworkAvailability(VALID_UUID_1)

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/inventory/stock/${VALID_UUID_1}/network`,
      { method: 'GET' },
    )
    expect(result.networkTotal).toBe(50)
  })

  it('calls POST /api/inventory/adjustments and parses receipt', async () => {
    const mockReceipt = {
      movementExternalId: VALID_UUID_1,
      movementType: 'ADJUSTMENT_NEG',
      quantity: 8,
      previousStock: 100,
      resultingStock: 92,
      createdAt: '2026-08-29T00:00:00Z',
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockReceipt)

    const result = await inventoryService.adjustStock({
      productExternalId: VALID_UUID_2,
      countedQuantity: 92,
      reason: 'Physical inventory audit',
    })

    expect(apiClientSpy).toHaveBeenCalledWith('/api/inventory/adjustments', {
      method: 'POST',
      body: JSON.stringify({
        productExternalId: VALID_UUID_2,
        countedQuantity: 92,
        reason: 'Physical inventory audit',
      }),
    })
    expect(result.resultingStock).toBe(92)
  })

  it('calls POST /api/inventory/write-offs and parses receipt', async () => {
    const mockReceipt = {
      movementExternalId: VALID_UUID_1,
      movementType: 'DAMAGE_WASTE',
      quantity: 3,
      previousStock: 50,
      resultingStock: 47,
      createdAt: '2026-08-29T00:00:00Z',
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockReceipt)

    const result = await inventoryService.writeOffStock({
      productExternalId: VALID_UUID_2,
      quantity: 3,
      reason: 'Expired product',
    })

    expect(apiClientSpy).toHaveBeenCalledWith('/api/inventory/write-offs', {
      method: 'POST',
      body: JSON.stringify({
        productExternalId: VALID_UUID_2,
        quantity: 3,
        reason: 'Expired product',
      }),
    })
    expect(result.resultingStock).toBe(47)
  })

  it('calls PUT /api/inventory/stock/{productExternalId}/threshold and parses response', async () => {
    const mockResponse = {
      productExternalId: VALID_UUID_1,
      minStockThreshold: 40,
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockResponse)

    const result = await inventoryService.setThreshold(VALID_UUID_1, {
      minStockThreshold: 40,
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      `/api/inventory/stock/${VALID_UUID_1}/threshold`,
      {
        method: 'PUT',
        body: JSON.stringify({ minStockThreshold: 40 }),
      },
    )
    expect(result.minStockThreshold).toBe(40)
  })

  it('calls GET /api/inventory/kardex and parses ledger movements', async () => {
    const mockKardexPage = {
      content: [
        {
          externalId: VALID_UUID_1,
          productExternalId: VALID_UUID_2,
          movementType: 'PURCHASE_RECEIPT',
          quantity: 100,
          unitCost: 10.0,
          totalCost: 1000.0,
          previousStock: 0,
          resultingStock: 100,
          referenceType: 'PO',
          referenceId: 'PO-101',
          notes: 'Supplier shipment',
          userExternalId: VALID_UUID_1,
          createdAt: '2026-08-29T00:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 20,
    }

    const apiClientSpy = vi
      .spyOn(apiClientModule, 'apiClient')
      .mockResolvedValueOnce(mockKardexPage)

    const result = await inventoryService.listKardex({
      movementType: 'PURCHASE_RECEIPT',
      page: 0,
      size: 20,
    })

    expect(apiClientSpy).toHaveBeenCalledWith(
      '/api/inventory/kardex?movementType=PURCHASE_RECEIPT&page=0&size=20',
      { method: 'GET' },
    )
    expect(result.content[0]?.resultingStock).toBe(100)
  })
})
