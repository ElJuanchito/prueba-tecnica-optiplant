import { apiClient } from '@/lib/api-client.ts'
import {
  activeTransferPageResponseSchema,
  activeTransferQuerySchema,
  compliancePageResponseSchema,
  complianceQuerySchema,
  createRouteRequestSchema,
  routePageResponseSchema,
  routeQuerySchema,
  routeResponseSchema,
  updateRouteRequestSchema,
} from '../schemas/index.ts'
import type {
  ActiveTransferPageResponse,
  ActiveTransferQueryParams,
  CompliancePageResponse,
  ComplianceQueryParams,
  CreateRouteRequest,
  RoutePageResponse,
  RouteQueryParams,
  RouteResponse,
  UpdateRouteRequest,
} from '../types/index.ts'

export const logisticsService = {
  async createRoute(input: CreateRouteRequest): Promise<RouteResponse> {
    const validatedInput = createRouteRequestSchema.parse(input)
    const raw = await apiClient<unknown>('/api/logistics/routes', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })
    return routeResponseSchema.parse(raw)
  },

  async listRoutes(params?: RouteQueryParams): Promise<RoutePageResponse> {
    const validatedParams = params ? routeQuerySchema.parse(params) : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.active !== undefined) {
        searchParams.set('active', String(validatedParams.active))
      }
      if (validatedParams.sort !== undefined) {
        searchParams.set('sort', validatedParams.sort)
      }
      if (validatedParams.page !== undefined) {
        searchParams.set('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.set('size', String(validatedParams.size))
      }
    }

    const query = searchParams.toString()
    const path = `/api/logistics/routes${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return routePageResponseSchema.parse(raw)
  },

  async updateRoute(
    externalId: string,
    input: UpdateRouteRequest,
  ): Promise<RouteResponse> {
    const validatedInput = updateRouteRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/logistics/routes/${externalId}`,
      {
        method: 'PUT',
        body: JSON.stringify(validatedInput),
      },
    )
    return routeResponseSchema.parse(raw)
  },

  async deactivateRoute(externalId: string): Promise<RouteResponse> {
    const raw = await apiClient<unknown>(
      `/api/logistics/routes/${externalId}/deactivation`,
      {
        method: 'PATCH',
      },
    )
    return routeResponseSchema.parse(raw)
  },

  async listActiveTransfers(
    params?: ActiveTransferQueryParams,
  ): Promise<ActiveTransferPageResponse> {
    const validatedParams = params
      ? activeTransferQuerySchema.parse(params)
      : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.status !== undefined) {
        searchParams.set('status', validatedParams.status)
      }
      if (validatedParams.delayed !== undefined) {
        searchParams.set('delayed', String(validatedParams.delayed))
      }
      if (validatedParams.page !== undefined) {
        searchParams.set('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.set('size', String(validatedParams.size))
      }
    }

    const query = searchParams.toString()
    const path = `/api/logistics/transfers/active${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return activeTransferPageResponseSchema.parse(raw)
  },

  async getComplianceReport(
    params: ComplianceQueryParams,
  ): Promise<CompliancePageResponse> {
    const validatedParams = complianceQuerySchema.parse(params)
    const searchParams = new URLSearchParams()

    searchParams.set('from', validatedParams.from)
    searchParams.set('to', validatedParams.to)
    if (validatedParams.groupBy !== undefined) {
      searchParams.set('groupBy', validatedParams.groupBy)
    }
    if (validatedParams.page !== undefined) {
      searchParams.set('page', String(validatedParams.page))
    }
    if (validatedParams.size !== undefined) {
      searchParams.set('size', String(validatedParams.size))
    }

    const query = searchParams.toString()
    const path = `/api/logistics/compliance${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return compliancePageResponseSchema.parse(raw)
  },
}
