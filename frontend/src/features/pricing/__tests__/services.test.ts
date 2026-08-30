import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiClientModule from '@/lib/api-client.ts'
import { pricingService } from '../services/pricing.service.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'

describe('Pricing Service Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  const mockPriceList = {
    externalId: VALID_UUID_1,
    code: 'RETAIL',
    name: 'Lista General Minorista',
    description: 'Ventas mostrador',
    maxDiscountPercent: 20,
    isDefault: true,
    active: true,
    createdAt: '2026-08-30T10:00:00Z',
    updatedAt: null,
  }

  const mockPrice = {
    externalId: VALID_UUID_1,
    priceListExternalId: VALID_UUID_1,
    productExternalId: VALID_UUID_2,
    branchExternalId: null,
    unitPrice: 50.0,
    validFrom: '2026-08-30',
    validTo: null,
    createdAt: '2026-08-30T10:00:00Z',
  }

  describe('priceList management', () => {
    it('creates a price list via POST /api/pricing/price-lists', async () => {
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockPriceList)

      const result = await pricingService.createPriceList({
        code: 'RETAIL',
        name: 'Lista General Minorista',
        description: 'Ventas mostrador',
        maxDiscountPercent: 20,
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        '/api/pricing/price-lists',
        expect.objectContaining({
          method: 'POST',
        }),
      )
      expect(result.code).toBe('RETAIL')
    })

    it('lists price lists via GET /api/pricing/price-lists', async () => {
      const mockPage = {
        content: [mockPriceList],
        totalElements: 1,
        page: 0,
        size: 10,
      }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockPage)

      const result = await pricingService.listPriceLists({ page: 0, size: 10 })

      expect(apiClientSpy).toHaveBeenCalledWith(
        '/api/pricing/price-lists?page=0&size=10',
        { method: 'GET' },
      )
      expect(result.content).toHaveLength(1)
    })

    it('updates a price list via PUT /api/pricing/price-lists/{externalId}', async () => {
      const updated = { ...mockPriceList, name: 'Nombre Actualizado' }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(updated)

      const result = await pricingService.updatePriceList(VALID_UUID_1, {
        name: 'Nombre Actualizado',
        maxDiscountPercent: 25,
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/pricing/price-lists/${VALID_UUID_1}`,
        expect.objectContaining({
          method: 'PUT',
        }),
      )
      expect(result.name).toBe('Nombre Actualizado')
    })

    it('deactivates a price list via PATCH /api/pricing/price-lists/{externalId}/deactivation', async () => {
      const deactivated = { ...mockPriceList, active: false }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(deactivated)

      const result = await pricingService.deactivatePriceList(VALID_UUID_1)

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/pricing/price-lists/${VALID_UUID_1}/deactivation`,
        {
          method: 'PATCH',
        },
      )
      expect(result.active).toBe(false)
    })
  })

  describe('price management', () => {
    it('sets a price via POST /api/pricing/price-lists/{id}/prices', async () => {
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockPrice)

      const result = await pricingService.setPrice(VALID_UUID_1, {
        productExternalId: VALID_UUID_2,
        unitPrice: 50.0,
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/pricing/price-lists/${VALID_UUID_1}/prices`,
        expect.objectContaining({
          method: 'POST',
        }),
      )
      expect(result.unitPrice).toBe(50.0)
    })

    it('lists prices via GET /api/pricing/price-lists/{id}/prices', async () => {
      const mockPricePage = {
        content: [mockPrice],
        totalElements: 1,
        page: 0,
        size: 10,
      }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockPricePage)

      const result = await pricingService.listPrices(VALID_UUID_1, {
        currentOnly: true,
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/pricing/price-lists/${VALID_UUID_1}/prices?currentOnly=true`,
        { method: 'GET' },
      )
      expect(result.content).toHaveLength(1)
    })

    it('closes price validity via PATCH /api/pricing/prices/{id}/closure', async () => {
      const closedPrice = { ...mockPrice, validTo: '2026-12-31' }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(closedPrice)

      const result = await pricingService.closePrice(VALID_UUID_1, {
        validTo: '2026-12-31',
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/pricing/prices/${VALID_UUID_1}/closure`,
        expect.objectContaining({
          method: 'PATCH',
        }),
      )
      expect(result.validTo).toBe('2026-12-31')
    })
  })

  describe('pricing quote', () => {
    it('calculates a quote via POST /api/pricing/quotes', async () => {
      const mockQuoteResponse = {
        code: 'RETAIL',
        maxDiscountPercent: 20,
        items: [
          {
            productExternalId: VALID_UUID_2,
            listUnitPrice: 50.0,
            unitPrice: 45.0,
            subtotal: 90.0,
          },
        ],
      }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockQuoteResponse)

      const result = await pricingService.quote({
        priceListExternalId: VALID_UUID_1,
        items: [{ productExternalId: VALID_UUID_2, quantity: 2, discountPercent: 10 }],
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        '/api/pricing/quotes',
        expect.objectContaining({
          method: 'POST',
        }),
      )
      expect(result.items[0]?.subtotal).toBe(90.0)
    })
  })
})
