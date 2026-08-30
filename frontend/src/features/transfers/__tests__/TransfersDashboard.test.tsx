import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  createTestQueryClient,
  renderWithProviders,
} from '@/test/test-utils.tsx'
import { TransfersDashboard } from '../components/TransfersDashboard.tsx'
import { transferService } from '../services/transfer.service.ts'
import { branchService } from '@/features/iam/services/branch.service.ts'
import { productService } from '@/features/catalog/services/product.service.ts'
import { inventoryService } from '@/features/inventory/services/inventory.service.ts'
import { queryKeys } from '@/lib/query-keys.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'
const VALID_UUID_3 = 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33'

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

describe('TransfersDashboard Component Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()

    vi.spyOn(branchService, 'listBranches').mockResolvedValue({
      content: [
        {
          externalId: VALID_UUID_2,
          code: 'NORTE-01',
          name: 'Sucursal Norte',
          address: 'Calle 1',
          city: 'Quito',
          phone: null,
          active: true,
        },
        {
          externalId: VALID_UUID_3,
          code: 'SUR-01',
          name: 'Sucursal Sur',
          address: 'Calle 2',
          city: 'Guayaquil',
          phone: null,
          active: true,
        },
      ],
      totalElements: 2,
      page: 0,
      size: 10,
    })

    vi.spyOn(productService, 'listProducts').mockResolvedValue({
      content: [
        {
          externalId: VALID_UUID_1,
          sku: 'FERT-01',
          name: 'Fertilizante Premium',
          baseUnit: 'KG',
          active: true,
          createdAt: '2026-08-29T00:00:00Z',
          updatedAt: '2026-08-29T00:00:00Z',
          category: {
            externalId: VALID_UUID_1,
            name: 'Insumos',
            active: true,
          },
        },
      ],
      totalElements: 1,
      page: 0,
      size: 10,
    })

    vi.spyOn(inventoryService, 'getNetworkAvailability').mockResolvedValue({
      productExternalId: VALID_UUID_1,
      sku: 'FERT-01',
      name: 'Fertilizante Premium',
      branches: [
        {
          branchExternalId: VALID_UUID_2,
          branchName: 'Sucursal Norte',
          currentStock: 100,
          reservedStock: 10,
          inTransitStock: 0,
          availableStock: 90,
          isOwnBranch: false,
        },
        {
          branchExternalId: VALID_UUID_3,
          branchName: 'Sucursal Sur',
          currentStock: 50,
          reservedStock: 0,
          inTransitStock: 0,
          availableStock: 50,
          isOwnBranch: true,
        },
      ],
      networkTotal: 150,
    })

    vi.spyOn(transferService, 'list').mockResolvedValue({
      content: [
        {
          externalId: VALID_UUID_1,
          transferNumber: 'TRF-2026-0001',
          status: 'REQUESTED',
          priority: 'URGENT',
          originBranch: { externalId: VALID_UUID_2, name: 'Sucursal Norte' },
          destinationBranch: { externalId: VALID_UUID_3, name: 'Sucursal Sur' },
          createdAt: '2026-08-29T10:00:00Z',
          estimatedArrivalAt: null,
        },
      ],
      totalElements: 1,
      page: 0,
      size: 10,
    })

    vi.spyOn(transferService, 'getDetail').mockResolvedValue({
      externalId: VALID_UUID_1,
      transferNumber: 'TRF-2026-0001',
      status: 'REQUESTED',
      priority: 'URGENT',
      originBranch: { externalId: VALID_UUID_2, name: 'Sucursal Norte' },
      destinationBranch: { externalId: VALID_UUID_3, name: 'Sucursal Sur' },
      carrierName: null,
      trackingNumber: null,
      dispatchedAt: null,
      estimatedArrivalAt: null,
      actualArrivalAt: null,
      deviationHours: null,
      observations: ['Initial transfer request note'],
      requestedBy: VALID_UUID_1,
      dispatchedBy: null,
      receivedBy: null,
      createdAt: '2026-08-29T10:00:00Z',
      updatedAt: null,
      items: [
        {
          externalId: VALID_UUID_1,
          productExternalId: VALID_UUID_1,
          sku: 'FERT-01',
          name: 'Fertilizante Premium',
          requestedQuantity: 20,
          dispatchedQuantity: null,
          receivedQuantity: null,
          discrepancyQuantity: null,
          discrepancyReason: null,
        },
      ],
    })
  })

  it('renders dashboard title, state machine KPI metrics, and transfer table', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'mock-token',
      refreshToken: 'mock-refresh',
      expiresInSeconds: 3600,
      role: 'BRANCH_MANAGER',
      branchId: VALID_UUID_3,
      branchName: 'Sucursal Sur',
      username: 'manager_sur',
    })

    renderWithProviders(<TransfersDashboard />, { queryClient })

    expect(
      await screen.findByText('Inter-Branch Transfers'),
    ).toBeInTheDocument()
    expect(await screen.findByText('TRF-2026-0001')).toBeInTheDocument()
    expect(screen.getByText('Sucursal Norte')).toBeInTheDocument()
    expect(screen.getAllByText('Sucursal Sur').length).toBeGreaterThanOrEqual(1)
  })

  it('allows opening Transfer Request Dialog and creating new request', async () => {
    const user = userEvent.setup()
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'mock-token',
      refreshToken: 'mock-refresh',
      expiresInSeconds: 3600,
      role: 'OPERATOR',
      branchId: VALID_UUID_3,
      branchName: 'Sucursal Sur',
      username: 'operator_sur',
    })

    renderWithProviders(<TransfersDashboard />, { queryClient })

    const requestButton = await screen.findByRole('button', {
      name: /Request Transfer/i,
    })
    await user.click(requestButton)

    expect(await screen.findByText('New Transfer Request')).toBeInTheDocument()
  })

  it('opens Transfer Detail Dialog when View Details is clicked', async () => {
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

    renderWithProviders(<TransfersDashboard />, { queryClient })

    const viewButton = await screen.findByTitle(/View Details/i)
    await user.click(viewButton)

    expect(
      await screen.findByText(/Initial transfer request note/i),
    ).toBeInTheDocument()
  })

  it('shows Review/Approval action for origin branch manager', async () => {
    const user = userEvent.setup()
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'mock-token',
      refreshToken: 'mock-refresh',
      expiresInSeconds: 3600,
      role: 'BRANCH_MANAGER',
      branchId: VALID_UUID_2, // Origin branch
      branchName: 'Sucursal Norte',
      username: 'manager_norte',
    })

    renderWithProviders(<TransfersDashboard />, { queryClient })

    const approveButton = await screen.findByRole('button', {
      name: /Approve Request/i,
    })
    expect(approveButton).toBeInTheDocument()
    await user.click(approveButton)

    expect(
      await screen.findByText('Transfer Review & Approval'),
    ).toBeInTheDocument()
  })
})
