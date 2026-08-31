import { describe, expect, it } from 'vitest'
import {
  cancellationRequestSchema,
  costHistoryItemResponseSchema,
  costHistoryPageResponseSchema,
  createPurchaseOrderItemRequestSchema,
  createPurchaseOrderRequestSchema,
  createSupplierRequestSchema,
  purchaseOrderDetailResponseSchema,
  purchaseOrderItemResponseSchema,
  purchaseOrderPageResponseSchema,
  registerReceptionItemRequestSchema,
  registerReceptionRequestSchema,
  supplierPageResponseSchema,
  supplierResponseSchema,
  updatePurchaseOrderRequestSchema,
  updateSupplierRequestSchema,
} from '../schemas/purchases.schema.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'
const VALID_UUID_3 = 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33'

describe('Purchases Feature — Zod Schemas Test Suite', () => {
  // --- Suppliers ---
  describe('createSupplierRequestSchema', () => {
    it('validates a valid create supplier request', () => {
      const valid = {
        taxId: '1790012345001',
        name: 'AgroQuímica del Norte S.A.',
        contactName: 'Ing. Carlos Mendoza',
        email: 'ventas@agronorte.com',
        phone: '+593 99 123 4567',
        address: 'Av. Panamericana Km 12',
      }
      expect(createSupplierRequestSchema.parse(valid)).toEqual(valid)
    })

    it('rejects empty name or taxId', () => {
      expect(() =>
        createSupplierRequestSchema.parse({
          taxId: '   ',
          name: 'Proveedor',
        }),
      ).toThrow()

      expect(() =>
        createSupplierRequestSchema.parse({
          taxId: '1790012345001',
          name: '',
        }),
      ).toThrow()
    })

    it('rejects invalid email format', () => {
      expect(() =>
        createSupplierRequestSchema.parse({
          taxId: '1790012345001',
          name: 'Proveedor',
          email: 'not-an-email',
        }),
      ).toThrow()
    })
  })

  describe('updateSupplierRequestSchema', () => {
    it('validates a valid update supplier request', () => {
      const valid = {
        name: 'Nuevo Nombre Proveedor',
        contactName: 'Ing. Laura Silva',
        email: 'laura@proveedor.com',
        phone: '+593 98 765 4321',
        address: 'Nueva Dirección 123',
      }
      expect(updateSupplierRequestSchema.parse(valid)).toEqual(valid)
    })

    it('rejects empty name on update', () => {
      expect(() =>
        updateSupplierRequestSchema.parse({
          name: '   ',
        }),
      ).toThrow()
    })
  })

  // --- Purchase Order Items & Orders ---
  describe('createPurchaseOrderItemRequestSchema', () => {
    it('validates a valid line item request', () => {
      const valid = {
        productExternalId: VALID_UUID_1,
        quantity: 50,
        unitOfMeasureExternalId: VALID_UUID_2,
        unitCost: 15.5,
        discountPercent: 5.0,
      }
      expect(createPurchaseOrderItemRequestSchema.parse(valid)).toEqual(valid)
    })

    it('rejects non-positive quantity', () => {
      expect(() =>
        createPurchaseOrderItemRequestSchema.parse({
          productExternalId: VALID_UUID_1,
          quantity: 0,
          unitCost: 10,
        }),
      ).toThrow()

      expect(() =>
        createPurchaseOrderItemRequestSchema.parse({
          productExternalId: VALID_UUID_1,
          quantity: -5,
          unitCost: 10,
        }),
      ).toThrow()
    })

    it('rejects negative unit cost', () => {
      expect(() =>
        createPurchaseOrderItemRequestSchema.parse({
          productExternalId: VALID_UUID_1,
          quantity: 10,
          unitCost: -1.5,
        }),
      ).toThrow()
    })

    it('rejects discountPercent out of 0..100 range', () => {
      expect(() =>
        createPurchaseOrderItemRequestSchema.parse({
          productExternalId: VALID_UUID_1,
          quantity: 10,
          unitCost: 10,
          discountPercent: -1,
        }),
      ).toThrow()

      expect(() =>
        createPurchaseOrderItemRequestSchema.parse({
          productExternalId: VALID_UUID_1,
          quantity: 10,
          unitCost: 10,
          discountPercent: 101,
        }),
      ).toThrow()
    })
  })

  describe('createPurchaseOrderRequestSchema & updatePurchaseOrderRequestSchema', () => {
    it('validates a valid order request with line items', () => {
      const valid = {
        supplierExternalId: VALID_UUID_1,
        paymentTerms: '30 días contra entrega',
        notes: 'Entregar en bodega principal',
        items: [
          {
            productExternalId: VALID_UUID_2,
            quantity: 100,
            unitCost: 10.0,
            discountPercent: 0,
          },
        ],
      }
      expect(createPurchaseOrderRequestSchema.parse(valid)).toEqual(valid)
      expect(updatePurchaseOrderRequestSchema.parse(valid)).toEqual(valid)
    })

    it('rejects order with empty items array', () => {
      expect(() =>
        createPurchaseOrderRequestSchema.parse({
          supplierExternalId: VALID_UUID_1,
          items: [],
        }),
      ).toThrow()
    })
  })

  // --- Cancellation ---
  describe('cancellationRequestSchema', () => {
    it('validates a valid non-blank cancellation reason', () => {
      const valid = { reason: 'Proveedor sin disponibilidad de stock' }
      expect(cancellationRequestSchema.parse(valid)).toEqual(valid)
    })

    it('rejects empty or whitespace-only reason', () => {
      expect(() => cancellationRequestSchema.parse({ reason: '' })).toThrow()

      expect(() =>
        cancellationRequestSchema.parse({ reason: '    ' }),
      ).toThrow()
    })
  })

  // --- Reception ---
  describe('registerReceptionRequestSchema', () => {
    it('validates a valid reception request', () => {
      const valid = {
        notes: 'Guía de remisión GR-2026-999',
        items: [
          {
            itemExternalId: VALID_UUID_1,
            receivedQuantity: 60,
          },
        ],
      }
      expect(registerReceptionRequestSchema.parse(valid)).toEqual(valid)
    })

    it('rejects reception item with negative received quantity', () => {
      expect(() =>
        registerReceptionItemRequestSchema.parse({
          itemExternalId: VALID_UUID_1,
          receivedQuantity: -10,
        }),
      ).toThrow()
    })

    it('rejects reception with empty items array', () => {
      expect(() =>
        registerReceptionRequestSchema.parse({
          items: [],
        }),
      ).toThrow()
    })
  })

  // --- Response Shapes ---
  describe('supplierResponseSchema & supplierPageResponseSchema', () => {
    it('parses paginated supplier response', () => {
      const payload = {
        content: [
          {
            externalId: VALID_UUID_1,
            taxId: '1790012345001',
            name: 'AgroNorte S.A.',
            contactName: 'Carlos Mendoza',
            email: 'ventas@agronorte.com',
            phone: '+593 99 123 4567',
            address: 'Av. Panamericana Km 12',
            active: true,
            createdAt: '2026-08-30T10:00:00Z',
            updatedAt: null,
          },
        ],
        totalElements: 1,
        page: 0,
        size: 10,
      }
      expect(supplierPageResponseSchema.parse(payload).content).toHaveLength(1)
      expect(supplierResponseSchema.parse(payload.content[0])).toBeDefined()
    })
  })

  describe('purchaseOrderDetailResponseSchema & purchaseOrderPageResponseSchema', () => {
    it('parses purchase order detail response', () => {
      const payload = {
        externalId: VALID_UUID_1,
        orderNumber: 'OC-2026-0001',
        status: 'PENDING',
        branch: { externalId: VALID_UUID_2, name: 'Sucursal Matriz' },
        supplier: {
          externalId: VALID_UUID_3,
          taxId: '1790012345001',
          name: 'AgroNorte S.A.',
        },
        createdBy: { externalId: VALID_UUID_2, username: 'operator1' },
        paymentTerms: '30 días',
        totalAmount: 950.0,
        notes: 'Despacho urgente',
        cancellationReason: null,
        createdAt: '2026-08-30T10:00:00Z',
        updatedAt: null,
        receivedAt: null,
        items: [
          {
            externalId: VALID_UUID_1,
            productExternalId: VALID_UUID_2,
            sku: 'FERT-01',
            name: 'Fertilizante Foliar 1L',
            orderedQuantity: 100,
            receivedQuantity: 0,
            pendingQuantity: 100,
            unitCost: 10.0,
            discountPercent: 5.0,
            effectiveUnitCost: 9.5,
            subtotal: 950.0,
          },
        ],
      }
      const parsed = purchaseOrderDetailResponseSchema.parse(payload)
      expect(parsed.orderNumber).toBe('OC-2026-0001')
      expect(parsed.items).toHaveLength(1)
      expect(parsed.items[0]?.effectiveUnitCost).toBe(9.5)
      expect(
        purchaseOrderItemResponseSchema.parse(payload.items[0]),
      ).toBeDefined()
    })

    it('parses paginated purchase order summary response', () => {
      const payload = {
        content: [
          {
            externalId: VALID_UUID_1,
            orderNumber: 'OC-2026-0001',
            status: 'APPROVED',
            branch: { externalId: VALID_UUID_2, name: 'Sucursal Matriz' },
            supplier: {
              externalId: VALID_UUID_3,
              taxId: '1790012345001',
              name: 'AgroNorte S.A.',
            },
            totalAmount: 950.0,
            createdAt: '2026-08-30T10:00:00Z',
            receivedAt: null,
          },
        ],
        totalElements: 1,
        page: 0,
        size: 15,
      }
      expect(
        purchaseOrderPageResponseSchema.parse(payload).content,
      ).toHaveLength(1)
    })
  })

  describe('costHistoryItemResponseSchema & costHistoryPageResponseSchema', () => {
    it('parses agreed cost history response', () => {
      const payload = {
        content: [
          {
            orderExternalId: VALID_UUID_1,
            orderNumber: 'OC-2026-0001',
            supplier: { externalId: VALID_UUID_2, name: 'AgroNorte S.A.' },
            unitCost: 20.0,
            discountPercent: 10.0,
            effectiveUnitCost: 18.0,
            quantity: 50,
            orderedAt: '2026-08-20T10:00:00Z',
            receivedAt: '2026-08-25T14:30:00Z',
          },
        ],
        totalElements: 1,
        page: 0,
        size: 15,
      }
      const parsed = costHistoryPageResponseSchema.parse(payload)
      expect(parsed.content).toHaveLength(1)
      expect(parsed.content[0]?.effectiveUnitCost).toBe(18.0)
      expect(
        costHistoryItemResponseSchema.parse(payload.content[0]),
      ).toBeDefined()
    })
  })
})
