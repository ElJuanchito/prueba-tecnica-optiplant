import { z } from 'zod'
import {
  paginatedResponseSchema,
  uuidSchema,
} from '@/features/iam/schemas/common.schema.ts'
import { branchReferenceSchema } from './route.schema.ts'

export const activeTransferResponseSchema = z.object({
  transferExternalId: uuidSchema,
  transferNumber: z.string(),
  status: z.string(),
  originBranch: branchReferenceSchema.nullable().optional(),
  destinationBranch: branchReferenceSchema.nullable().optional(),
  priority: z.string(),
  itemCount: z.number(),
  totalQuantity: z.number(),
  estimatedArrivalAt: z.string().nullable().optional(),
  isDelayed: z.boolean(),
})

export const activeTransferPageResponseSchema = paginatedResponseSchema(
  activeTransferResponseSchema,
)

export const activeTransferQuerySchema = z.object({
  status: z.string().optional(),
  delayed: z.boolean().optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
})

export type ActiveTransferResponse = z.infer<
  typeof activeTransferResponseSchema
>
export type ActiveTransferPageResponse = z.infer<
  typeof activeTransferPageResponseSchema
>
export type ActiveTransferQueryParams = z.infer<
  typeof activeTransferQuerySchema
>
