import { apiClient } from '@/lib/api-client.ts'
import {
  alertPageResponseSchema,
  alertQuerySchema,
  alertResponseSchema,
} from '../schemas/index.ts'
import type {
  AlertPageResponse,
  AlertQueryParams,
  AlertResponse,
} from '../types/index.ts'

export const alertService = {
  async listAlerts(params?: AlertQueryParams): Promise<AlertPageResponse> {
    const validatedParams = params ? alertQuerySchema.parse(params) : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.resolved !== undefined) {
        searchParams.set('resolved', String(validatedParams.resolved))
      }
      if (validatedParams.alertType !== undefined) {
        searchParams.set('alertType', validatedParams.alertType)
      }
      if (validatedParams.severity !== undefined) {
        searchParams.set('severity', validatedParams.severity)
      }
      if (validatedParams.page !== undefined) {
        searchParams.set('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.set('size', String(validatedParams.size))
      }
    }

    const query = searchParams.toString()
    const path = `/api/notifications/alerts${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return alertPageResponseSchema.parse(raw)
  },

  async resolveAlert(externalId: string): Promise<AlertResponse> {
    const raw = await apiClient<unknown>(
      `/api/notifications/alerts/${externalId}/resolve`,
      { method: 'PATCH' },
    )
    return alertResponseSchema.parse(raw)
  },
}
