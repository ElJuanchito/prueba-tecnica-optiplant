import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  createTestQueryClient,
  renderWithProviders,
} from '@/test/test-utils.tsx'
import { AnalyticsDashboard } from '../components/AnalyticsDashboard.tsx'
import { analyticsService } from '../services/analytics.service.ts'
import { branchService } from '@/features/iam/services/branch.service.ts'
import { queryKeys } from '@/lib/query-keys.ts'

const PRODUCT_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const PRODUCT_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'
const BRANCH_UUID_1 = 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33'
const BRANCH_UUID_2 = 'd0eebc99-9c0b-4ef8-bb6d-6bb9bd380a44'

vi.mock('@tanstack/react-router', async () => {
  const actual = (await vi.importActual('@tanstack/react-router')) as Record<
    string,
    unknown
  >
  return {
    ...actual,
    Link: ({
      children,
      to,
      className,
    }: {
      children: React.ReactNode
      to?: string
      className?: string
    }) => (
      <a href={to ?? '#'} className={className}>
        {children}
      </a>
    ),
  }
})

describe('AnalyticsDashboard Component Tests', () => {
  const mockBranches = [
    {
      externalId: BRANCH_UUID_1,
      code: 'SUC-01',
      name: 'Sucursal Matriz Quito',
      address: 'Av. Amazonas N24-100',
      city: 'Quito',
      phone: '022345678',
      active: true,
      createdAt: '2026-08-30T10:00:00Z',
    },
    {
      externalId: BRANCH_UUID_2,
      code: 'SUC-02',
      name: 'Sucursal Guayaquil Centro',
      address: 'Av. 9 de Octubre 500',
      city: 'Guayaquil',
      phone: '042123456',
      active: true,
      createdAt: '2026-08-30T10:00:00Z',
    },
  ]

  const mockSalesTrend = {
    branchExternalId: BRANCH_UUID_1,
    months: [
      {
        year: 2026,
        month: 7,
        salesCount: 80,
        unitsSold: 300.0,
        totalAmount: 7500.0,
      },
      {
        year: 2026,
        month: 8,
        salesCount: 100,
        unitsSold: 450.0,
        totalAmount: 10000.0,
      },
    ],
    monthOverMonthVariationPercent: 33.33,
    empty: false,
  }

  const mockRotationPage = {
    content: [
      {
        productExternalId: PRODUCT_UUID_1,
        sku: 'FERT-001',
        name: 'Fertilizante Foliar 1L',
        unitsSold: 300,
        salesAmount: 7500,
        sharePercent: 75.0,
        cumulativeSharePercent: 75.0,
        abcClass: 'A' as const,
        coverageDays: 12.0,
      },
      {
        productExternalId: PRODUCT_UUID_2,
        sku: 'INSECT-002',
        name: 'Insecticida Orgánico 500ml',
        unitsSold: 50,
        salesAmount: 2500,
        sharePercent: 25.0,
        cumulativeSharePercent: 100.0,
        abcClass: 'B' as const,
        coverageDays: 0,
      },
    ],
    totalElements: 2,
    page: 0,
    size: 20,
  }

  const mockTransfersSummary = {
    inbound: { requested: 1, inPreparation: 2, inTransit: 3 },
    outbound: { requested: 0, inPreparation: 1, inTransit: 1 },
    delayedCount: 2,
  }

  const mockStockImpactPage = {
    content: [
      {
        productExternalId: PRODUCT_UUID_1,
        sku: 'FERT-001',
        name: 'Fertilizante Foliar 1L',
        currentStock: 100,
        inTransitStock: 50,
        inboundInTransit: 50,
        outboundCommitted: 20,
        projectedStock: 130,
      },
    ],
    totalElements: 1,
    page: 0,
    size: 20,
  }

  const mockReplenishmentPage = {
    content: [
      {
        productExternalId: PRODUCT_UUID_1,
        sku: 'FERT-001',
        name: 'Fertilizante Foliar 1L',
        currentStock: 0,
        minStockThreshold: 20,
        severity: 'OUT_OF_STOCK' as const,
        coverageDays: 0,
      },
      {
        productExternalId: PRODUCT_UUID_2,
        sku: 'INSECT-002',
        name: 'Insecticida Orgánico 500ml',
        currentStock: 5,
        minStockThreshold: 15,
        severity: 'CRITICAL' as const,
        coverageDays: 3.5,
      },
    ],
    totalElements: 2,
    page: 0,
    size: 20,
  }

  const mockCorporateBoardPage = {
    content: [
      {
        branchExternalId: BRANCH_UUID_1,
        code: 'SUC-01',
        name: 'Sucursal Matriz Quito',
        salesAmount: 50000,
        salesCount: 400,
        unitsSold: 2000,
        inventoryValue: 80000,
        criticalProductCount: 2,
        activeTransferCount: 4,
      },
      {
        branchExternalId: BRANCH_UUID_2,
        code: 'SUC-02',
        name: 'Sucursal Guayaquil Centro',
        salesAmount: 35000,
        salesCount: 280,
        unitsSold: 1400,
        inventoryValue: 60000,
        criticalProductCount: 1,
        activeTransferCount: 2,
      },
    ],
    totalElements: 2,
    page: 0,
    size: 20,
  }

  beforeEach(() => {
    vi.restoreAllMocks()

    vi.spyOn(branchService, 'listBranches').mockResolvedValue({
      content: mockBranches,
      totalElements: 2,
      page: 0,
      size: 100,
    })

    vi.spyOn(analyticsService, 'getSalesTrend').mockResolvedValue(
      mockSalesTrend,
    )
    vi.spyOn(analyticsService, 'getRotation').mockResolvedValue(
      mockRotationPage,
    )
    vi.spyOn(analyticsService, 'getTransferActivitySummary').mockResolvedValue(
      mockTransfersSummary,
    )
    vi.spyOn(analyticsService, 'getTransferStockImpact').mockResolvedValue(
      mockStockImpactPage,
    )
    vi.spyOn(analyticsService, 'getReplenishment').mockResolvedValue(
      mockReplenishmentPage,
    )
    vi.spyOn(analyticsService, 'getCorporateBoard').mockResolvedValue(
      mockCorporateBoardPage,
    )
  })

  it('renders sales trend view with KPIs, monthly table and NO UUIDs in DOM text', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'test-token',
      refreshToken: 'test-refresh',
      expiresInSeconds: 3600,
      role: 'ADMIN',
      branchId: null,
      username: 'admin',
    })

    renderWithProviders(<AnalyticsDashboard />, { queryClient })

    // Wait for sales trend data to be rendered
    await waitFor(() => {
      expect(screen.getByText('+33.3%')).toBeInTheDocument()
      expect(
        screen.getAllByText(/\$10,000\.00/i).length,
      ).toBeGreaterThanOrEqual(1)
    })

    // Assert that no UUID is exposed in text content
    const documentBodyText = document.body.textContent || ''
    expect(documentBodyText).not.toContain(PRODUCT_UUID_1)
    expect(documentBodyText).not.toContain(PRODUCT_UUID_2)
    expect(documentBodyText).not.toContain(BRANCH_UUID_1)
    expect(documentBodyText).not.toContain(BRANCH_UUID_2)
  })

  it('navigates to Rotation / Pareto ABC tab and displays ABC classification', async () => {
    const user = userEvent.setup()
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'test-token',
      refreshToken: 'test-refresh',
      expiresInSeconds: 3600,
      role: 'ADMIN',
      branchId: null,
      username: 'admin',
    })

    renderWithProviders(<AnalyticsDashboard />, { queryClient })

    // Click on Rotation tab
    const rotationTab = await screen.findByRole('tab', {
      name: /Rotation & ABC/i,
    })
    await user.click(rotationTab)

    // Check rotation items
    await waitFor(() => {
      expect(screen.getByText('FERT-001')).toBeInTheDocument()
      expect(screen.getByText('Fertilizante Foliar 1L')).toBeInTheDocument()
      expect(screen.getByText('INSECT-002')).toBeInTheDocument()
    })

    // Check ABC Class badges
    expect(screen.getByText(/Class A/i)).toBeInTheDocument()
    expect(screen.getByText(/Class B/i)).toBeInTheDocument()

    // Check that UUIDs are not rendered
    const documentBodyText = document.body.textContent || ''
    expect(documentBodyText).not.toContain(PRODUCT_UUID_1)
  })

  it('navigates to Transfers & Impact tab and shows logistics summary and stock impact', async () => {
    const user = userEvent.setup()
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'test-token',
      refreshToken: 'test-refresh',
      expiresInSeconds: 3600,
      role: 'ADMIN',
      branchId: null,
      username: 'admin',
    })

    renderWithProviders(<AnalyticsDashboard />, { queryClient })

    const transfersTab = await screen.findByRole('tab', {
      name: /Transfers & Impact/i,
    })
    await user.click(transfersTab)

    // Check delayed transfer banner
    await waitFor(() => {
      expect(screen.getByText(/Logistics Delay Alert/i)).toBeInTheDocument()
      expect(screen.getByText(/2 transfers exceeded/i)).toBeInTheDocument()
    })

    // Check stock impact row
    expect(screen.getByText('FERT-001')).toBeInTheDocument()
    expect(screen.getByText('130.00')).toBeInTheDocument()
  })

  it('navigates to Replenishment tab and renders critical & out of stock products', async () => {
    const user = userEvent.setup()
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'test-token',
      refreshToken: 'test-refresh',
      expiresInSeconds: 3600,
      role: 'ADMIN',
      branchId: null,
      username: 'admin',
    })

    renderWithProviders(<AnalyticsDashboard />, { queryClient })

    const replenishmentTab = await screen.findByRole('tab', {
      name: /Critical Replenishment/i,
    })
    await user.click(replenishmentTab)

    await waitFor(() => {
      expect(screen.getByText('OUT OF STOCK')).toBeInTheDocument()
      expect(screen.getByText('CRITICAL')).toBeInTheDocument()
    })

    expect(screen.getByText('FERT-001')).toBeInTheDocument()
    expect(screen.getByText('INSECT-002')).toBeInTheDocument()
  })

  it('renders Corporate Board tab for ADMIN and displays comparative branch table', async () => {
    const user = userEvent.setup()
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'test-token',
      refreshToken: 'test-refresh',
      expiresInSeconds: 3600,
      role: 'ADMIN',
      branchId: null,
      username: 'admin',
    })

    renderWithProviders(<AnalyticsDashboard />, { queryClient })

    const corpTab = await screen.findByRole('tab', {
      name: /Corporate Board/i,
    })
    await user.click(corpTab)

    await waitFor(() => {
      expect(screen.getByText('Sucursal Matriz Quito')).toBeInTheDocument()
      expect(screen.getByText('Sucursal Guayaquil Centro')).toBeInTheDocument()
      expect(screen.getByText('SUC-01')).toBeInTheDocument()
      expect(screen.getByText('SUC-02')).toBeInTheDocument()
    })

    // Assert that raw branch UUIDs are never exposed
    const documentBodyText = document.body.textContent || ''
    expect(documentBodyText).not.toContain(BRANCH_UUID_1)
    expect(documentBodyText).not.toContain(BRANCH_UUID_2)
  })

  it('does not show Corporate Board tab for non-admin OPERATOR role', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'test-token',
      refreshToken: 'test-refresh',
      expiresInSeconds: 3600,
      role: 'OPERATOR',
      branchId: BRANCH_UUID_1,
      branchName: 'Sucursal Matriz Quito',
      branchCode: 'SUC-01',
      username: 'operator1',
    })

    renderWithProviders(<AnalyticsDashboard />, { queryClient })

    await waitFor(() => {
      expect(
        screen.getByRole('tab', { name: /Sales Trend/i }),
      ).toBeInTheDocument()
    })

    // Corporate Board tab should not be present for OPERATOR
    expect(
      screen.queryByRole('tab', { name: /Corporate Board/i }),
    ).not.toBeInTheDocument()
  })
})
