import { z } from 'zod'
import { uuidSchema } from '@/features/iam/schemas/common.schema.ts'

export const unitRequestSchema = z.object({
  unitName: z
    .string()
    .trim()
    .min(1, 'Unit name is required')
    .max(50, 'Unit name must be at most 50 characters')
    .regex(
      /^[A-Z0-9_]+$/,
      'Unit name must contain only uppercase letters, numbers and underscores',
    ),
  conversionFactor: z
    .number()
    .positive('Conversion factor must be greater than zero'),
  defaultSaleUnit: z.boolean(),
})

export const productUnitItemResponseSchema = z.object({
  externalId: uuidSchema,
  unitName: z.string(),
  conversionFactor: z.number(),
  defaultSaleUnit: z.boolean(),
  createdAt: z.string().optional(),
})

export const productUnitListResponseSchema = z.array(
  productUnitItemResponseSchema,
)

export type UnitRequestInput = z.infer<typeof unitRequestSchema>
export type ProductUnitItemResponse = z.infer<
  typeof productUnitItemResponseSchema
>
export type ProductUnitListResponse = z.infer<
  typeof productUnitListResponseSchema
>
