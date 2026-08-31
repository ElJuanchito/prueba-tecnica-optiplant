import { describe, expect, it } from 'vitest'
import {
  branchPerformanceResponseSchema,
  corporateBoardPageResponseSchema,
  corporateBoardQuerySchema,
  monthlySalesResponseSchema,
  replenishmentLineResponseSchema,
  replenishmentPageResponseSchema,
  replenishmentQuerySchema,
  rotationLineResponseSchema,
  rotationPageResponseSchema,
  rotationQuerySchema,
  salesTrendQuerySchema,
  salesTrendResponseSchema,
  transferActivityQuerySchema,
  transferActivitySummaryResponseSchema,
  transferStatusCountsResponseSchema,
  transferStockImpactPageResponseSchema,
  transferStockImpactQuerySchema,
  transferStockImpactResponseSchema,
} from '../schemas/analytics.schema.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'

describe('Analytics Feature — Zod Schemas Test Suite', () => {
  // --- 1. Sales Trend ---
  describe('Sales Trend Schemas (RF-DSH-01)', () => {
    it('validates a valid monthly sales item', () => {
      const item = {
        year: 2026,
        month: 8,
        salesCount: 145,
        unitsSold: 520.5,
        totalAmount: 12500.75,
      }
      expect(monthlySalesResponseSchema.parse(item)).toEqual(item)
    })

    it('validates a valid sales trend response with MoM variation', () => {
      const response = {
        branchExternalId: VALID_UUID_1,
        months: [
          {
            year: 2026,
            month: 7,
            salesCount: 120,
            unitsSold: 400.0,
            totalAmount: 10000.0,
          },
          {
            year: 2026,
            month: 8,
            salesCount: 145,
            unitsSold: 520.5,
            totalAmount: 12500.75,
          },
        ],
        monthOverMonthVariationPercent: 25.01,
        empty: false,
      }
      expect(salesTrendResponseSchema.parse(response)).toEqual(response)
    })

    it('validates a sales trend response with null variation and empty state', () => {
      const response = {
        branchExternalId: null,
        months: [],
        monthOverMonthVariationPercent: null,
        empty: true,
      }
      expect(salesTrendResponseSchema.parse(response)).toEqual(response)
    })

    it('validates sales trend query params', () => {
      expect(salesTrendQuerySchema.parse({ months: 4 })).toEqual({ months: 4 })
      expect(
        salesTrendQuerySchema.parse({
          months: 12,
          branchExternalId: VALID_UUID_1,
        }),
      ).toEqual({ months: 12, branchExternalId: VALID_UUID_1 })
    })

    it('rejects months out of range (1..12)', () => {
      expect(() => salesTrendQuerySchema.parse({ months: 0 })).toThrow()
      expect(() => salesTrendQuerySchema.parse({ months: 13 })).toThrow()
    })
  })

  // --- 2. Product Rotation / Pareto ABC ---
  describe('Product Rotation & ABC Schemas (RF-DSH-02)', () => {
    it('validates a valid rotation line item', () => {
      const line = {
        productExternalId: VALID_UUID_1,
        sku: 'FERT-001',
        name: 'Fertilizante Foliar 1L',
        unitsSold: 350.0,
        salesAmount: 8750.0,
        sharePercent: 35.0,
        cumulativeSharePercent: 35.0,
        abcClass: 'A' as const,
        coverageDays: 14.5,
      }
      expect(rotationLineResponseSchema.parse(line)).toEqual(line)
    })

    it('validates rotation line with null or zero coverage days', () => {
      const lineZeroStock = {
        productExternalId: VALID_UUID_1,
        sku: 'SEED-002',
        name: 'Semilla de Maíz Híbrido',
        unitsSold: 100.0,
        salesAmount: 4000.0,
        sharePercent: 16.0,
        cumulativeSharePercent: 51.0,
        abcClass: 'A' as const,
        coverageDays: 0,
      }
      expect(rotationLineResponseSchema.parse(lineZeroStock)).toEqual(
        lineZeroStock,
      )

      const lineNoDemand = {
        productExternalId: VALID_UUID_2,
        sku: 'TOOL-003',
        name: 'Tijera de Podar Profesional',
        unitsSold: 0,
        salesAmount: 0,
        sharePercent: 0,
        cumulativeSharePercent: 100.0,
        abcClass: 'C' as const,
        coverageDays: null,
      }
      expect(rotationLineResponseSchema.parse(lineNoDemand)).toEqual(
        lineNoDemand,
      )
    })

    it('validates paginated rotation response', () => {
      const page = {
        content: [
          {
            productExternalId: VALID_UUID_1,
            sku: 'FERT-001',
            name: 'Fertilizante Foliar 1L',
            unitsSold: 350.0,
            salesAmount: 8750.0,
            sharePercent: 35.0,
            cumulativeSharePercent: 35.0,
            abcClass: 'A' as const,
            coverageDays: 14.5,
          },
        ],
        totalElements: 1,
        page: 0,
        size: 20,
      }
      expect(rotationPageResponseSchema.parse(page)).toEqual(page)
    })

    it('validates rotation query params', () => {
      const valid = {
        from: '2026-08-01T00:00:00Z',
        to: '2026-08-31T23:59:59Z',
        direction: 'TOP' as const,
        branchExternalId: VALID_UUID_1,
        page: 0,
        size: 50,
      }
      expect(rotationQuerySchema.parse(valid)).toEqual(valid)
    })

    it('rejects invalid rotation direction or invalid page size', () => {
      expect(() =>
        rotationQuerySchema.parse({ direction: 'INVALID' }),
      ).toThrow()
      expect(() => rotationQuerySchema.parse({ size: 101 })).toThrow()
      expect(() => rotationQuerySchema.parse({ size: 0 })).toThrow()
    })
  })

  // --- 3. Active Transfers Activity & Stock Impact ---
  describe('Active Transfers & Stock Impact Schemas (RF-DSH-03)', () => {
    it('validates transfer status counts and activity summary', () => {
      const counts = {
        requested: 2,
        inPreparation: 1,
        inTransit: 3,
      }
      expect(transferStatusCountsResponseSchema.parse(counts)).toEqual(counts)

      const summary = {
        inbound: counts,
        outbound: {
          requested: 0,
          inPreparation: 2,
          inTransit: 1,
        },
        delayedCount: 1,
      }
      expect(transferActivitySummaryResponseSchema.parse(summary)).toEqual(
        summary,
      )

      expect(
        transferActivityQuerySchema.parse({ branchExternalId: VALID_UUID_1 }),
      ).toEqual({ branchExternalId: VALID_UUID_1 })
    })

    it('validates transfer stock impact line and page', () => {
      const item = {
        productExternalId: VALID_UUID_1,
        sku: 'FERT-001',
        name: 'Fertilizante Foliar 1L',
        currentStock: 150.0,
        inTransitStock: 50.0,
        inboundInTransit: 50.0,
        outboundCommitted: 20.0,
        projectedStock: 180.0,
      }
      expect(transferStockImpactResponseSchema.parse(item)).toEqual(item)

      const page = {
        content: [item],
        totalElements: 1,
        page: 0,
        size: 20,
      }
      expect(transferStockImpactPageResponseSchema.parse(page)).toEqual(page)
    })

    it('validates transfer impact query params', () => {
      expect(
        transferStockImpactQuerySchema.parse({
          branchExternalId: VALID_UUID_1,
          page: 1,
          size: 20,
        }),
      ).toEqual({
        branchExternalId: VALID_UUID_1,
        page: 1,
        size: 20,
      })
    })
  })

  // --- 4. Critical Replenishment Panel ---
  describe('Critical Replenishment Schemas (RF-DSH-04)', () => {
    it('validates replenishment line with OUT_OF_STOCK and CRITICAL severity and page', () => {
      const outOfStock = {
        productExternalId: VALID_UUID_1,
        sku: 'FERT-001',
        name: 'Fertilizante Foliar 1L',
        currentStock: 0,
        minStockThreshold: 50.0,
        severity: 'OUT_OF_STOCK' as const,
        coverageDays: 0,
      }
      expect(replenishmentLineResponseSchema.parse(outOfStock)).toEqual(
        outOfStock,
      )

      const critical = {
        productExternalId: VALID_UUID_2,
        sku: 'INSECT-002',
        name: 'Insecticida Orgánico 500ml',
        currentStock: 15.0,
        minStockThreshold: 30.0,
        severity: 'CRITICAL' as const,
        coverageDays: 4.2,
      }
      expect(replenishmentLineResponseSchema.parse(critical)).toEqual(critical)

      const page = {
        content: [outOfStock, critical],
        totalElements: 2,
        page: 0,
        size: 20,
      }
      expect(replenishmentPageResponseSchema.parse(page)).toEqual(page)
    })

    it('validates replenishment query params and sort keys', () => {
      expect(
        replenishmentQuerySchema.parse({
          severity: 'OUT_OF_STOCK',
          sort: 'severity',
          branchExternalId: VALID_UUID_1,
          page: 0,
          size: 20,
        }),
      ).toEqual({
        severity: 'OUT_OF_STOCK',
        sort: 'severity',
        branchExternalId: VALID_UUID_1,
        page: 0,
        size: 20,
      })

      expect(
        replenishmentQuerySchema.parse({
          sort: 'product',
        }),
      ).toEqual({ sort: 'product' })

      expect(
        replenishmentQuerySchema.parse({
          sort: 'coverage',
        }),
      ).toEqual({ sort: 'coverage' })
    })

    it('rejects invalid sort key or severity', () => {
      expect(() =>
        replenishmentQuerySchema.parse({ sort: 'invalid_sort' as any }),
      ).toThrow()
      expect(() =>
        replenishmentQuerySchema.parse({ severity: 'MEDIUM' as any }),
      ).toThrow()
    })
  })

  // --- 5. Corporate Comparative Board ---
  describe('Corporate Board Schemas (RF-DSH-05)', () => {
    it('validates branch performance response item and page', () => {
      const branchPerf = {
        branchExternalId: VALID_UUID_1,
        code: 'SUC-01',
        name: 'Sucursal Matriz Quito',
        salesAmount: 85400.5,
        salesCount: 650,
        unitsSold: 3200.0,
        inventoryValue: 120500.0,
        criticalProductCount: 4,
        activeTransferCount: 2,
      }
      expect(branchPerformanceResponseSchema.parse(branchPerf)).toEqual(
        branchPerf,
      )

      const page = {
        content: [branchPerf],
        totalElements: 1,
        page: 0,
        size: 20,
      }
      expect(corporateBoardPageResponseSchema.parse(page)).toEqual(page)
    })

    it('validates corporate board query params', () => {
      const query = {
        year: 2026,
        month: 8,
        sort: 'salesAmount',
        direction: 'DESC' as const,
        page: 0,
        size: 20,
      }
      expect(corporateBoardQuerySchema.parse(query)).toEqual(query)
    })

    it('rejects invalid month (must be 1..12)', () => {
      expect(() => corporateBoardQuerySchema.parse({ month: 0 })).toThrow()
      expect(() => corporateBoardQuerySchema.parse({ month: 13 })).toThrow()
    })
  })
})
