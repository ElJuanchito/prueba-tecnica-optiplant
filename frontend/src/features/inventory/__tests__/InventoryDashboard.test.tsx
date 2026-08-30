import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  createTestQueryClient,
  renderWithProviders,
} from '@/test/test-utils.tsx'
import { InventoryDashboard } from '../components/InventoryDashboard.tsx'
import { inventoryService } from '../services/inventory.service.ts'
import { alertService } from '@/features/notifications/services/alert.service.ts'
import { productService } from '@/features/catalog/services/product.service.ts'
import { userService } from '@/features/iam/services/user.service.ts'
import { queryKeys } from '@/lib/query-keys.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'

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

describe('InventoryDashboard Component Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()

    vi.spyOn(userService, 'listUsers').mockResolvedValue({
      content: [],
      totalElements: 0,
      page: 0,
      size: 10,
    })

    vi.spyOn(productService, 'listProducts').mockResolvedValue({
      content: [],
      totalElements: 5,
      page: 0,
      size: 10,
    })

    vi.spyOn(inventoryService, 'listStock').mockResolvedValue({
      content: [
        {
          productExternalId: VALID_UUID_1,
          sku: 'SKU-100',
          name: 'Insecticida Alfa',
          currentStock: 50,
          reservedStock: 0,
          inTransitStock: 0,
          availableStock: 50,
          minStockThreshold: 10,
          averageCost: 20.0,
          lastUpdatedAt: '2026-08-29T00:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 100,
    })

    vi.spyOn(inventoryService, 'listKardex').mockResolvedValue({
      content: [],
      totalElements: 0,
      page: 0,
      size: 20,
    })

    vi.spyOn(alertService, 'listAlerts').mockResolvedValue({
      content: [],
      totalElements: 0,
      page: 0,
      size: 100,
    })
  })

  it('renders summary statistics and tabs for BRANCH_MANAGER', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'mock-token',
      refreshToken: 'mock-refresh',
      expiresInSeconds: 3600,
      role: 'BRANCH_MANAGER',
      branchId: VALID_UUID_1,
      branchName: 'Sucursal Central',
      branchCode: 'CEN-01',
      username: 'manager_central',
    })

    renderWithProviders(<InventoryDashboard />, { queryClient })

    expect(
      await screen.findByText('Total Products in Stock'),
    ).toBeInTheDocument()
    expect(screen.getByText('Critical / Low Stock')).toBeInTheDocument()
    expect(screen.getByText('Local Stock Valuation')).toBeInTheDocument()
    expect(screen.getByText('Operational Alerts')).toBeInTheDocument()

    // Header info
    expect(screen.getByText('manager_central')).toBeInTheDocument()
    expect(screen.getByText('BRANCH_MANAGER')).toBeInTheDocument()
    expect(screen.getByText(/Sucursal Central \(CEN-01\)/i)).toBeInTheDocument()

    // Tabs
    expect(
      screen.getByRole('tab', { name: /Stock Balances/i }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('tab', { name: /Kardex Ledger/i }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('tab', { name: /Alerts & Notifications/i }),
    ).toBeInTheDocument()
  })

  it('switches to Kardex and Alerts tabs when clicked by authorized manager', async () => {
    const user = userEvent.setup()
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'mock-token',
      refreshToken: 'mock-refresh',
      expiresInSeconds: 3600,
      role: 'ADMIN',
      branchId: null,
      username: 'admin_corporate',
    })

    renderWithProviders(<InventoryDashboard />, { queryClient })

    const kardexTab = await screen.findByRole('tab', { name: /Kardex Ledger/i })
    await user.click(kardexTab)

    expect(
      await screen.findByText('Immutable Kardex Audit Ledger'),
    ).toBeInTheDocument()
  })

  it('hides Kardex, Alerts tabs and IAM/Alerts navigation for OPERATOR role', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'mock-token',
      refreshToken: 'mock-refresh',
      expiresInSeconds: 3600,
      role: 'OPERATOR',
      branchId: VALID_UUID_1,
      branchName: 'Sucursal Central',
      username: 'operator_john',
    })

    renderWithProviders(<InventoryDashboard />, { queryClient })

    // Only stock tab should exist
    expect(
      await screen.findByRole('tab', { name: /Stock Balances/i }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('tab', { name: /Kardex Ledger/i })).toBeNull()
    expect(
      screen.queryByRole('tab', { name: /Alerts & Notifications/i }),
    ).toBeNull()

    // Navbar should NOT have IAM Governance or Alerts links
    expect(screen.queryByText('IAM & Governance')).toBeNull()
    expect(screen.queryByText('IAM Governance')).toBeNull()
    expect(screen.queryByText('Branch Governance')).toBeNull()
  })
})
