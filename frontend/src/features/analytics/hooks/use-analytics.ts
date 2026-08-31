import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import { analyticsService } from '../services/analytics.service.ts'
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

// --- 1. Sales Trend Hook ---
export function useSalesTrend(
  params?: SalesTrendQueryParams,
  enabled: boolean = true,
): UseQueryResult<SalesTrendResponse, Error> {
  return useQuery({
    queryKey: queryKeys.analytics.salesTrend(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => analyticsService.getSalesTrend(params),
    enabled,
  })
}

// --- 2. Product Rotation / Pareto ABC Hook ---
export function useRotation(
  params?: RotationQueryParams,
  enabled: boolean = true,
): UseQueryResult<RotationPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.analytics.rotation(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => analyticsService.getRotation(params),
    enabled,
  })
}

// --- 3. Active Transfers Activity Summary Hook ---
export function useTransferActivitySummary(
  params?: TransferActivityQueryParams,
  enabled: boolean = true,
): UseQueryResult<TransferActivitySummaryResponse, Error> {
  return useQuery({
    queryKey: queryKeys.analytics.transfersSummary(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => analyticsService.getTransferActivitySummary(params),
    enabled,
  })
}

// --- 4. Active Transfers Stock Impact Hook ---
export function useTransferStockImpact(
  params?: TransferStockImpactQueryParams,
  enabled: boolean = true,
): UseQueryResult<TransferStockImpactPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.analytics.transfersImpact(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => analyticsService.getTransferStockImpact(params),
    enabled,
  })
}

// --- 5. Critical Replenishment Panel Hook ---
export function useReplenishment(
  params?: ReplenishmentQueryParams,
  enabled: boolean = true,
): UseQueryResult<ReplenishmentPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.analytics.replenishment(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => analyticsService.getReplenishment(params),
    enabled,
  })
}

// --- 6. Corporate Comparative Board Hook ---
export function useCorporateBoard(
  params?: CorporateBoardQueryParams,
  enabled: boolean = true,
): UseQueryResult<CorporateBoardPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.analytics.corporateBoard(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => analyticsService.getCorporateBoard(params),
    enabled,
  })
}
