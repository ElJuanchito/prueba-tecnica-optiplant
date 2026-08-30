import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  createTestQueryClient,
  renderWithProviders,
} from '@/test/test-utils.tsx'
import { CustomersDashboard } from '../components/CustomersDashboard.tsx'
import { customerService } from '../services/customer.service.ts'
import { authService } from '@/features/iam/services/auth.service.ts'
import { queryKeys } from '@/lib/query-keys.ts'
import type { SessionData } from '@/lib/api-client.ts'
import type { UserRole } from '@/lib/permissions.ts'

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

function createMockSession(role: UserRole, username = 'testuser'): SessionData {
  return {
    accessToken: 'test-access-token',
    refreshToken: 'test-refresh-token',
    expiresInSeconds: 3600,
    role,
    branchId: VALID_UUID_2,
    branchName: 'Sucursal Matriz',
    branchCode: 'MAT',
    username,
  }
}

describe('CustomersDashboard Component Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  const mockCustomer1 = {
    externalId: VALID_UUID_1,
    name: 'Agrícola San Pedro S.A.',
    taxId: '1790012345001',
    email: 'contacto@sanpedro.com',
    phone: '+593 99 123 4567',
    address: 'Km 14.5 Vía a Daule',
    active: true,
    createdAt: '2026-08-30T10:00:00Z',
    updatedAt: '2026-08-30T10:00:00Z',
  }

  const mockCustomer2 = {
    externalId: VALID_UUID_2,
    name: 'Hacienda El Rocío',
    taxId: '0990098765001',
    email: 'info@elrocio.ec',
    phone: '+593 4 2345678',
    address: 'Sector Los Lojas',
    active: false,
    createdAt: '2026-08-29T10:00:00Z',
    updatedAt: '2026-08-30T09:00:00Z',
  }

  const mockCustomersPage = {
    content: [mockCustomer1, mockCustomer2],
    totalElements: 2,
    page: 0,
    size: 15,
  }

  const mockSalesHistoryPage = {
    content: [
      {
        externalId: VALID_UUID_3,
        invoiceNumber: 'VEN-2026-0001',
        status: 'COMPLETED' as const,
        branch: { externalId: VALID_UUID_2, name: 'Sucursal Matriz' },
        soldBy: { externalId: VALID_UUID_1, username: 'cajero1' },
        priceList: null,
        customerName: 'Agrícola San Pedro S.A.',
        totalAmount: 350.0,
        createdAt: '2026-08-30T10:00:00Z',
      },
    ],
    totalElements: 1,
    page: 0,
    size: 50,
    aggregates: {
      salesCount: 1,
      totalAmount: 350.0,
    },
  }

  it('renders customers dashboard and displays list of customers', async () => {
    vi.spyOn(customerService, 'list').mockResolvedValueOnce(mockCustomersPage)
    const session = createMockSession('ADMIN', 'admin')
    vi.spyOn(authService, 'getSession').mockReturnValue(session)

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, session)

    renderWithProviders(<CustomersDashboard />, { queryClient })

    expect(
      await screen.findByText('Agrícola San Pedro S.A.'),
    ).toBeInTheDocument()
    expect(screen.getByText('NIT: 1790012345001')).toBeInTheDocument()
    expect(screen.getByText('contacto@sanpedro.com')).toBeInTheDocument()
    expect(screen.getByText('Hacienda El Rocío')).toBeInTheDocument()
  })

  it('enforces RBAC: ADMIN sees create, edit and disable/enable buttons', async () => {
    vi.spyOn(customerService, 'list').mockResolvedValueOnce(mockCustomersPage)
    const session = createMockSession('ADMIN', 'admin')
    vi.spyOn(authService, 'getSession').mockReturnValue(session)

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, session)

    renderWithProviders(<CustomersDashboard />, { queryClient })

    expect(
      await screen.findByText('Agrícola San Pedro S.A.'),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: /New Customer|Nuevo Cliente/i }),
    ).toBeInTheDocument()
    expect(screen.getAllByTitle(/Edit|Editar/i).length).toBe(2)
    expect(screen.getByTitle(/Deactivate Customer|Desactivar Cliente/i)).toBeInTheDocument()
    expect(screen.getByTitle(/Reactivate Customer|Reactivar Cliente/i)).toBeInTheDocument()
  })

  it('enforces RBAC: BRANCH_MANAGER and OPERATOR see create & edit, but NO deactivate/reactivate', async () => {
    vi.spyOn(customerService, 'list').mockResolvedValue(mockCustomersPage)
    const session = createMockSession('OPERATOR', 'operador1')
    vi.spyOn(authService, 'getSession').mockReturnValue(session)

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, session)

    renderWithProviders(<CustomersDashboard />, { queryClient })

    expect(
      await screen.findByText('Agrícola San Pedro S.A.'),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: /New Customer|Nuevo Cliente/i }),
    ).toBeInTheDocument()
    expect(screen.getAllByTitle(/Edit|Editar/i).length).toBe(2)
    // Deactivate / Reactivate buttons MUST NOT be rendered
    expect(
      screen.queryByTitle(/Deactivate Customer|Desactivar Cliente/i),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByTitle(/Reactivate Customer|Reactivar Cliente/i),
    ).not.toBeInTheDocument()
  })

  it('creates a new customer via form dialog', async () => {
    const user = userEvent.setup()
    vi.spyOn(customerService, 'list').mockResolvedValue(mockCustomersPage)
    const session = createMockSession('BRANCH_MANAGER', 'manager1')
    vi.spyOn(authService, 'getSession').mockReturnValue(session)
    const createSpy = vi
      .spyOn(customerService, 'create')
      .mockResolvedValueOnce(mockCustomer1)

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, session)

    renderWithProviders(<CustomersDashboard />, { queryClient })

    const newCustomerBtn = await screen.findByRole('button', {
      name: /New Customer|Nuevo Cliente/i,
    })
    await user.click(newCustomerBtn)

    const nameInput = await screen.findByLabelText(/Name|Nombre/i)
    await user.type(nameInput, 'Nuevo Cliente Prueba')

    const taxIdInput = screen.getByLabelText(/Tax ID|Identificación/i)
    await user.type(taxIdInput, '1790099999001')

    const emailInput = screen.getByLabelText(/Email|Correo/i)
    await user.type(emailInput, 'test@cliente.com')

    const createBtn = screen.getByRole('button', { name: /Create|Crear/i })
    await user.click(createBtn)

    await waitFor(() => {
      expect(createSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Nuevo Cliente Prueba',
          taxId: '1790099999001',
          email: 'test@cliente.com',
        }),
      )
    })
  })

  it('edits an existing customer via form dialog', async () => {
    const user = userEvent.setup()
    vi.spyOn(customerService, 'list').mockResolvedValue(mockCustomersPage)
    const session = createMockSession('ADMIN', 'admin')
    vi.spyOn(authService, 'getSession').mockReturnValue(session)
    const editSpy = vi
      .spyOn(customerService, 'edit')
      .mockResolvedValueOnce({
        ...mockCustomer1,
        name: 'Agrícola San Pedro Modificado S.A.',
      })

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, session)

    renderWithProviders(<CustomersDashboard />, { queryClient })

    const editButtons = await screen.findAllByTitle(/Edit|Editar/i)
    await user.click(editButtons[0]!)

    const nameInput = await screen.findByDisplayValue('Agrícola San Pedro S.A.')
    await user.clear(nameInput)
    await user.type(nameInput, 'Agrícola San Pedro Modificado S.A.')

    const saveBtn = screen.getByRole('button', { name: /Save|Guardar/i })
    await user.click(saveBtn)

    await waitFor(() => {
      expect(editSpy).toHaveBeenCalledWith(
        VALID_UUID_1,
        expect.objectContaining({
          name: 'Agrícola San Pedro Modificado S.A.',
        }),
      )
    })
  })

  it('deactivates and reactivates a customer', async () => {
    const user = userEvent.setup()
    vi.spyOn(customerService, 'list').mockResolvedValue(mockCustomersPage)
    const session = createMockSession('ADMIN', 'admin')
    vi.spyOn(authService, 'getSession').mockReturnValue(session)
    const disableSpy = vi
      .spyOn(customerService, 'disable')
      .mockResolvedValueOnce({ ...mockCustomer1, active: false })
    const enableSpy = vi
      .spyOn(customerService, 'enable')
      .mockResolvedValueOnce({ ...mockCustomer2, active: true })

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, session)

    renderWithProviders(<CustomersDashboard />, { queryClient })

    const disableBtn = await screen.findByTitle(/Deactivate Customer|Desactivar Cliente/i)
    await user.click(disableBtn)

    await waitFor(() => {
      expect(disableSpy).toHaveBeenCalledWith(VALID_UUID_1)
    })

    const enableBtn = screen.getByTitle(/Reactivate Customer|Reactivar Cliente/i)
    await user.click(enableBtn)

    await waitFor(() => {
      expect(enableSpy).toHaveBeenCalledWith(VALID_UUID_2)
    })
  })

  it('opens customer detail dialog and displays purchase history with aggregates', async () => {
    const user = userEvent.setup()
    vi.spyOn(customerService, 'list').mockResolvedValue(mockCustomersPage)
    vi.spyOn(customerService, 'get').mockResolvedValueOnce(mockCustomer1)
    vi.spyOn(customerService, 'getSalesHistory').mockResolvedValueOnce(
      mockSalesHistoryPage,
    )
    const session = createMockSession('ADMIN', 'admin')
    vi.spyOn(authService, 'getSession').mockReturnValue(session)

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, session)

    renderWithProviders(<CustomersDashboard />, { queryClient })

    const detailButtons = await screen.findAllByTitle(/Details|Detalles/i)
    await user.click(detailButtons[0]!)

    expect(await screen.findByText('VEN-2026-0001')).toBeInTheDocument()
    expect(screen.getByText('Sucursal Matriz')).toBeInTheDocument()
    expect(screen.getAllByText('$350.00').length).toBeGreaterThan(0)
  })
})
