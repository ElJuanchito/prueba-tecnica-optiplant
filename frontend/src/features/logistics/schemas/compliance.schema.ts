import { z } from 'zod'
import { paginatedResponseSchema } from '@/features/iam/schemas/common.schema.ts'

export const COMPLIANCE_GROUPING = {
  ROUTE: 'ROUTE',
  BRANCH: 'BRANCH',
} as const

export type ComplianceGrouping =
  (typeof COMPLIANCE_GROUPING)[keyof typeof COMPLIANCE_GROUPING]

export const complianceGroupingSchema = z.enum(['ROUTE', 'BRANCH'])

export const complianceRowResponseSchema = z.object({
  key: z.string(),
  label: z.string(),
  deliveredCount: z.number(),
  onTimeCount: z.number(),
  onTimePercentage: z.number(),
  averageDeviationHours: z.number(),
  unmeasuredCount: z.number(),
})

export const compliancePageResponseSchema = paginatedResponseSchema(
  complianceRowResponseSchema,
)

export const complianceQuerySchema = z.object({
  from: z.string({ message: 'From date is required' }).min(1),
  to: z.string({ message: 'To date is required' }).min(1),
  groupBy: complianceGroupingSchema.optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
})

export type ComplianceRowResponse = z.infer<typeof complianceRowResponseSchema>
export type CompliancePageResponse = z.infer<
  typeof compliancePageResponseSchema
>
export type ComplianceQueryParams = z.infer<typeof complianceQuerySchema>
