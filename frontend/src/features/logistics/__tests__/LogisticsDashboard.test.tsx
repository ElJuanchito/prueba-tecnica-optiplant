import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  createTestQueryClient,
  renderWithProviders,
} from '@/test/test-utils.tsx'
import { LogisticsDashboard } from '../components/LogisticsDashboard.tsx'
import { logisticsService } from '../services/logistics.service.ts'
import { branchService } from '@/features/iam/services/branch.service.ts'
import { queryKeys } from '@/lib/query-keys.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'

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

describe('LogisticsDashboard Component Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()

    vi.spyOn(branchService, 'listBranches').mockResolvedValue({
      content: [
        {
          externalId: VALID_UUID_1,
          code: 'NORTE-01',
          name: 'Sucursal Norte',
          address: 'Av. Norte 123',
          city: 'Quito',
          phone: null,
          active: true,
        },
        {
          externalId: VALID_UUID_2,
          code: 'SUR-01',
          name: 'Sucursal Sur',
          address: 'Av. Sur 456',
          city: 'Guayaquil',
          phone: null,
          active: true,
        },
      ],
      totalElements: 2,
      page: 0,
      size: 10,
    })

    vi.spyOn(logisticsService, 'listActiveTransfers').mockResolvedValue({
      content: [
        {
          transferExternalId: VALID_UUID_1,
          transferNumber: 'TRF-2026-0001',
          status: 'IN_TRANSIT',
          originBranch: { externalId: VALID_UUID_1, name: 'Sucursal Norte' },
          destinationBranch: { externalId: VALID_UUID_2, name: 'Sucursal Sur' },
          priority: 'URGENT',
          itemCount: 2,
          totalQuantity: 50,
          estimatedArrivalAt: '2026-08-29T20:00:00Z',
          isDelayed: true,
        },
      ],
      totalElements: 1,
      page: 0,
      size: 10,
    })

    vi.spyOn(logisticsService, 'getComplianceReport').mockResolvedValue({
      content: [
        {
          key: 'ROUTE_1_2',
          label: 'Sucursal Norte → Sucursal Sur',
          deliveredCount: 15,
          onTimeCount: 14,
          onTimePercentage: 93.3,
          averageDeviationHours: 0.8,
          unmeasuredCount: 1,
        },
      ],
      totalElements: 1,
      page: 0,
      size: 10,
    })

    vi.spyOn(logisticsService, 'listRoutes').mockResolvedValue({
      content: [
        {
          externalId: VALID_UUID_1,
          originBranch: { externalId: VALID_UUID_1, name: 'Sucursal Norte' },
          destinationBranch: { externalId: VALID_UUID_2, name: 'Sucursal Sur' },
          estimatedDurationHours: 12.0,
          transportCost: 200.0,
          priorityLevel: 'STANDARD',
          active: true,
          createdAt: '2026-08-29T00:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 10,
    })
  })

  it('renders Active Shipments Monitor on initial load (CU-LOG-02)', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'mock-token',
      refreshToken: 'mock-refresh',
      expiresInSeconds: 3600,
      role: 'BRANCH_MANAGER',
      branchId: VALID_UUID_1,
      branchName: 'Sucursal Norte',
      username: 'manager_norte',
    })

    renderWithProviders(<LogisticsDashboard />, { queryClient })

    expect(
      await screen.findByText('Logistics Monitoring & Route Control'),
    ).toBeInTheDocument()
    expect(await screen.findByText('TRF-2026-0001')).toBeInTheDocument()
    expect(screen.getByText(/Delayed/i)).toBeInTheDocument()
  })

  it('switches to Compliance Report tab and renders metrics (CU-LOG-03)', async () => {
    const user = userEvent.setup()
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'mock-token',
      refreshToken: 'mock-refresh',
      expiresInSeconds: 3600,
      role: 'BRANCH_MANAGER',
      branchId: VALID_UUID_1,
      branchName: 'Sucursal Norte',
      username: 'manager_norte',
    })

    renderWithProviders(<LogisticsDashboard />, { queryClient })

    const complianceTab = await screen.findByRole('tab', {
      name: /Compliance Report/i,
    })
    await user.click(complianceTab)

    expect(
      await screen.findByText('Sucursal Norte → Sucursal Sur'),
    ).toBeInTheDocument()
    expect(screen.getByText('93.3%')).toBeInTheDocument()
  })

  it('displays Parametrized Routes tab for ADMIN user (CU-LOG-01)', async () => {
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

    renderWithProviders(<LogisticsDashboard />, { queryClient })

    const routesTab = await screen.findByRole('tab', {
      name: /Parametrized Routes/i,
    })
    expect(routesTab).toBeInTheDocument()
    await user.click(routesTab)

    expect(await screen.findByText('New Logistics Route')).toBeInTheDocument()
  })

  it('hides Parametrized Routes tab for non-admin roles', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'mock-token',
      refreshToken: 'mock-refresh',
      expiresInSeconds: 3600,
      role: 'BRANCH_MANAGER',
      branchId: VALID_UUID_1,
      branchName: 'Sucursal Norte',
      username: 'manager_norte',
    })

    renderWithProviders(<LogisticsDashboard />, { queryClient })

    expect(
      screen.queryByRole('tab', { name: /Parametrized Routes/i }),
    ).toBeNull()
  })
})
