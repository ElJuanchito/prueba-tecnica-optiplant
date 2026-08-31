import { apiClient } from '@/lib/api-client.ts'
import {
  corporateBoardPageResponseSchema,
  corporateBoardQuerySchema,
  replenishmentPageResponseSchema,
  replenishmentQuerySchema,
  rotationPageResponseSchema,
  rotationQuerySchema,
  salesTrendQuerySchema,
  salesTrendResponseSchema,
  transferActivityQuerySchema,
  transferActivitySummaryResponseSchema,
  transferStockImpactPageResponseSchema,
  transferStockImpactQuerySchema,
} from '../schemas/index.ts'
import type {
  CorporateBoardPageResponse,
  CorporateBoardQueryParams,
  ReplenishmentPageResponse,
  ReplenishmentQueryParams,
  RotationPageResponse,
  RotationQueryParams,
  SalesTrendQueryParams,
  SalesTrendResponse,
  TransferActivityQueryParams,
  TransferActivitySummaryResponse,
  TransferStockImpactPageResponse,
  TransferStockImpactQueryParams,
} from '../types/index.ts'

export const analyticsService = {
  // --- 1. Sales Trend (RF-DSH-01) ---
  async getSalesTrend(
    params?: SalesTrendQueryParams,
  ): Promise<SalesTrendResponse> {
    const validatedParams = params
      ? salesTrendQuerySchema.parse(params)
      : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.months !== undefined) {
        searchParams.append('months', String(validatedParams.months))
      }
      if (validatedParams.branchExternalId) {
        searchParams.append(
          'branchExternalId',
          validatedParams.branchExternalId,
        )
      }
    }

    const queryString = searchParams.toString()
    const path = `/api/analytics/dashboard/sales-trend${queryString ? `?${queryString}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return salesTrendResponseSchema.parse(raw)
  },

  // --- 2. Product Rotation / Pareto ABC (RF-DSH-02) ---
  async getRotation(
    params?: RotationQueryParams,
  ): Promise<RotationPageResponse> {
    const validatedParams = params
      ? rotationQuerySchema.parse(params)
      : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.from) {
        searchParams.append('from', validatedParams.from)
      }
      if (validatedParams.to) {
        searchParams.append('to', validatedParams.to)
      }
      if (validatedParams.direction) {
        searchParams.append('direction', validatedParams.direction)
      }
      if (validatedParams.branchExternalId) {
        searchParams.append(
          'branchExternalId',
          validatedParams.branchExternalId,
        )
      }
      if (validatedParams.page !== undefined) {
        searchParams.append('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.append('size', String(validatedParams.size))
      }
    }

    const queryString = searchParams.toString()
    const path = `/api/analytics/dashboard/rotation${queryString ? `?${queryString}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return rotationPageResponseSchema.parse(raw)
  },

  // --- 3. Active Transfers Activity Summary (RF-DSH-03) ---
  async getTransferActivitySummary(
    params?: TransferActivityQueryParams,
  ): Promise<TransferActivitySummaryResponse> {
    const validatedParams = params
      ? transferActivityQuerySchema.parse(params)
      : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams?.branchExternalId) {
      searchParams.append('branchExternalId', validatedParams.branchExternalId)
    }

    const queryString = searchParams.toString()
    const path = `/api/analytics/dashboard/transfers${queryString ? `?${queryString}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return transferActivitySummaryResponseSchema.parse(raw)
  },

  // --- 4. Active Transfers Stock Impact (RF-DSH-03) ---
  async getTransferStockImpact(
    params?: TransferStockImpactQueryParams,
  ): Promise<TransferStockImpactPageResponse> {
    const validatedParams = params
      ? transferStockImpactQuerySchema.parse(params)
      : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.branchExternalId) {
        searchParams.append(
          'branchExternalId',
          validatedParams.branchExternalId,
        )
      }
      if (validatedParams.page !== undefined) {
        searchParams.append('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.append('size', String(validatedParams.size))
      }
    }

    const queryString = searchParams.toString()
    const path = `/api/analytics/dashboard/transfers/stock-impact${queryString ? `?${queryString}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return transferStockImpactPageResponseSchema.parse(raw)
  },

  // --- 5. Critical Replenishment Panel (RF-DSH-04) ---
  async getReplenishment(
    params?: ReplenishmentQueryParams,
  ): Promise<ReplenishmentPageResponse> {
    const validatedParams = params
      ? replenishmentQuerySchema.parse(params)
      : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.severity) {
        searchParams.append('severity', validatedParams.severity)
      }
      if (validatedParams.sort) {
        searchParams.append('sort', validatedParams.sort)
      }
      if (validatedParams.branchExternalId) {
        searchParams.append(
          'branchExternalId',
          validatedParams.branchExternalId,
        )
      }
      if (validatedParams.page !== undefined) {
        searchParams.append('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.append('size', String(validatedParams.size))
      }
    }

    const queryString = searchParams.toString()
    const path = `/api/analytics/replenishment${queryString ? `?${queryString}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return replenishmentPageResponseSchema.parse(raw)
  },

  // --- 6. Corporate Comparative Board (RF-DSH-05) ---
  async getCorporateBoard(
    params?: CorporateBoardQueryParams,
  ): Promise<CorporateBoardPageResponse> {
    const validatedParams = params
      ? corporateBoardQuerySchema.parse(params)
      : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.year !== undefined) {
        searchParams.append('year', String(validatedParams.year))
      }
      if (validatedParams.month !== undefined) {
        searchParams.append('month', String(validatedParams.month))
      }
      if (validatedParams.sort) {
        searchParams.append('sort', validatedParams.sort)
      }
      if (validatedParams.direction) {
        searchParams.append('direction', validatedParams.direction)
      }
      if (validatedParams.page !== undefined) {
        searchParams.append('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.append('size', String(validatedParams.size))
      }
    }

    const queryString = searchParams.toString()
    const path = `/api/analytics/corporate/branches${queryString ? `?${queryString}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return corporateBoardPageResponseSchema.parse(raw)
  },
}
