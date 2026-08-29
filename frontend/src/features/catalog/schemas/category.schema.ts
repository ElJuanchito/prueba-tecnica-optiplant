import { z } from 'zod'
import {
  paginatedResponseSchema,
  uuidSchema,
} from '@/features/iam/schemas/common.schema.ts'

export const createCategorySchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Category name is required')
    .max(100, 'Category name must be at most 100 characters'),
  description: z.string().trim().nullable().optional(),
})

export const editCategorySchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Category name is required')
    .max(100, 'Category name must be at most 100 characters'),
  description: z.string().trim().nullable().optional(),
})

export const categoryResponseSchema = z.object({
  externalId: uuidSchema,
  name: z.string(),
  description: z.string().nullable().optional(),
  active: z.boolean(),
  activeProductCount: z.number().int().nonnegative(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const categoryQuerySchema = z.object({
  name: z.string().optional(),
  active: z.enum(['true', 'false', 'all']).optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
})

export const categoryPageResponseSchema = paginatedResponseSchema(
  categoryResponseSchema,
)

export type CreateCategoryInput = z.infer<typeof createCategorySchema>
export type EditCategoryInput = z.infer<typeof editCategorySchema>
export type CategoryResponse = z.infer<typeof categoryResponseSchema>
export type CategoryQueryParams = z.infer<typeof categoryQuerySchema>
export type CategoryPageResponse = z.infer<typeof categoryPageResponseSchema>
