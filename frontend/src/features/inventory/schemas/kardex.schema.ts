import { z } from 'zod'
import {
  paginatedResponseSchema,
  uuidSchema,
} from '@/features/iam/schemas/common.schema.ts'

export const STOCK_MOVEMENT_TYPE = {
  PURCHASE_RECEIPT: 'PURCHASE_RECEIPT',
  SALE: 'SALE',
  TRANSFER_OUT: 'TRANSFER_OUT',
  TRANSFER_IN: 'TRANSFER_IN',
  ADJUSTMENT_POS: 'ADJUSTMENT_POS',
  ADJUSTMENT_NEG: 'ADJUSTMENT_NEG',
  DAMAGE_WASTE: 'DAMAGE_WASTE',
  INITIAL_LOAD: 'INITIAL_LOAD',
} as const

export type StockMovementType =
  (typeof STOCK_MOVEMENT_TYPE)[keyof typeof STOCK_MOVEMENT_TYPE]

export const stockMovementTypeSchema = z.enum([
  'PURCHASE_RECEIPT',
  'SALE',
  'TRANSFER_OUT',
  'TRANSFER_IN',
  'ADJUSTMENT_POS',
  'ADJUSTMENT_NEG',
  'DAMAGE_WASTE',
  'INITIAL_LOAD',
])

export const kardexLineResponseSchema = z.object({
  externalId: uuidSchema,
  productExternalId: uuidSchema,
  movementType: stockMovementTypeSchema,
  quantity: z.number(),
  unitCost: z.number().nullable().optional(),
  totalCost: z.number().nullable().optional(),
  previousStock: z.number(),
  resultingStock: z.number(),
  referenceType: z.string().nullable().optional(),
  referenceId: z.string().nullable().optional(),
  notes: z.string().nullable().optional(),
  userExternalId: uuidSchema.nullable().optional(),
  createdAt: z.string(),
})

export const kardexPageResponseSchema = paginatedResponseSchema(
  kardexLineResponseSchema,
)

export const kardexQuerySchema = z.object({
  productExternalId: uuidSchema.optional(),
  movementType: stockMovementTypeSchema.optional(),
  from: z.string().optional(),
  to: z.string().optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
})

export type KardexLineResponse = z.infer<typeof kardexLineResponseSchema>
export type KardexPageResponse = z.infer<typeof kardexPageResponseSchema>
export type KardexQueryParams = z.infer<typeof kardexQuerySchema>
