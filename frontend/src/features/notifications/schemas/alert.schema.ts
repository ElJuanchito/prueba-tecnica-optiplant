import { z } from 'zod'
import {
  paginatedResponseSchema,
  uuidSchema,
} from '@/features/iam/schemas/common.schema.ts'

export const ALERT_TYPE = {
  STOCK_MINIMUM: 'STOCK_MINIMUM',
  LOGISTIC_DELAY: 'LOGISTIC_DELAY',
  TRANSFER_DISCREPANCY: 'TRANSFER_DISCREPANCY',
  PRICE_CHANGE: 'PRICE_CHANGE',
} as const

export type AlertType = (typeof ALERT_TYPE)[keyof typeof ALERT_TYPE]

export const alertTypeSchema = z.enum([
  'STOCK_MINIMUM',
  'LOGISTIC_DELAY',
  'TRANSFER_DISCREPANCY',
  'PRICE_CHANGE',
])

export const ALERT_SEVERITY = {
  INFO: 'INFO',
  WARNING: 'WARNING',
  CRITICAL: 'CRITICAL',
} as const

export type AlertSeverity = (typeof ALERT_SEVERITY)[keyof typeof ALERT_SEVERITY]

export const alertSeveritySchema = z.enum(['INFO', 'WARNING', 'CRITICAL'])

export const alertResponseSchema = z.object({
  externalId: uuidSchema,
  alertType: alertTypeSchema,
  severity: alertSeveritySchema,
  title: z.string(),
  message: z.string(),
  isResolved: z.boolean(),
  resolvedAt: z.string().nullable().optional(),
  createdAt: z.string(),
})

export const alertPageResponseSchema =
  paginatedResponseSchema(alertResponseSchema)

export const alertQuerySchema = z.object({
  resolved: z.boolean().optional(),
  alertType: alertTypeSchema.optional(),
  severity: alertSeveritySchema.optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
})

export type AlertResponse = z.infer<typeof alertResponseSchema>
export type AlertPageResponse = z.infer<typeof alertPageResponseSchema>
export type AlertQueryParams = z.infer<typeof alertQuerySchema>
