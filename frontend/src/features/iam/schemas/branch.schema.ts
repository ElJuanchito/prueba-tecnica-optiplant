import { z } from 'zod'
import { paginatedResponseSchema, uuidSchema } from './common.schema.ts'

export const createBranchSchema = z.object({
  code: z.string().trim().min(1, 'Branch code is required'),
  name: z.string().trim().min(1, 'Branch name is required'),
  address: z.string().trim().min(1, 'Address is required'),
  city: z.string().trim().min(1, 'City is required'),
  phone: z.string().trim().nullable().optional(),
})

export const editBranchSchema = z.object({
  name: z.string().trim().min(1, 'Branch name is required'),
  address: z.string().trim().min(1, 'Address is required'),
  city: z.string().trim().min(1, 'City is required'),
  phone: z.string().trim().nullable().optional(),
})

export const branchResponseSchema = z.object({
  externalId: uuidSchema,
  code: z.string(),
  name: z.string(),
  address: z.string(),
  city: z.string(),
  phone: z.string().nullable(),
  active: z.boolean(),
})

export const branchQuerySchema = z.object({
  active: z.boolean().optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
})

export const branchPageResponseSchema =
  paginatedResponseSchema(branchResponseSchema)

export type CreateBranchInput = z.infer<typeof createBranchSchema>
export type EditBranchInput = z.infer<typeof editBranchSchema>
export type BranchResponse = z.infer<typeof branchResponseSchema>
export type BranchQueryParams = z.infer<typeof branchQuerySchema>
export type BranchPageResponse = z.infer<typeof branchPageResponseSchema>
