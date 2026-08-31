import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiClientModule from '@/lib/api-client.ts'
import { analyticsService } from '../services/analytics.service.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'

describe('Analytics Service Tests (All Endpoints §6)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  // --- 1. Sales Trend ---
  describe('getSalesTrend', () => {
    it('calls /api/analytics/dashboard/sales-trend with default params', async () => {
      const mockResponse = {
        branchExternalId: VALID_UUID_1,
        months: [
          {
            year: 2026,
            month: 8,
            salesCount: 100,
            unitsSold: 450,
            totalAmount: 9800.5,
          },
        ],
        monthOverMonthVariationPercent: 12.5,
        empty: false,
      }

      const spy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValue(mockResponse)

      const result = await analyticsService.getSalesTrend({ months: 4 })

      expect(spy).toHaveBeenCalledWith(
        '/api/analytics/dashboard/sales-trend?months=4',
        { method: 'GET' },
      )
      expect(result).toEqual(mockResponse)
    })

    it('passes branchExternalId when provided for admin', async () => {
      const mockResponse = {
        branchExternalId: VALID_UUID_1,
        months: [],
        monthOverMonthVariationPercent: null,
        empty: true,
      }

      const spy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValue(mockResponse)

      const result = await analyticsService.getSalesTrend({
        months: 6,
        branchExternalId: VALID_UUID_1,
      })

      expect(spy).toHaveBeenCalledWith(
        `/api/analytics/dashboard/sales-trend?months=6&branchExternalId=${VALID_UUID_1}`,
        { method: 'GET' },
      )
      expect(result).toEqual(mockResponse)
    })
  })

  // --- 2. Product Rotation / Pareto ABC ---
  describe('getRotation', () => {
    it('calls /api/analytics/dashboard/rotation with all search params', async () => {
      const mockPage = {
        content: [
          {
            productExternalId: VALID_UUID_1,
            sku: 'FERT-001',
            name: 'Fertilizante Foliar 1L',
            unitsSold: 300,
            salesAmount: 7500,
            sharePercent: 50.0,
            cumulativeSharePercent: 50.0,
            abcClass: 'A',
            coverageDays: 10.0,
          },
        ],
        totalElements: 1,
        page: 0,
        size: 20,
      }

      const spy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValue(mockPage)

      const result = await analyticsService.getRotation({
        from: '2026-08-01T00:00:00Z',
        to: '2026-08-31T23:59:59Z',
        direction: 'TOP',
        branchExternalId: VALID_UUID_1,
        page: 0,
        size: 20,
      })

      expect(spy).toHaveBeenCalledWith(
        `/api/analytics/dashboard/rotation?from=2026-08-01T00%3A00%3A00Z&to=2026-08-31T23%3A59%3A59Z&direction=TOP&branchExternalId=${VALID_UUID_1}&page=0&size=20`,
        { method: 'GET' },
      )
      expect(result).toEqual(mockPage)
    })
  })

  // --- 3. Active Transfers Activity Summary ---
  describe('getTransferActivitySummary', () => {
    it('calls /api/analytics/dashboard/transfers', async () => {
      const mockSummary = {
        inbound: { requested: 1, inPreparation: 2, inTransit: 3 },
        outbound: { requested: 0, inPreparation: 1, inTransit: 1 },
        delayedCount: 0,
      }

      const spy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValue(mockSummary)

      const result = await analyticsService.getTransferActivitySummary({
        branchExternalId: VALID_UUID_1,
      })

      expect(spy).toHaveBeenCalledWith(
        `/api/analytics/dashboard/transfers?branchExternalId=${VALID_UUID_1}`,
        { method: 'GET' },
      )
      expect(result).toEqual(mockSummary)
    })
  })

  // --- 4. Active Transfers Stock Impact ---
  describe('getTransferStockImpact', () => {
    it('calls /api/analytics/dashboard/transfers/stock-impact', async () => {
      const mockPage = {
        content: [
          {
            productExternalId: VALID_UUID_1,
            sku: 'FERT-001',
            name: 'Fertilizante Foliar 1L',
            currentStock: 100,
            inTransitStock: 20,
            inboundInTransit: 20,
            outboundCommitted: 10,
            projectedStock: 110,
          },
        ],
        totalElements: 1,
        page: 0,
        size: 20,
      }

      const spy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValue(mockPage)

      const result = await analyticsService.getTransferStockImpact({
        branchExternalId: VALID_UUID_1,
        page: 0,
        size: 20,
      })

      expect(spy).toHaveBeenCalledWith(
        `/api/analytics/dashboard/transfers/stock-impact?branchExternalId=${VALID_UUID_1}&page=0&size=20`,
        { method: 'GET' },
      )
      expect(result).toEqual(mockPage)
    })
  })

  // --- 5. Critical Replenishment Panel ---
  describe('getReplenishment', () => {
    it('calls /api/analytics/replenishment with severity and sort', async () => {
      const mockPage = {
        content: [
          {
            productExternalId: VALID_UUID_1,
            sku: 'FERT-001',
            name: 'Fertilizante Foliar 1L',
            currentStock: 0,
            minStockThreshold: 50,
            severity: 'OUT_OF_STOCK',
            coverageDays: 0,
          },
        ],
        totalElements: 1,
        page: 0,
        size: 20,
      }

      const spy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValue(mockPage)

      const result = await analyticsService.getReplenishment({
        severity: 'OUT_OF_STOCK',
        sort: 'severity',
        branchExternalId: VALID_UUID_1,
        page: 0,
        size: 20,
      })

      expect(spy).toHaveBeenCalledWith(
        `/api/analytics/replenishment?severity=OUT_OF_STOCK&sort=severity&branchExternalId=${VALID_UUID_1}&page=0&size=20`,
        { method: 'GET' },
      )
      expect(result).toEqual(mockPage)
    })
  })

  // --- 6. Corporate Comparative Board ---
  describe('getCorporateBoard', () => {
    it('calls /api/analytics/corporate/branches with year, month, sort and direction', async () => {
      const mockPage = {
        content: [
          {
            branchExternalId: VALID_UUID_1,
            code: 'SUC-01',
            name: 'Sucursal Matriz Quito',
            salesAmount: 50000,
            salesCount: 400,
            unitsSold: 2000,
            inventoryValue: 80000,
            criticalProductCount: 2,
            activeTransferCount: 1,
          },
        ],
        totalElements: 1,
        page: 0,
        size: 20,
      }

      const spy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValue(mockPage)

      const result = await analyticsService.getCorporateBoard({
        year: 2026,
        month: 8,
        sort: 'salesAmount',
        direction: 'DESC',
        page: 0,
        size: 20,
      })

      expect(spy).toHaveBeenCalledWith(
        '/api/analytics/corporate/branches?year=2026&month=8&sort=salesAmount&direction=DESC&page=0&size=20',
        { method: 'GET' },
      )
      expect(result).toEqual(mockPage)
    })
  })
})
