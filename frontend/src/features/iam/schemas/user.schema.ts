import { z } from 'zod'
import {
  paginatedResponseSchema,
  roleSchema,
  uuidSchema,
} from './common.schema.ts'

export const createUserSchema = z.object({
  username: z.string().trim().min(1, 'Username is required'),
  email: z.string().trim().email('Invalid email address'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  fullName: z.string().trim().min(1, 'Full name is required'),
  role: roleSchema,
  branchId: uuidSchema.nullable().optional(),
})

export const editUserSchema = z.object({
  email: z.string().trim().email('Invalid email address'),
  fullName: z.string().trim().min(1, 'Full name is required'),
  role: roleSchema,
  branchId: uuidSchema.nullable().optional(),
})

export const userResponseSchema = z.object({
  externalId: uuidSchema,
  username: z.string(),
  email: z.string(),
  fullName: z.string(),
  role: roleSchema,
  branchId: uuidSchema.nullable(),
  branchName: z.string().nullable().optional(),
  branchCode: z.string().nullable().optional(),
  active: z.boolean(),
})

export const userQuerySchema = z.object({
  active: z.boolean().optional(),
  role: roleSchema.optional(),
  branchId: uuidSchema.optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
})

export const userPageResponseSchema =
  paginatedResponseSchema(userResponseSchema)

export type CreateUserInput = z.infer<typeof createUserSchema>
export type EditUserInput = z.infer<typeof editUserSchema>
export type UserResponse = z.infer<typeof userResponseSchema>
export type UserQueryParams = z.infer<typeof userQuerySchema>
export type UserPageResponse = z.infer<typeof userPageResponseSchema>
