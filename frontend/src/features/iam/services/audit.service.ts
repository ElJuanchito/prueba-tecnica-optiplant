import { apiClient } from '@/lib/api-client.ts'
import {
  auditPageResponseSchema,
  auditQuerySchema,
} from '../schemas/audit.schema.ts'
import type {
  AuditPageResponse,
  AuditQueryParams,
} from '../types/audit.types.ts'

export const auditService = {
  async listAuditLogs(params?: AuditQueryParams): Promise<AuditPageResponse> {
    const validatedParams = params ? auditQuerySchema.parse(params) : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.userId !== undefined) {
        searchParams.set('userId', validatedParams.userId)
      }
      if (validatedParams.branchId !== undefined) {
        searchParams.set('branchId', validatedParams.branchId)
      }
      if (validatedParams.entityName !== undefined) {
        searchParams.set('entityName', validatedParams.entityName)
      }
      if (validatedParams.action !== undefined) {
        searchParams.set('action', validatedParams.action)
      }
      if (validatedParams.from !== undefined) {
        searchParams.set('from', validatedParams.from)
      }
      if (validatedParams.to !== undefined) {
        searchParams.set('to', validatedParams.to)
      }
      if (validatedParams.page !== undefined) {
        searchParams.set('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.set('size', String(validatedParams.size))
      }
    }

    const query = searchParams.toString()
    const path = `/api/audit${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return auditPageResponseSchema.parse(raw)
  },
}
