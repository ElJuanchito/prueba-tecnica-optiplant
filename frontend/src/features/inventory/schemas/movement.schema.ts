import { z } from 'zod'
import { uuidSchema } from '@/features/iam/schemas/common.schema.ts'
import { stockMovementTypeSchema } from './kardex.schema.ts'

export const adjustStockRequestSchema = z.object({
  productExternalId: uuidSchema,
  countedQuantity: z
    .number({ message: 'Counted quantity is required' })
    .min(0, 'Counted quantity must be greater than or equal to 0'),
  reason: z
    .string()
    .trim()
    .min(1, 'Adjustment reason is required')
    .max(255, 'Reason must be at most 255 characters'),
})

export const writeOffRequestSchema = z.object({
  productExternalId: uuidSchema,
  quantity: z
    .number({ message: 'Quantity is required' })
    .positive('Quantity must be greater than 0'),
  reason: z
    .string()
    .trim()
    .min(1, 'Write-off reason is required')
    .max(255, 'Reason must be at most 255 characters'),
})

export const movementReceiptResponseSchema = z.object({
  movementExternalId: uuidSchema,
  movementType: stockMovementTypeSchema,
  quantity: z.number(),
  previousStock: z.number(),
  resultingStock: z.number(),
  createdAt: z.string(),
})

export type AdjustStockRequest = z.infer<typeof adjustStockRequestSchema>
export type WriteOffRequest = z.infer<typeof writeOffRequestSchema>
export type MovementReceiptResponse = z.infer<
  typeof movementReceiptResponseSchema
>
