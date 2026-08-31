import type { z } from 'zod'
import type {
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

export type {
  AbcClass,
  CorporateSortField,
  ReplenishmentSeverity,
  ReplenishmentSort,
  RotationDirection,
  SortDirection,
} from '../schemas/analytics.schema.ts'

// Sales Trend
export type MonthlySalesResponse = z.infer<typeof monthlySalesResponseSchema>
export type SalesTrendResponse = z.infer<typeof salesTrendResponseSchema>
export type SalesTrendQueryParams = z.infer<typeof salesTrendQuerySchema>

// Product Rotation / Pareto ABC
export type RotationLineResponse = z.infer<typeof rotationLineResponseSchema>
export type RotationPageResponse = z.infer<typeof rotationPageResponseSchema>
export type RotationQueryParams = z.infer<typeof rotationQuerySchema>

// Active Transfers Activity & Stock Impact
export type TransferStatusCountsResponse = z.infer<
  typeof transferStatusCountsResponseSchema
>
export type TransferActivitySummaryResponse = z.infer<
  typeof transferActivitySummaryResponseSchema
>
export type TransferActivityQueryParams = z.infer<
  typeof transferActivityQuerySchema
>
export type TransferStockImpactResponse = z.infer<
  typeof transferStockImpactResponseSchema
>
export type TransferStockImpactPageResponse = z.infer<
  typeof transferStockImpactPageResponseSchema
>
export type TransferStockImpactQueryParams = z.infer<
  typeof transferStockImpactQuerySchema
>

// Critical Replenishment Panel
export type ReplenishmentLineResponse = z.infer<
  typeof replenishmentLineResponseSchema
>
export type ReplenishmentPageResponse = z.infer<
  typeof replenishmentPageResponseSchema
>
export type ReplenishmentQueryParams = z.infer<typeof replenishmentQuerySchema>

// Corporate Board
export type BranchPerformanceResponse = z.infer<
  typeof branchPerformanceResponseSchema
>
export type CorporateBoardPageResponse = z.infer<
  typeof corporateBoardPageResponseSchema
>
export type CorporateBoardQueryParams = z.infer<
  typeof corporateBoardQuerySchema
>
