import { z } from 'zod'
import {
  paginatedResponseSchema,
  uuidSchema,
} from '@/features/iam/schemas/common.schema.ts'

export const unitPayloadRequestSchema = z.object({
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

export const createProductSchema = z.object({
  sku: z
    .string()
    .trim()
    .min(1, 'SKU is required')
    .max(50, 'SKU must be at most 50 characters'),
  name: z
    .string()
    .trim()
    .min(1, 'Product name is required')
    .max(150, 'Product name must be at most 150 characters'),
  description: z.string().trim().nullable().optional(),
  categoryExternalId: uuidSchema,
  baseUnit: z
    .string()
    .trim()
    .min(1, 'Base unit is required')
    .max(20, 'Base unit must be at most 20 characters')
    .regex(
      /^[A-Z0-9_]+$/,
      'Base unit must contain only uppercase letters, numbers and underscores',
    ),
  units: z.array(unitPayloadRequestSchema).optional(),
})

export const editProductSchema = z.object({
  sku: z
    .string()
    .trim()
    .min(1, 'SKU is required')
    .max(50, 'SKU must be at most 50 characters'),
  name: z
    .string()
    .trim()
    .min(1, 'Product name is required')
    .max(150, 'Product name must be at most 150 characters'),
  description: z.string().trim().nullable().optional(),
  categoryExternalId: uuidSchema,
})

export const categoryRefResponseSchema = z.object({
  externalId: uuidSchema,
  name: z.string(),
  active: z.boolean(),
})

export const productUnitResponseSchema = z.object({
  externalId: uuidSchema,
  unitName: z.string(),
  conversionFactor: z.number(),
  defaultSaleUnit: z.boolean(),
})

export const productDetailResponseSchema = z.object({
  externalId: uuidSchema,
  sku: z.string(),
  name: z.string(),
  description: z.string().nullable().optional(),
  baseUnit: z.string(),
  active: z.boolean(),
  category: categoryRefResponseSchema.nullable().optional(),
  units: z.array(productUnitResponseSchema),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const productListItemResponseSchema = z.object({
  externalId: uuidSchema,
  sku: z.string(),
  name: z.string(),
  baseUnit: z.string(),
  active: z.boolean(),
  category: categoryRefResponseSchema.nullable().optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const productQuerySchema = z.object({
  q: z.string().optional(),
  categoryId: uuidSchema.optional(),
  active: z.enum(['true', 'false', 'all']).optional(),
  sort: z.enum(['sku', 'name', 'createdAt']).optional(),
  direction: z.enum(['asc', 'desc']).optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
})

export const productPageResponseSchema = paginatedResponseSchema(
  productListItemResponseSchema,
)

export type UnitPayloadRequest = z.infer<typeof unitPayloadRequestSchema>
export type CreateProductInput = z.infer<typeof createProductSchema>
export type EditProductInput = z.infer<typeof editProductSchema>
export type CategoryRefResponse = z.infer<typeof categoryRefResponseSchema>
export type ProductUnitResponse = z.infer<typeof productUnitResponseSchema>
export type ProductDetailResponse = z.infer<typeof productDetailResponseSchema>
export type ProductListItemResponse = z.infer<
  typeof productListItemResponseSchema
>
export type ProductQueryParams = z.infer<typeof productQuerySchema>
export type ProductPageResponse = z.infer<typeof productPageResponseSchema>
