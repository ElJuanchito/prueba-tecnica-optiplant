import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  createTestQueryClient,
  renderWithProviders,
} from '@/test/test-utils.tsx'
import { AppLayout } from '../AppLayout.tsx'
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
      onClick,
    }: {
      children: React.ReactNode
      to?: string
      className?: string
      onClick?: () => void
    }) => (
      <a href={to ?? '#'} className={className} onClick={onClick}>
        {children}
      </a>
    ),
    useRouterState: () => ({
      location: {
        pathname: '/inventory',
      },
    }),
  }
})

describe('AppLayout Collapsible Sidebar Component Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('renders sidebar navigation items with RBAC permissions for ADMIN', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'mock-token',
      refreshToken: 'mock-refresh',
      expiresInSeconds: 3600,
      role: 'ADMIN',
      branchId: null,
      username: 'admin.corp',
    })

    renderWithProviders(
      <AppLayout activeModule="inventory">
        <div>Main Dashboard Content</div>
      </AppLayout>,
      { queryClient, language: 'es' },
    )

    expect(screen.getAllByText('OptiPlant')[0]).toBeInTheDocument()
    expect(screen.getByText('Módulos del Sistema')).toBeInTheDocument()

    // ADMIN sees IAM, Inventory, Alerts, Catalog Master
    expect(screen.getByText('Gobernanza e IAM')).toBeInTheDocument()
    expect(screen.getByText('Inventario y Stock')).toBeInTheDocument()
    expect(screen.getByText('Centro de Alertas')).toBeInTheDocument()
    expect(screen.getByText('Catálogo Maestro')).toBeInTheDocument()

    // User profile card
    expect(screen.getByText('admin.corp')).toBeInTheDocument()
    expect(screen.getByText('ADMIN')).toBeInTheDocument()

    // Main content
    expect(screen.getByText('Main Dashboard Content')).toBeInTheDocument()
  })

  it('restricts unauthorized modules for OPERATOR', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'mock-token',
      refreshToken: 'mock-refresh',
      expiresInSeconds: 3600,
      role: 'OPERATOR',
      branchId: VALID_UUID_1,
      branchName: 'Sucursal Central',
      username: 'operador_1',
    })

    renderWithProviders(
      <AppLayout activeModule="inventory">
        <div>Operator View</div>
      </AppLayout>,
      { queryClient, language: 'es' },
    )

    // OPERATOR only sees Inventory & Catalog Browser
    expect(screen.getByText('Inventario y Stock')).toBeInTheDocument()
    expect(screen.getByText('Explorador de Catálogo')).toBeInTheDocument()

    // IAM and Alerts are forbidden and hidden
    expect(screen.queryByText('Gobernanza e IAM')).toBeNull()
    expect(screen.queryByText('Centro de Alertas')).toBeNull()
  })

  it('toggles sidebar collapse/expand on button click and persists to localStorage', async () => {
    const user = userEvent.setup()
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'mock-token',
      refreshToken: 'mock-refresh',
      expiresInSeconds: 3600,
      role: 'ADMIN',
      branchId: null,
      username: 'admin.corp',
    })

    renderWithProviders(
      <AppLayout activeModule="inventory">
        <div>Content</div>
      </AppLayout>,
      { queryClient, language: 'es' },
    )

    const toggleBtn = screen.getByRole('button', { name: /Colapsar menú/i })
    await user.click(toggleBtn)

    expect(localStorage.getItem('optiplant_sidebar_collapsed')).toBe('true')
  })
})
