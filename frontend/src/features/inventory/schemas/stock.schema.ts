import { z } from 'zod'
import {
  paginatedResponseSchema,
  uuidSchema,
} from '@/features/iam/schemas/common.schema.ts'

export const stockLineResponseSchema = z.object({
  productExternalId: uuidSchema,
  sku: z.string(),
  name: z.string(),
  currentStock: z.number(),
  reservedStock: z.number(),
  inTransitStock: z.number(),
  availableStock: z.number(),
  minStockThreshold: z.number(),
  averageCost: z.number(),
  lastUpdatedAt: z.string().nullable().optional(),
})

export const stockPageResponseSchema = paginatedResponseSchema(
  stockLineResponseSchema,
)

export const stockQuerySchema = z.object({
  productExternalId: uuidSchema.optional(),
  belowThreshold: z.boolean().optional(),
  sort: z.enum(['product', 'currentStock']).optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
})

export const branchAvailabilityResponseSchema = z.object({
  branchExternalId: uuidSchema,
  branchName: z.string(),
  currentStock: z.number(),
  reservedStock: z.number(),
  inTransitStock: z.number(),
  availableStock: z.number(),
  isOwnBranch: z.boolean().nullable().optional(),
})

export const networkAvailabilityResponseSchema = z.object({
  productExternalId: uuidSchema,
  sku: z.string(),
  name: z.string(),
  branches: z.array(branchAvailabilityResponseSchema),
  networkTotal: z.number(),
})

export const setThresholdRequestSchema = z.object({
  minStockThreshold: z
    .number({ message: 'Threshold must be a valid number' })
    .min(0, 'Threshold must be greater than or equal to 0'),
})

export const thresholdResponseSchema = z.object({
  productExternalId: uuidSchema,
  minStockThreshold: z.number(),
})

export type StockLineResponse = z.infer<typeof stockLineResponseSchema>
export type StockPageResponse = z.infer<typeof stockPageResponseSchema>
export type StockQueryParams = z.infer<typeof stockQuerySchema>
export type BranchAvailabilityResponse = z.infer<
  typeof branchAvailabilityResponseSchema
>
export type NetworkAvailabilityResponse = z.infer<
  typeof networkAvailabilityResponseSchema
>
export type SetThresholdRequest = z.infer<typeof setThresholdRequestSchema>
export type ThresholdResponse = z.infer<typeof thresholdResponseSchema>
