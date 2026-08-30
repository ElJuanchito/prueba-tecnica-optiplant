import { describe, expect, it } from 'vitest'
import {
  closePriceRequestSchema,
  createPriceListRequestSchema,
  priceListPageResponseSchema,
  priceListResponseSchema,
  pricePageResponseSchema,
  priceResponseSchema,
  quoteRequestSchema,
  quoteResponseSchema,
  setPriceRequestSchema,
  updatePriceListRequestSchema,
} from '../schemas/pricing.schema.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'

describe('Pricing Feature — Zod Schemas Test Suite', () => {
  describe('createPriceListRequestSchema', () => {
    it('validates a valid create price list request', () => {
      const valid = {
        code: 'DISTRIB-01',
        name: 'Lista Distribuidores',
        description: 'Precios mayoristas para distribuidores',
        maxDiscountPercent: 25.5,
      }
      expect(createPriceListRequestSchema.parse(valid)).toEqual(valid)
    })

    it('rejects empty code or name', () => {
      expect(() =>
        createPriceListRequestSchema.parse({
          code: '   ',
          name: 'Lista',
          maxDiscountPercent: 10,
        }),
      ).toThrow()

      expect(() =>
        createPriceListRequestSchema.parse({
          code: 'DISTRIB',
          name: '',
          maxDiscountPercent: 10,
        }),
      ).toThrow()
    })

    it('rejects maxDiscountPercent < 0 or > 100', () => {
      expect(() =>
        createPriceListRequestSchema.parse({
          code: 'DISTRIB',
          name: 'Lista',
          maxDiscountPercent: -5,
        }),
      ).toThrow()

      expect(() =>
        createPriceListRequestSchema.parse({
          code: 'DISTRIB',
          name: 'Lista',
          maxDiscountPercent: 105,
        }),
      ).toThrow()
    })
  })

  describe('updatePriceListRequestSchema', () => {
    it('validates an update request', () => {
      const valid = {
        name: 'Nuevo Nombre',
        description: 'Nueva descripción',
        maxDiscountPercent: 30,
      }
      expect(updatePriceListRequestSchema.parse(valid)).toEqual(valid)
    })
  })

  describe('setPriceRequestSchema', () => {
    it('validates a set price request', () => {
      const valid = {
        productExternalId: VALID_UUID_1,
        branchExternalId: VALID_UUID_2,
        unitPrice: 45.99,
        validFrom: '2026-09-01',
      }
      expect(setPriceRequestSchema.parse(valid)).toEqual(valid)
    })

    it('rejects non-positive unitPrice', () => {
      expect(() =>
        setPriceRequestSchema.parse({
          productExternalId: VALID_UUID_1,
          unitPrice: 0,
        }),
      ).toThrow()
    })
  })

  describe('closePriceRequestSchema', () => {
    it('validates a close price request', () => {
      expect(
        closePriceRequestSchema.parse({ validTo: '2026-12-31' }),
      ).toEqual({ validTo: '2026-12-31' })
      expect(closePriceRequestSchema.parse({})).toEqual({})
    })
  })

  describe('quoteRequestSchema', () => {
    it('validates a quote request with items', () => {
      const valid = {
        priceListExternalId: VALID_UUID_1,
        items: [
          {
            productExternalId: VALID_UUID_2,
            quantity: 10,
            discountPercent: 5,
          },
        ],
      }
      expect(quoteRequestSchema.parse(valid)).toEqual(valid)
    })

    it('rejects quote request with empty items', () => {
      expect(() =>
        quoteRequestSchema.parse({
          items: [],
        }),
      ).toThrow()
    })
  })

  describe('quoteResponseSchema', () => {
    it('parses calculated quote response', () => {
      const payload = {
        code: 'RETAIL',
        maxDiscountPercent: 20,
        items: [
          {
            productExternalId: VALID_UUID_1,
            listUnitPrice: 100,
            unitPrice: 90,
            subtotal: 900,
          },
        ],
      }
      expect(quoteResponseSchema.parse(payload)).toEqual(payload)
    })
  })

  describe('priceListResponseSchema & priceListPageResponseSchema', () => {
    it('parses paginated price list response', () => {
      const payload = {
        content: [
          {
            externalId: VALID_UUID_1,
            code: 'RETAIL',
            name: 'Lista Minorista',
            description: 'Mostrador',
            maxDiscountPercent: 15,
            isDefault: true,
            active: true,
            createdAt: '2026-08-30T10:00:00Z',
          },
        ],
        totalElements: 1,
        page: 0,
        size: 10,
      }
      expect(priceListPageResponseSchema.parse(payload).content).toHaveLength(1)
      expect(priceListResponseSchema.parse(payload.content[0])).toBeDefined()
    })
  })

  describe('priceResponseSchema & pricePageResponseSchema', () => {
    it('parses paginated price response', () => {
      const payload = {
        content: [
          {
            externalId: VALID_UUID_1,
            priceListExternalId: VALID_UUID_1,
            productExternalId: VALID_UUID_2,
            branchExternalId: null,
            unitPrice: 29.99,
            validFrom: '2026-08-01',
            validTo: null,
            createdAt: '2026-08-30T10:00:00Z',
          },
        ],
        totalElements: 1,
        page: 0,
        size: 10,
      }
      expect(pricePageResponseSchema.parse(payload).content).toHaveLength(1)
      expect(priceResponseSchema.parse(payload.content[0])).toBeDefined()
    })
  })
})
