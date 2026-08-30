import { z } from 'zod'
import { uuidSchema } from '@/features/iam/schemas/common.schema.ts'

export const SALE_STATUS = {
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
} as const

export type SaleStatus = (typeof SALE_STATUS)[keyof typeof SALE_STATUS]

export const saleStatusSchema = z.enum(['COMPLETED', 'CANCELLED'])

export const branchRefResponseSchema = z.object({
  externalId: uuidSchema,
  name: z.string(),
})

export const userRefResponseSchema = z.object({
  externalId: uuidSchema,
  username: z.string(),
})

export const priceListRefResponseSchema = z.object({
  externalId: uuidSchema,
  code: z.string(),
  maxDiscountPercent: z.number(),
})

export const saleItemResponseSchema = z.object({
  externalId: uuidSchema,
  productExternalId: uuidSchema,
  sku: z.string(),
  name: z.string(),
  quantity: z.number(),
  listUnitPrice: z.number(),
  unitPrice: z.number(),
  discountPercent: z.number(),
  subtotal: z.number(),
})

export const customerRefResponseSchema = z.object({
  externalId: uuidSchema,
  name: z.string(),
  taxId: z.string().nullable().optional(),
})

export const saleDetailResponseSchema = z.object({
  externalId: uuidSchema,
  invoiceNumber: z.string(),
  status: saleStatusSchema,
  branch: branchRefResponseSchema.nullable().optional(),
  soldBy: userRefResponseSchema.nullable().optional(),
  priceList: priceListRefResponseSchema.nullable().optional(),
  customer: customerRefResponseSchema.nullable().optional(),
  customerName: z.string(),
  customerTaxId: z.string().nullable().optional(),
  subtotal: z.number(),
  discountAmount: z.number(),
  taxAmount: z.number(),
  totalAmount: z.number(),
  notes: z.string().nullable().optional(),
  cancellationReason: z.string().nullable().optional(),
  createdAt: z.string(),
  items: z.array(saleItemResponseSchema),
})

export const saleSummaryResponseSchema = z.object({
  externalId: uuidSchema,
  invoiceNumber: z.string(),
  status: saleStatusSchema,
  branch: branchRefResponseSchema.nullable().optional(),
  soldBy: userRefResponseSchema.nullable().optional(),
  priceList: priceListRefResponseSchema.nullable().optional(),
  customer: customerRefResponseSchema.nullable().optional(),
  customerName: z.string(),
  totalAmount: z.number(),
  createdAt: z.string(),
})

export const saleAggregatesResponseSchema = z.object({
  salesCount: z.number().int().nonnegative(),
  totalAmount: z.number(),
})

export const salePageResponseSchema = z.object({
  content: z.array(saleSummaryResponseSchema),
  totalElements: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  aggregates: saleAggregatesResponseSchema,
})

export const registerSaleItemRequestSchema = z.object({
  productExternalId: uuidSchema,
  quantity: z
    .number({ message: 'Quantity must be a valid number' })
    .positive('Quantity must be greater than 0'),
  unitOfMeasureExternalId: uuidSchema.nullable().optional(),
  discountPercent: z
    .number({ message: 'Discount must be a valid number' })
    .min(0, 'Discount cannot be negative')
    .max(100, 'Discount cannot exceed 100%')
    .nullable()
    .optional(),
})

export const registerSaleRequestSchema = z.object({
  priceListExternalId: uuidSchema.nullable().optional(),
  customerExternalId: uuidSchema.nullable().optional(),
  customerName: z.string().trim().min(1, 'Customer name is required'),
  customerTaxId: z.string().trim().nullable().optional(),
  taxPercent: z
    .number({ message: 'Tax percent must be a valid number' })
    .min(0, 'Tax percent cannot be negative')
    .max(100, 'Tax percent cannot exceed 100%')
    .nullable()
    .optional(),
  notes: z.string().trim().nullable().optional(),
  items: z
    .array(registerSaleItemRequestSchema)
    .min(1, 'At least one item is required in the sale'),
})

export const cancellationRequestSchema = z.object({
  reason: z.string().trim().min(1, 'Cancellation reason is required'),
})

export const saleQuerySchema = z.object({
  status: saleStatusSchema.optional(),
  from: z.string().optional(),
  to: z.string().optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
  sort: z.string().optional(),
})
