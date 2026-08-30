import { z } from 'zod'
import {
  paginatedResponseSchema,
  uuidSchema,
} from '@/features/iam/schemas/common.schema.ts'

export const TRANSFER_STATUS = {
  REQUESTED: 'REQUESTED',
  IN_PREPARATION: 'IN_PREPARATION',
  IN_TRANSIT: 'IN_TRANSIT',
  RECEIVED: 'RECEIVED',
  RECEIVED_WITH_DISCREPANCY: 'RECEIVED_WITH_DISCREPANCY',
  CANCELLED: 'CANCELLED',
} as const

export type TransferStatus =
  (typeof TRANSFER_STATUS)[keyof typeof TRANSFER_STATUS]

export const transferStatusSchema = z.enum([
  'REQUESTED',
  'IN_PREPARATION',
  'IN_TRANSIT',
  'RECEIVED',
  'RECEIVED_WITH_DISCREPANCY',
  'CANCELLED',
])

export const TRANSFER_PRIORITY = {
  LOW: 'LOW',
  STANDARD: 'STANDARD',
  URGENT: 'URGENT',
} as const

export type TransferPriority =
  (typeof TRANSFER_PRIORITY)[keyof typeof TRANSFER_PRIORITY]

export const transferPrioritySchema = z.enum(['LOW', 'STANDARD', 'URGENT'])

export const TRANSFER_DIRECTION = {
  INBOUND: 'INBOUND',
  OUTBOUND: 'OUTBOUND',
} as const

export type TransferDirection =
  (typeof TRANSFER_DIRECTION)[keyof typeof TRANSFER_DIRECTION]

export const transferDirectionSchema = z.enum(['INBOUND', 'OUTBOUND'])

export const branchReferenceSchema = z.object({
  externalId: uuidSchema,
  name: z.string(),
})

export const requestedLineRequestSchema = z.object({
  productExternalId: uuidSchema,
  requestedQuantity: z
    .number({ message: 'Quantity must be a valid number' })
    .positive('Quantity must be greater than 0'),
})

export const requestTransferRequestSchema = z.object({
  originBranchExternalId: uuidSchema,
  priority: transferPrioritySchema,
  notes: z.string().optional().nullable(),
  items: z
    .array(requestedLineRequestSchema)
    .min(1, 'At least one item is required'),
})

export const approvedLineRequestSchema = z.object({
  itemExternalId: uuidSchema,
  approvedQuantity: z
    .number({ message: 'Quantity must be a valid number' })
    .positive('Quantity must be greater than 0'),
})

export const approvalRequestSchema = z.object({
  items: z
    .array(approvedLineRequestSchema)
    .min(1, 'At least one item is required'),
  notes: z.string().optional().nullable(),
})

export const reasonRequestSchema = z.object({
  reason: z.string().trim().min(1, 'Reason is required'),
})

export const dispatchLineRequestSchema = z.object({
  itemExternalId: uuidSchema,
  dispatchedQuantity: z
    .number({ message: 'Quantity must be a valid number' })
    .positive('Quantity must be greater than 0'),
})

export const dispatchRequestSchema = z.object({
  carrierName: z.string().trim().min(1, 'Carrier name is required'),
  trackingNumber: z.string().optional().nullable(),
  estimatedArrivalAt: z.string().optional().nullable(),
  items: z
    .array(dispatchLineRequestSchema)
    .min(1, 'At least one item is required'),
})

export const receiptLineRequestSchema = z.object({
  itemExternalId: uuidSchema,
  receivedQuantity: z
    .number({ message: 'Quantity must be a valid number' })
    .nonnegative('Quantity cannot be negative'),
  discrepancyReason: z.string().optional().nullable(),
})

export const receiptRequestSchema = z.object({
  items: z
    .array(receiptLineRequestSchema)
    .min(1, 'At least one item is required'),
})

export const transferItemResponseSchema = z.object({
  externalId: uuidSchema,
  productExternalId: uuidSchema,
  sku: z.string(),
  name: z.string(),
  requestedQuantity: z.number(),
  dispatchedQuantity: z.number().nullable().optional(),
  receivedQuantity: z.number().nullable().optional(),
  discrepancyQuantity: z.number().nullable().optional(),
  discrepancyReason: z.string().nullable().optional(),
})

export const transferDetailResponseSchema = z.object({
  externalId: uuidSchema,
  transferNumber: z.string(),
  status: transferStatusSchema,
  priority: transferPrioritySchema,
  originBranch: branchReferenceSchema.nullable().optional(),
  destinationBranch: branchReferenceSchema.nullable().optional(),
  carrierName: z.string().nullable().optional(),
  trackingNumber: z.string().nullable().optional(),
  dispatchedAt: z.string().nullable().optional(),
  estimatedArrivalAt: z.string().nullable().optional(),
  actualArrivalAt: z.string().nullable().optional(),
  deviationHours: z.number().nullable().optional(),
  observations: z.array(z.string()),
  requestedBy: uuidSchema.nullable().optional(),
  dispatchedBy: uuidSchema.nullable().optional(),
  receivedBy: uuidSchema.nullable().optional(),
  createdAt: z.string(),
  updatedAt: z.string().nullable().optional(),
  items: z.array(transferItemResponseSchema),
})

export const transferSummaryResponseSchema = z.object({
  externalId: uuidSchema,
  transferNumber: z.string(),
  status: transferStatusSchema,
  priority: transferPrioritySchema,
  originBranch: branchReferenceSchema.nullable().optional(),
  destinationBranch: branchReferenceSchema.nullable().optional(),
  createdAt: z.string(),
  estimatedArrivalAt: z.string().nullable().optional(),
})

export const transferPageResponseSchema = paginatedResponseSchema(
  transferSummaryResponseSchema,
)

export const transferQuerySchema = z.object({
  status: transferStatusSchema.optional(),
  direction: transferDirectionSchema.optional(),
  from: z.string().optional(),
  to: z.string().optional(),
  sort: z.string().optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
})

export type BranchReference = z.infer<typeof branchReferenceSchema>
export type RequestedLineRequest = z.infer<typeof requestedLineRequestSchema>
export type RequestTransferRequest = z.infer<
  typeof requestTransferRequestSchema
>
export type ApprovedLineRequest = z.infer<typeof approvedLineRequestSchema>
export type ApprovalRequest = z.infer<typeof approvalRequestSchema>
export type ReasonRequest = z.infer<typeof reasonRequestSchema>
export type DispatchLineRequest = z.infer<typeof dispatchLineRequestSchema>
export type DispatchRequest = z.infer<typeof dispatchRequestSchema>
export type ReceiptLineRequest = z.infer<typeof receiptLineRequestSchema>
export type ReceiptRequest = z.infer<typeof receiptRequestSchema>
export type TransferItemResponse = z.infer<typeof transferItemResponseSchema>
export type TransferDetailResponse = z.infer<
  typeof transferDetailResponseSchema
>
export type TransferSummaryResponse = z.infer<
  typeof transferSummaryResponseSchema
>
export type TransferPageResponse = z.infer<typeof transferPageResponseSchema>
export type TransferQueryParams = z.infer<typeof transferQuerySchema>
