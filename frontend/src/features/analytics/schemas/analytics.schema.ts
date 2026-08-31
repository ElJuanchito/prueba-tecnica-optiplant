import { z } from 'zod'
import {
  paginatedResponseSchema,
  uuidSchema,
} from '@/features/iam/schemas/common.schema.ts'

// --- Enums and Constants ---

export const ABC_CLASS = {
  A: 'A',
  B: 'B',
  C: 'C',
} as const

export type AbcClass = (typeof ABC_CLASS)[keyof typeof ABC_CLASS]

export const abcClassSchema = z.enum(['A', 'B', 'C'])

export const ROTATION_DIRECTION = {
  TOP: 'TOP',
  BOTTOM: 'BOTTOM',
} as const

export type RotationDirection =
  (typeof ROTATION_DIRECTION)[keyof typeof ROTATION_DIRECTION]

export const rotationDirectionSchema = z.enum(['TOP', 'BOTTOM'])

export const REPLENISHMENT_SEVERITY = {
  OUT_OF_STOCK: 'OUT_OF_STOCK',
  CRITICAL: 'CRITICAL',
} as const

export type ReplenishmentSeverity =
  (typeof REPLENISHMENT_SEVERITY)[keyof typeof REPLENISHMENT_SEVERITY]

export const replenishmentSeveritySchema = z.enum(['OUT_OF_STOCK', 'CRITICAL'])

export const REPLENISHMENT_SORT = {
  SEVERITY: 'severity',
  PRODUCT: 'product',
  COVERAGE: 'coverage',
} as const

export type ReplenishmentSort =
  (typeof REPLENISHMENT_SORT)[keyof typeof REPLENISHMENT_SORT]

export const replenishmentSortSchema = z.enum([
  'severity',
  'product',
  'coverage',
])

export const CORPORATE_SORT_FIELD = {
  SALES_AMOUNT: 'salesAmount',
  SALES_COUNT: 'salesCount',
  UNITS_SOLD: 'unitsSold',
  INVENTORY_VALUE: 'inventoryValue',
  CRITICAL_PRODUCT_COUNT: 'criticalProductCount',
  ACTIVE_TRANSFER_COUNT: 'activeTransferCount',
  NAME: 'name',
  CODE: 'code',
} as const

export type CorporateSortField =
  (typeof CORPORATE_SORT_FIELD)[keyof typeof CORPORATE_SORT_FIELD]

export const SORT_DIRECTION = {
  ASC: 'ASC',
  DESC: 'DESC',
} as const

export type SortDirection = (typeof SORT_DIRECTION)[keyof typeof SORT_DIRECTION]

export const sortDirectionSchema = z.enum(['ASC', 'DESC'])

// --- 1. Sales Trend (RF-DSH-01) ---

export const monthlySalesResponseSchema = z.object({
  year: z.number().int(),
  month: z.number().int(),
  salesCount: z.number().int(),
  unitsSold: z.number(),
  totalAmount: z.number(),
})

export const salesTrendResponseSchema = z.object({
  branchExternalId: uuidSchema.nullable().optional(),
  months: z.array(monthlySalesResponseSchema),
  monthOverMonthVariationPercent: z.number().nullable(),
  empty: z.boolean(),
})

export const salesTrendQuerySchema = z.object({
  months: z.number().int().min(1).max(12).optional(),
  branchExternalId: uuidSchema.optional(),
})

// --- 2. Product Rotation / Pareto ABC (RF-DSH-02) ---

export const rotationLineResponseSchema = z.object({
  productExternalId: uuidSchema,
  sku: z.string(),
  name: z.string(),
  unitsSold: z.number(),
  salesAmount: z.number(),
  sharePercent: z.number(),
  cumulativeSharePercent: z.number(),
  abcClass: abcClassSchema,
  coverageDays: z.number().nullable(),
})

export const rotationPageResponseSchema = paginatedResponseSchema(
  rotationLineResponseSchema,
)

export const rotationQuerySchema = z.object({
  from: z.string().optional(),
  to: z.string().optional(),
  direction: rotationDirectionSchema.optional(),
  branchExternalId: uuidSchema.optional(),
  page: z.number().int().min(0).optional(),
  size: z.number().int().min(1).max(100).optional(),
})

// --- 3. Active Transfers Activity & Stock Impact (RF-DSH-03) ---

export const transferStatusCountsResponseSchema = z.object({
  requested: z.number().int(),
  inPreparation: z.number().int(),
  inTransit: z.number().int(),
})

export const transferActivitySummaryResponseSchema = z.object({
  inbound: transferStatusCountsResponseSchema,
  outbound: transferStatusCountsResponseSchema,
  delayedCount: z.number().int(),
})

export const transferActivityQuerySchema = z.object({
  branchExternalId: uuidSchema.optional(),
})

export const transferStockImpactResponseSchema = z.object({
  productExternalId: uuidSchema,
  sku: z.string(),
  name: z.string(),
  currentStock: z.number(),
  inTransitStock: z.number(),
  inboundInTransit: z.number(),
  outboundCommitted: z.number(),
  projectedStock: z.number(),
})

export const transferStockImpactPageResponseSchema = paginatedResponseSchema(
  transferStockImpactResponseSchema,
)

export const transferStockImpactQuerySchema = z.object({
  branchExternalId: uuidSchema.optional(),
  page: z.number().int().min(0).optional(),
  size: z.number().int().min(1).max(100).optional(),
})

// --- 4. Critical Replenishment Panel (RF-DSH-04) ---

export const replenishmentLineResponseSchema = z.object({
  productExternalId: uuidSchema,
  sku: z.string(),
  name: z.string(),
  currentStock: z.number(),
  minStockThreshold: z.number(),
  severity: replenishmentSeveritySchema,
  coverageDays: z.number().nullable(),
})

export const replenishmentPageResponseSchema = paginatedResponseSchema(
  replenishmentLineResponseSchema,
)

export const replenishmentQuerySchema = z.object({
  severity: replenishmentSeveritySchema.optional(),
  sort: replenishmentSortSchema.optional(),
  branchExternalId: uuidSchema.optional(),
  page: z.number().int().min(0).optional(),
  size: z.number().int().min(1).max(100).optional(),
})

// --- 5. Corporate Comparative Board (RF-DSH-05) ---

export const branchPerformanceResponseSchema = z.object({
  branchExternalId: uuidSchema,
  code: z.string(),
  name: z.string(),
  salesAmount: z.number(),
  salesCount: z.number().int(),
  unitsSold: z.number(),
  inventoryValue: z.number(),
  criticalProductCount: z.number().int(),
  activeTransferCount: z.number().int(),
})

export const corporateBoardPageResponseSchema = paginatedResponseSchema(
  branchPerformanceResponseSchema,
)

export const corporateBoardQuerySchema = z.object({
  year: z.number().int().optional(),
  month: z.number().int().min(1).max(12).optional(),
  sort: z.string().optional(),
  direction: sortDirectionSchema.optional(),
  page: z.number().int().min(0).optional(),
  size: z.number().int().min(1).max(100).optional(),
})
