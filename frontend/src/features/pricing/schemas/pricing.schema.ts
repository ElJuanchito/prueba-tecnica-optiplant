import { z } from 'zod'
import {
  paginatedResponseSchema,
  uuidSchema,
} from '@/features/iam/schemas/common.schema.ts'

export const priceListResponseSchema = z.object({
  externalId: uuidSchema,
  code: z.string(),
  name: z.string(),
  description: z.string().nullable().optional(),
  maxDiscountPercent: z.number(),
  isDefault: z.boolean(),
  active: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string().nullable().optional(),
})

export const priceListPageResponseSchema = paginatedResponseSchema(
  priceListResponseSchema,
)

export const createPriceListRequestSchema = z.object({
  code: z.string().trim().min(1, 'Code is required'),
  name: z.string().trim().min(1, 'Name is required'),
  description: z.string().trim().nullable().optional(),
  maxDiscountPercent: z
    .number({ message: 'Max discount percent must be a valid number' })
    .min(0, 'Discount percent cannot be negative')
    .max(100, 'Discount percent cannot exceed 100%'),
})

export const updatePriceListRequestSchema = z.object({
  name: z.string().trim().min(1, 'Name is required'),
  description: z.string().trim().nullable().optional(),
  maxDiscountPercent: z
    .number({ message: 'Max discount percent must be a valid number' })
    .min(0, 'Discount percent cannot be negative')
    .max(100, 'Discount percent cannot exceed 100%'),
})

export const priceResponseSchema = z.object({
  externalId: uuidSchema,
  priceListExternalId: uuidSchema,
  productExternalId: uuidSchema,
  branchExternalId: uuidSchema.nullable().optional(),
  unitPrice: z.number(),
  validFrom: z.string().nullable().optional(),
  validTo: z.string().nullable().optional(),
  createdAt: z.string(),
})

export const pricePageResponseSchema =
  paginatedResponseSchema(priceResponseSchema)

export const setPriceRequestSchema = z.object({
  productExternalId: uuidSchema,
  branchExternalId: uuidSchema.nullable().optional(),
  unitPrice: z
    .number({ message: 'Unit price must be a valid number' })
    .positive('Unit price must be greater than 0'),
  validFrom: z.string().nullable().optional(),
})

export const closePriceRequestSchema = z.object({
  validTo: z.string().nullable().optional(),
})

export const quoteItemRequestSchema = z.object({
  productExternalId: uuidSchema,
  quantity: z
    .number({ message: 'Quantity must be a valid number' })
    .positive('Quantity must be greater than 0'),
  discountPercent: z
    .number({ message: 'Discount percent must be a valid number' })
    .min(0, 'Discount cannot be negative')
    .max(100, 'Discount cannot exceed 100%')
    .nullable()
    .optional(),
})

export const quoteRequestSchema = z.object({
  priceListExternalId: uuidSchema.nullable().optional(),
  items: z.array(quoteItemRequestSchema).min(1, 'At least one item is required'),
})

export const quoteItemResponseSchema = z.object({
  productExternalId: uuidSchema,
  listUnitPrice: z.number(),
  unitPrice: z.number(),
  subtotal: z.number(),
})

export const quoteResponseSchema = z.object({
  priceListExternalId: uuidSchema.nullable().optional(),
  code: z.string().nullable().optional(),
  maxDiscountPercent: z.number().nullable().optional(),
  items: z.array(quoteItemResponseSchema),
})

export const priceListQuerySchema = z.object({
  active: z.boolean().optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
})

export const priceQuerySchema = z.object({
  productExternalId: uuidSchema.optional(),
  branchExternalId: uuidSchema.optional(),
  currentOnly: z.boolean().optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
})
