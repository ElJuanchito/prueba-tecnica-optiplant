import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import {
  createTestQueryClient,
  renderWithProviders,
} from '@/test/test-utils.tsx'
import { AuditTable } from '../components/AuditTable.tsx'
import { IamDashboard } from '../components/IamDashboard.tsx'
import { auditService } from '../services/audit.service.ts'
import { userService } from '../services/user.service.ts'
import { branchService } from '../services/branch.service.ts'
import { alertService } from '@/features/notifications/services/alert.service.ts'
import { queryKeys } from '@/lib/query-keys.ts'

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

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'
const VALID_UUID_3 = 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33'
const VALID_UUID_4 = 'd0eebc99-9c0b-4ef8-bb6d-6bb9bd380a44'

describe('Audit and Dashboard Components', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(userService, 'listUsers').mockResolvedValue({
      content: [],
      totalElements: 0,
      page: 0,
      size: 10,
    })
    vi.spyOn(branchService, 'listBranches').mockResolvedValue({
      content: [],
      totalElements: 0,
      page: 0,
      size: 10,
    })
    vi.spyOn(alertService, 'listAlerts').mockResolvedValue({
      content: [],
      totalElements: 0,
      page: 0,
      size: 10,
    })
  })

  it('renders AuditTable with logs and payload before/after', async () => {
    vi.spyOn(auditService, 'listAuditLogs').mockResolvedValueOnce({
      content: [
        {
          externalId: VALID_UUID_3,
          actorUserId: VALID_UUID_1,
          branchId: null,
          action: 'USER_CREATE',
          entityName: 'UserAccount',
          entityId: VALID_UUID_4,
          payloadBefore: null,
          payloadAfter: '{"username":"newuser"}',
          ipAddress: '192.168.1.1',
          createdAt: '2026-08-28T05:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 15,
    })

    renderWithProviders(<AuditTable />)

    expect(await screen.findByText('USER_CREATE')).toBeInTheDocument()
    expect(screen.getByText('UserAccount')).toBeInTheDocument()
    expect(screen.getByText('192.168.1.1')).toBeInTheDocument()
    expect(screen.getByText(/newuser/)).toBeInTheDocument()
  })

  it('renders IamDashboard with role-appropriate tabs for ADMIN', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token-admin',
      refreshToken: 'ref-admin',
      expiresInSeconds: 900,
      role: 'ADMIN',
      branchId: null,
      username: 'admin',
    })

    renderWithProviders(<IamDashboard />, { queryClient })

    expect(screen.getAllByText('OptiPlant')[0]).toBeInTheDocument()
    expect(screen.getByText('admin')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /users/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /branches/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /audit log/i })).toBeInTheDocument()
  })

  it('renders IamDashboard with restricted tabs for BRANCH_MANAGER and displays branch name', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token-bm',
      refreshToken: 'ref-bm',
      expiresInSeconds: 900,
      role: 'BRANCH_MANAGER',
      branchId: VALID_UUID_2,
      branchName: 'Sede Bogotá',
      branchCode: 'BOG-01',
      username: 'mgr_bogota',
    })

    renderWithProviders(<IamDashboard />, { queryClient })

    expect(screen.getByText('Sede Bogotá (BOG-01)')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /users/i })).toBeInTheDocument()
    expect(
      screen.queryByRole('tab', { name: /branches/i }),
    ).not.toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /audit log/i })).toBeInTheDocument()
  })

  it('renders IamDashboard with restricted view for OPERATOR and displays assigned branch', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token-op',
      refreshToken: 'ref-op',
      expiresInSeconds: 900,
      role: 'OPERATOR',
      branchId: VALID_UUID_2,
      branchName: 'Sede Medellín',
      branchCode: 'MED-01',
      username: 'operator_juan',
    })

    renderWithProviders(<IamDashboard />, { queryClient })

    expect(screen.getByText('Plant Operator Session')).toBeInTheDocument()
    expect(
      screen.getAllByText('Sede Medellín (MED-01)').length,
    ).toBeGreaterThan(0)
    expect(
      screen.queryByRole('tab', { name: /users/i }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('tab', { name: /branches/i }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('tab', { name: /audit log/i }),
    ).not.toBeInTheDocument()
    expect(screen.queryByText('Total Users')).not.toBeInTheDocument()
  })
})
