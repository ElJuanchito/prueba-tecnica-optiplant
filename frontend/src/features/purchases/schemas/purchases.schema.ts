import { z } from 'zod'
import {
  paginatedResponseSchema,
  uuidSchema,
} from '@/features/iam/schemas/common.schema.ts'

export const PURCHASE_ORDER_STATUS = {
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  PARTIALLY_RECEIVED: 'PARTIALLY_RECEIVED',
  RECEIVED: 'RECEIVED',
  CANCELLED: 'CANCELLED',
} as const

export type PurchaseOrderStatus =
  (typeof PURCHASE_ORDER_STATUS)[keyof typeof PURCHASE_ORDER_STATUS]

export const purchaseOrderStatusSchema = z.enum([
  'PENDING',
  'APPROVED',
  'PARTIALLY_RECEIVED',
  'RECEIVED',
  'CANCELLED',
])

// --- Suppliers ---

export const supplierResponseSchema = z.object({
  externalId: uuidSchema,
  taxId: z.string(),
  name: z.string(),
  contactName: z.string().nullable().optional(),
  email: z.string().nullable().optional(),
  phone: z.string().nullable().optional(),
  address: z.string().nullable().optional(),
  active: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string().nullable().optional(),
})

export const supplierPageResponseSchema = paginatedResponseSchema(
  supplierResponseSchema,
)

export const createSupplierRequestSchema = z.object({
  taxId: z.string().trim().min(1, 'Tax ID is required'),
  name: z.string().trim().min(1, 'Name is required'),
  contactName: z.string().trim().nullable().optional(),
  email: z
    .string()
    .trim()
    .email('Invalid email address')
    .or(z.literal(''))
    .nullable()
    .optional(),
  phone: z.string().trim().nullable().optional(),
  address: z.string().trim().nullable().optional(),
})

export const updateSupplierRequestSchema = z.object({
  name: z.string().trim().min(1, 'Name is required'),
  contactName: z.string().trim().nullable().optional(),
  email: z
    .string()
    .trim()
    .email('Invalid email address')
    .or(z.literal(''))
    .nullable()
    .optional(),
  phone: z.string().trim().nullable().optional(),
  address: z.string().trim().nullable().optional(),
})

export const supplierQuerySchema = z.object({
  search: z.string().optional(),
  active: z.boolean().optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
  sort: z.string().optional(),
})

// --- Reference Schemas ---

export const branchRefResponseSchema = z.object({
  externalId: uuidSchema,
  name: z.string(),
})

export const supplierRefResponseSchema = z.object({
  externalId: uuidSchema,
  taxId: z.string().optional(),
  name: z.string(),
})

export const userRefResponseSchema = z.object({
  externalId: uuidSchema,
  username: z.string(),
})

// --- Purchase Order Items ---

export const purchaseOrderItemResponseSchema = z.object({
  externalId: uuidSchema,
  productExternalId: uuidSchema,
  sku: z.string(),
  name: z.string(),
  orderedQuantity: z.number(),
  receivedQuantity: z.number(),
  pendingQuantity: z.number(),
  unitCost: z.number(),
  discountPercent: z.number(),
  effectiveUnitCost: z.number(),
  subtotal: z.number(),
})

// --- Purchase Order Detail & Summary ---

export const purchaseOrderDetailResponseSchema = z.object({
  externalId: uuidSchema,
  orderNumber: z.string(),
  status: purchaseOrderStatusSchema,
  branch: branchRefResponseSchema.nullable().optional(),
  supplier: supplierRefResponseSchema,
  createdBy: userRefResponseSchema.nullable().optional(),
  paymentTerms: z.string().nullable().optional(),
  totalAmount: z.number(),
  notes: z.string().nullable().optional(),
  cancellationReason: z.string().nullable().optional(),
  createdAt: z.string(),
  updatedAt: z.string().nullable().optional(),
  receivedAt: z.string().nullable().optional(),
  items: z.array(purchaseOrderItemResponseSchema),
})

export const purchaseOrderSummaryResponseSchema = z.object({
  externalId: uuidSchema,
  orderNumber: z.string(),
  status: purchaseOrderStatusSchema,
  branch: branchRefResponseSchema.nullable().optional(),
  supplier: supplierRefResponseSchema,
  totalAmount: z.number(),
  createdAt: z.string(),
  receivedAt: z.string().nullable().optional(),
})

export const purchaseOrderPageResponseSchema = z.object({
  content: z.array(purchaseOrderSummaryResponseSchema),
  totalElements: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
})

// --- Purchase Order Creation & Update Requests ---

export const createPurchaseOrderItemRequestSchema = z.object({
  productExternalId: uuidSchema,
  quantity: z
    .number({ message: 'Quantity must be a valid number' })
    .positive('Quantity must be greater than 0'),
  unitOfMeasureExternalId: uuidSchema.nullable().optional(),
  unitCost: z
    .number({ message: 'Unit cost must be a valid number' })
    .min(0, 'Unit cost cannot be negative'),
  discountPercent: z
    .number({ message: 'Discount percent must be a valid number' })
    .min(0, 'Discount cannot be negative')
    .max(100, 'Discount cannot exceed 100%')
    .nullable()
    .optional(),
})

export const createPurchaseOrderRequestSchema = z.object({
  supplierExternalId: uuidSchema,
  paymentTerms: z.string().trim().nullable().optional(),
  notes: z.string().trim().nullable().optional(),
  items: z
    .array(createPurchaseOrderItemRequestSchema)
    .min(1, 'At least one item is required in the purchase order'),
})

export const updatePurchaseOrderRequestSchema = createPurchaseOrderRequestSchema

export const purchaseOrderQuerySchema = z.object({
  supplierExternalId: uuidSchema.optional(),
  productExternalId: uuidSchema.optional(),
  status: purchaseOrderStatusSchema.optional(),
  from: z.string().optional(),
  to: z.string().optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
  sort: z.string().optional(),
})

// --- Cancellation ---

export const cancellationRequestSchema = z.object({
  reason: z.string().trim().min(1, 'Cancellation reason is required'),
})

// --- Reception ---

export const registerReceptionItemRequestSchema = z.object({
  itemExternalId: uuidSchema,
  receivedQuantity: z
    .number({ message: 'Received quantity must be a valid number' })
    .min(0, 'Received quantity cannot be negative'),
  unitOfMeasureExternalId: uuidSchema.nullable().optional(),
})

export const registerReceptionRequestSchema = z.object({
  notes: z.string().trim().nullable().optional(),
  items: z
    .array(registerReceptionItemRequestSchema)
    .min(1, 'At least one item must be received'),
})

// --- Cost History ---

export const costHistoryItemResponseSchema = z.object({
  orderExternalId: uuidSchema,
  orderNumber: z.string(),
  supplier: z.object({
    externalId: uuidSchema,
    name: z.string(),
  }),
  unitCost: z.number(),
  discountPercent: z.number(),
  effectiveUnitCost: z.number(),
  quantity: z.number(),
  orderedAt: z.string(),
  receivedAt: z.string().nullable().optional(),
})

export const costHistoryPageResponseSchema = z.object({
  content: z.array(costHistoryItemResponseSchema),
  totalElements: z.number().int().nonnegative(),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
})

export const costHistoryQuerySchema = z.object({
  productExternalId: uuidSchema,
  supplierExternalId: uuidSchema.optional(),
  from: z.string().optional(),
  to: z.string().optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
})
