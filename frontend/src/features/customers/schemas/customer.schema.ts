import { z } from 'zod'
import {
  paginatedResponseSchema,
  uuidSchema,
} from '@/features/iam/schemas/common.schema.ts'
import { saleStatusSchema } from '@/features/sales/schemas/sale.schema.ts'

export const customerResponseSchema = z.object({
  externalId: uuidSchema,
  name: z.string(),
  taxId: z.string().nullable().optional(),
  email: z.string().nullable().optional(),
  phone: z.string().nullable().optional(),
  address: z.string().nullable().optional(),
  active: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const customerRefResponseSchema = z.object({
  externalId: uuidSchema,
  name: z.string(),
  taxId: z.string().nullable().optional(),
})

export const customerPageResponseSchema =
  paginatedResponseSchema(customerResponseSchema)

export const createCustomerRequestSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Customer name is required')
    .max(150, 'Name cannot exceed 150 characters'),
  taxId: z
    .string()
    .trim()
    .max(30, 'Tax ID cannot exceed 30 characters')
    .nullable()
    .optional(),
  email: z
    .string()
    .trim()
    .max(100, 'Email cannot exceed 100 characters')
    .nullable()
    .optional(),
  phone: z
    .string()
    .trim()
    .max(50, 'Phone cannot exceed 50 characters')
    .nullable()
    .optional(),
  address: z
    .string()
    .trim()
    .max(255, 'Address cannot exceed 255 characters')
    .nullable()
    .optional(),
})

export const editCustomerRequestSchema = createCustomerRequestSchema

export const customerQuerySchema = z.object({
  search: z.string().optional(),
  active: z.boolean().optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
  sort: z.string().optional(),
})

export const customerSalesHistoryQuerySchema = z.object({
  status: saleStatusSchema.optional(),
  from: z.string().optional(),
  to: z.string().optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
  sort: z.string().optional(),
})
