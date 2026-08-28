import { z } from 'zod'

export const ROLE = {
  ADMIN: 'ADMIN',
  BRANCH_MANAGER: 'BRANCH_MANAGER',
  OPERATOR: 'OPERATOR',
} as const

export type Role = (typeof ROLE)[keyof typeof ROLE]

export const roleSchema = z.enum(['ADMIN', 'BRANCH_MANAGER', 'OPERATOR'])

export const uuidSchema = z
  .string()
  .regex(
    /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/,
    'Invalid UUID',
  )

export function paginatedResponseSchema<T extends z.ZodTypeAny>(itemSchema: T) {
  return z.object({
    content: z.array(itemSchema),
    totalElements: z.number().int().nonnegative(),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
  })
}

export type PaginatedResponse<T> = {
  content: T[]
  totalElements: number
  page: number
  size: number
}
