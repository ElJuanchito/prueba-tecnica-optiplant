import { describe, expect, it } from 'vitest'
import {
  adjustStockRequestSchema,
  branchAvailabilityResponseSchema,
  kardexLineResponseSchema,
  kardexPageResponseSchema,
  kardexQuerySchema,
  movementReceiptResponseSchema,
  networkAvailabilityResponseSchema,
  setThresholdRequestSchema,
  STOCK_MOVEMENT_TYPE,
  stockLineResponseSchema,
  stockMovementTypeSchema,
  stockPageResponseSchema,
  stockQuerySchema,
  thresholdResponseSchema,
  writeOffRequestSchema,
} from '../schemas/index.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'

describe('Inventory Zod Schemas', () => {
  describe('Stock Schemas', () => {
    it('validates a valid StockLineResponse', () => {
      const parsed = stockLineResponseSchema.parse({
        productExternalId: VALID_UUID_1,
        sku: 'FERT-UREA-46',
        name: 'Urea Granulada 46%',
        currentStock: 150,
        reservedStock: 10,
        inTransitStock: 25,
        availableStock: 140,
        minStockThreshold: 30,
        averageCost: 12.5,
        lastUpdatedAt: '2026-08-29T00:00:00Z',
      })

      expect(parsed.productExternalId).toBe(VALID_UUID_1)
      expect(parsed.sku).toBe('FERT-UREA-46')
      expect(parsed.availableStock).toBe(140)
    })

    it('validates paginated stock response', () => {
      const parsed = stockPageResponseSchema.parse({
        content: [
          {
            productExternalId: VALID_UUID_1,
            sku: 'FERT-01',
            name: 'Fertilizante',
            currentStock: 50,
            reservedStock: 0,
            inTransitStock: 0,
            availableStock: 50,
            minStockThreshold: 10,
            averageCost: 5.0,
            lastUpdatedAt: null,
          },
        ],
        totalElements: 1,
        page: 0,
        size: 20,
      })

      expect(parsed.totalElements).toBe(1)
      expect(parsed.content).toHaveLength(1)
    })

    it('validates stockQuerySchema with optional parameters', () => {
      const parsed = stockQuerySchema.parse({
        belowThreshold: true,
        sort: 'currentStock',
        page: 0,
        size: 15,
      })

      expect(parsed.belowThreshold).toBe(true)
      expect(parsed.sort).toBe('currentStock')
    })

    it('validates branchAvailabilityResponseSchema and networkAvailabilityResponseSchema', () => {
      const branch = branchAvailabilityResponseSchema.parse({
        branchExternalId: VALID_UUID_2,
        branchName: 'Sucursal Central',
        currentStock: 100,
        reservedStock: 10,
        inTransitStock: 0,
        availableStock: 90,
        isOwnBranch: true,
      })
      expect(branch.branchName).toBe('Sucursal Central')

      const parsed = networkAvailabilityResponseSchema.parse({
        productExternalId: VALID_UUID_1,
        sku: 'SKU-001',
        name: 'Product 1',
        branches: [branch],
        networkTotal: 100,
      })

      expect(parsed.networkTotal).toBe(100)
      expect(parsed.branches[0]?.isOwnBranch).toBe(true)
    })

    it('validates setThresholdRequestSchema with positive or zero value', () => {
      expect(setThresholdRequestSchema.parse({ minStockThreshold: 0 }).minStockThreshold).toBe(0)
      expect(setThresholdRequestSchema.parse({ minStockThreshold: 50 }).minStockThreshold).toBe(50)
      expect(() => setThresholdRequestSchema.parse({ minStockThreshold: -5 })).toThrow()
    })

    it('validates thresholdResponseSchema', () => {
      const parsed = thresholdResponseSchema.parse({
        productExternalId: VALID_UUID_1,
        minStockThreshold: 25,
      })
      expect(parsed.minStockThreshold).toBe(25)
    })
  })

  describe('Movement & Adjustment Schemas', () => {
    it('validates adjustStockRequestSchema and rejects empty reason', () => {
      const valid = adjustStockRequestSchema.parse({
        productExternalId: VALID_UUID_1,
        countedQuantity: 92,
        reason: '  Physical count annual audit  ',
      })
      expect(valid.countedQuantity).toBe(92)
      expect(valid.reason).toBe('Physical count annual audit')

      expect(() =>
        adjustStockRequestSchema.parse({
          productExternalId: VALID_UUID_1,
          countedQuantity: 92,
          reason: '   ',
        }),
      ).toThrow()

      expect(() =>
        adjustStockRequestSchema.parse({
          productExternalId: VALID_UUID_1,
          countedQuantity: -1,
          reason: 'Negative count',
        }),
      ).toThrow()
    })

    it('validates writeOffRequestSchema with strictly positive quantity', () => {
      const valid = writeOffRequestSchema.parse({
        productExternalId: VALID_UUID_1,
        quantity: 5,
        reason: 'Damaged during unloading',
      })
      expect(valid.quantity).toBe(5)

      expect(() =>
        writeOffRequestSchema.parse({
          productExternalId: VALID_UUID_1,
          quantity: 0,
          reason: 'Zero quantity',
        }),
      ).toThrow()

      expect(() =>
        writeOffRequestSchema.parse({
          productExternalId: VALID_UUID_1,
          quantity: 5,
          reason: '',
        }),
      ).toThrow()
    })

    it('validates movementReceiptResponseSchema', () => {
      const parsed = movementReceiptResponseSchema.parse({
        movementExternalId: VALID_UUID_1,
        movementType: STOCK_MOVEMENT_TYPE.ADJUSTMENT_NEG,
        quantity: 8,
        previousStock: 100,
        resultingStock: 92,
        createdAt: '2026-08-29T01:00:00Z',
      })

      expect(parsed.movementType).toBe('ADJUSTMENT_NEG')
      expect(parsed.resultingStock).toBe(92)
    })
  })

  describe('Kardex Schemas', () => {
    it('validates all 8 StockMovementType literals', () => {
      const validTypes = [
        'PURCHASE_RECEIPT',
        'SALE',
        'TRANSFER_OUT',
        'TRANSFER_IN',
        'ADJUSTMENT_POS',
        'ADJUSTMENT_NEG',
        'DAMAGE_WASTE',
        'INITIAL_LOAD',
      ]

      validTypes.forEach((type) => {
        expect(stockMovementTypeSchema.parse(type)).toBe(type)
      })

      expect(() => stockMovementTypeSchema.parse('INVALID_TYPE')).toThrow()
    })

    it('validates kardexLineResponseSchema', () => {
      const parsed = kardexLineResponseSchema.parse({
        externalId: VALID_UUID_1,
        productExternalId: VALID_UUID_2,
        movementType: 'SALE',
        quantity: 10,
        unitCost: 15.5,
        totalCost: 155.0,
        previousStock: 50,
        resultingStock: 40,
        referenceType: 'SALE_ORDER',
        referenceId: 'SO-1001',
        notes: 'Commercial sale',
        userExternalId: VALID_UUID_1,
        createdAt: '2026-08-29T00:00:00Z',
      })

      expect(parsed.movementType).toBe('SALE')
      expect(parsed.resultingStock).toBe(40)
    })

    it('validates kardexPageResponseSchema', () => {
      const line = kardexLineResponseSchema.parse({
        externalId: VALID_UUID_1,
        productExternalId: VALID_UUID_2,
        movementType: 'SALE',
        quantity: 10,
        unitCost: 15.5,
        totalCost: 155.0,
        previousStock: 50,
        resultingStock: 40,
        referenceType: 'SALE_ORDER',
        referenceId: 'SO-1001',
        notes: 'Commercial sale',
        userExternalId: VALID_UUID_1,
        createdAt: '2026-08-29T00:00:00Z',
      })

      const page = kardexPageResponseSchema.parse({
        content: [line],
        totalElements: 1,
        page: 0,
        size: 20,
      })

      expect(page.totalElements).toBe(1)
      expect(page.content[0]?.quantity).toBe(10)
    })

    it('validates kardexQuerySchema', () => {
      const parsed = kardexQuerySchema.parse({
        productExternalId: VALID_UUID_1,
        movementType: 'PURCHASE_RECEIPT',
        from: '2026-08-01T00:00:00Z',
        to: '2026-08-29T00:00:00Z',
        page: 0,
        size: 20,
      })

      expect(parsed.movementType).toBe('PURCHASE_RECEIPT')
      expect(parsed.page).toBe(0)
    })
  })
})
