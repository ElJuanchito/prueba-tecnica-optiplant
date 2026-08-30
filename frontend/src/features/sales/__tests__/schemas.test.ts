import { describe, expect, it } from 'vitest'
import {
  cancellationRequestSchema,
  registerSaleItemRequestSchema,
  registerSaleRequestSchema,
  saleDetailResponseSchema,
  salePageResponseSchema,
  saleQuerySchema,
} from '../schemas/sale.schema.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'
const VALID_UUID_3 = 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33'

describe('Sales Feature — Zod Schemas Test Suite', () => {
  describe('registerSaleItemRequestSchema', () => {
    it('validates a valid sale item request', () => {
      const valid = {
        productExternalId: VALID_UUID_1,
        quantity: 5,
        unitOfMeasureExternalId: VALID_UUID_2,
        discountPercent: 10,
      }
      expect(registerSaleItemRequestSchema.parse(valid)).toEqual(valid)
    })

    it('rejects non-positive quantity', () => {
      const invalid = {
        productExternalId: VALID_UUID_1,
        quantity: 0,
      }
      expect(() => registerSaleItemRequestSchema.parse(invalid)).toThrow()
    })

    it('rejects negative discountPercent or discountPercent > 100', () => {
      expect(() =>
        registerSaleItemRequestSchema.parse({
          productExternalId: VALID_UUID_1,
          quantity: 2,
          discountPercent: -5,
        }),
      ).toThrow()

      expect(() =>
        registerSaleItemRequestSchema.parse({
          productExternalId: VALID_UUID_1,
          quantity: 2,
          discountPercent: 105,
        }),
      ).toThrow()
    })
  })

  describe('registerSaleRequestSchema', () => {
    it('validates a complete sale request', () => {
      const valid = {
        customerName: 'Cliente Ejemplo',
        customerTaxId: '1790012345001',
        priceListExternalId: VALID_UUID_2,
        taxPercent: 15,
        notes: 'Facturar con guía de remisión',
        items: [
          {
            productExternalId: VALID_UUID_1,
            quantity: 3,
            discountPercent: 5,
          },
        ],
      }
      const parsed = registerSaleRequestSchema.parse(valid)
      expect(parsed.customerName).toBe('Cliente Ejemplo')
      expect(parsed.items).toHaveLength(1)
    })

    it('rejects empty customer name', () => {
      const invalid = {
        customerName: '   ',
        items: [{ productExternalId: VALID_UUID_1, quantity: 1 }],
      }
      expect(() => registerSaleRequestSchema.parse(invalid)).toThrow()
    })

    it('rejects empty items array', () => {
      const invalid = {
        customerName: 'Cliente Valido',
        items: [],
      }
      expect(() => registerSaleRequestSchema.parse(invalid)).toThrow()
    })
  })

  describe('cancellationRequestSchema', () => {
    it('validates non-empty cancellation reason', () => {
      const valid = { reason: 'Devolución de mercadería solicitada' }
      expect(cancellationRequestSchema.parse(valid).reason).toBe(
        'Devolución de mercadería solicitada',
      )
    })

    it('rejects blank cancellation reason', () => {
      expect(() =>
        cancellationRequestSchema.parse({ reason: '   ' }),
      ).toThrow()
    })
  })

  describe('saleDetailResponseSchema', () => {
    it('parses full sale detail response', () => {
      const payload = {
        externalId: VALID_UUID_1,
        invoiceNumber: 'VEN-2026-0001',
        status: 'COMPLETED',
        branch: { externalId: VALID_UUID_2, name: 'Sucursal Matriz' },
        soldBy: { externalId: VALID_UUID_3, username: 'cajero1' },
        priceList: {
          externalId: VALID_UUID_2,
          code: 'RETAIL',
          maxDiscountPercent: 25,
        },
        customerName: 'Juan Pérez',
        customerTaxId: '1234567890',
        subtotal: 100,
        discountAmount: 10,
        taxAmount: 13.5,
        totalAmount: 103.5,
        notes: 'Venta presencial',
        cancellationReason: null,
        createdAt: '2026-08-30T10:00:00Z',
        items: [
          {
            externalId: VALID_UUID_3,
            productExternalId: VALID_UUID_1,
            sku: 'PROD-01',
            name: 'Producto Uno',
            quantity: 2,
            listUnitPrice: 50,
            unitPrice: 45,
            discountPercent: 10,
            subtotal: 90,
          },
        ],
      }

      const parsed = saleDetailResponseSchema.parse(payload)
      expect(parsed.invoiceNumber).toBe('VEN-2026-0001')
      expect(parsed.status).toBe('COMPLETED')
      expect(parsed.totalAmount).toBe(103.5)
      expect(parsed.items).toHaveLength(1)
    })
  })

  describe('salePageResponseSchema', () => {
    it('parses paginated sales response with aggregates', () => {
      const payload = {
        content: [
          {
            externalId: VALID_UUID_1,
            invoiceNumber: 'VEN-2026-0001',
            status: 'COMPLETED',
            branch: { externalId: VALID_UUID_2, name: 'Sucursal Matriz' },
            soldBy: { externalId: VALID_UUID_3, username: 'cajero1' },
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

      const parsed = salePageResponseSchema.parse(payload)
      expect(parsed.content).toHaveLength(1)
      expect(parsed.aggregates.salesCount).toBe(1)
      expect(parsed.aggregates.totalAmount).toBe(103.5)
    })
  })

  describe('saleQuerySchema', () => {
    it('parses valid query parameters', () => {
      const params = {
        status: 'COMPLETED',
        from: '2026-08-01T00:00:00Z',
        to: '2026-08-31T23:59:59Z',
        page: 0,
        size: 20,
        sort: 'createdAt,desc',
      }
      expect(saleQuerySchema.parse(params)).toEqual(params)
    })
  })
})
