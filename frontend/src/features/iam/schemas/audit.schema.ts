import { z } from 'zod'
import { paginatedResponseSchema, uuidSchema } from './common.schema.ts'

export const auditEntryResponseSchema = z.object({
  externalId: uuidSchema,
  actorUserId: uuidSchema.nullable(),
  branchId: uuidSchema.nullable(),
  action: z.string(),
  entityName: z.string(),
  entityId: z.string(),
  payloadBefore: z.string().nullable(),
  payloadAfter: z.string().nullable(),
  ipAddress: z.string().nullable(),
  createdAt: z.string(),
})

export const auditQuerySchema = z.object({
  userId: uuidSchema.optional(),
  branchId: uuidSchema.optional(),
  entityName: z.string().optional(),
  action: z.string().optional(),
  from: z.string().optional(),
  to: z.string().optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
})

export const auditPageResponseSchema = paginatedResponseSchema(
  auditEntryResponseSchema,
)

export type AuditEntryResponse = z.infer<typeof auditEntryResponseSchema>
export type AuditQueryParams = z.infer<typeof auditQuerySchema>
export type AuditPageResponse = z.infer<typeof auditPageResponseSchema>
