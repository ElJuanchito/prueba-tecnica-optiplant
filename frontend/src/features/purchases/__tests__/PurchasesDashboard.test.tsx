import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  createTestQueryClient,
  renderWithProviders,
} from '@/test/test-utils.tsx'
import { PurchasesDashboard } from '../components/PurchasesDashboard.tsx'
import { purchasesService } from '../services/purchases.service.ts'
import { productService } from '@/features/catalog/services/product.service.ts'
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

describe('PurchasesDashboard Component Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()

    vi.spyOn(productService, 'listProducts').mockResolvedValue({
      content: [
        {
          externalId: VALID_UUID_2,
          sku: 'FERT-01',
          name: 'Fertilizante Foliar 1L',
          baseUnit: 'L',
          active: true,
          category: {
            externalId: VALID_UUID_3,
            name: 'Nutrición',
            active: true,
          },
          createdAt: '2026-08-30T10:00:00Z',
          updatedAt: '2026-08-30T10:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 100,
    })
  })

  const mockSupplier = {
    externalId: VALID_UUID_1,
    taxId: '1790012345001',
    name: 'AgroQuímica del Norte S.A.',
    contactName: 'Ing. Carlos Mendoza',
    email: 'ventas@agronorte.com',
    phone: '+593 99 123 4567',
    address: 'Av. Panamericana Km 12',
    active: true,
    createdAt: '2026-08-30T10:00:00Z',
    updatedAt: null,
  }

  const mockSuppliersPage = {
    content: [mockSupplier],
    totalElements: 1,
    page: 0,
    size: 15,
  }

  const mockOrderSummary = {
    externalId: VALID_UUID_1,
    orderNumber: 'OC-2026-0001',
    status: 'PENDING' as const,
    branch: { externalId: VALID_UUID_2, name: 'Sucursal Matriz' },
    supplier: {
      externalId: VALID_UUID_1,
      taxId: '1790012345001',
      name: 'AgroQuímica del Norte S.A.',
    },
    totalAmount: 950.0,
    createdAt: '2026-08-30T10:00:00Z',
    receivedAt: null,
  }

  const mockOrdersPage = {
    content: [mockOrderSummary],
    totalElements: 1,
    page: 0,
    size: 15,
  }

  const mockOrderDetail = {
    externalId: VALID_UUID_1,
    orderNumber: 'OC-2026-0001',
    status: 'PENDING' as const,
    branch: { externalId: VALID_UUID_2, name: 'Sucursal Matriz' },
    supplier: {
      externalId: VALID_UUID_1,
      taxId: '1790012345001',
      name: 'AgroQuímica del Norte S.A.',
    },
    createdBy: { externalId: VALID_UUID_2, username: 'admin' },
    paymentTerms: '30 días',
    totalAmount: 950.0,
    notes: 'Despacho urgente',
    cancellationReason: null,
    createdAt: '2026-08-30T10:00:00Z',
    updatedAt: null,
    receivedAt: null,
    items: [
      {
        externalId: VALID_UUID_3,
        productExternalId: VALID_UUID_2,
        sku: 'FERT-01',
        name: 'Fertilizante Foliar 1L',
        orderedQuantity: 100,
        receivedQuantity: 0,
        pendingQuantity: 100,
        unitCost: 10.0,
        discountPercent: 5.0,
        effectiveUnitCost: 9.5,
        subtotal: 950.0,
      },
    ],
  }

  it('renders purchases dashboard with orders and summary metrics', async () => {
    vi.spyOn(purchasesService, 'listOrders').mockResolvedValueOnce(
      mockOrdersPage,
    )
    vi.spyOn(purchasesService, 'listSuppliers').mockResolvedValueOnce(
      mockSuppliersPage,
    )

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token',
      role: 'ADMIN',
      username: 'admin',
      branchId: VALID_UUID_2,
      branchName: 'Sucursal Matriz',
    })

    renderWithProviders(<PurchasesDashboard />, { queryClient })

    expect(await screen.findByText('OC-2026-0001')).toBeInTheDocument()
    expect(screen.getByText('AgroQuímica del Norte S.A.')).toBeInTheDocument()
    expect(screen.getAllByText('$950.00').length).toBeGreaterThan(0)
  })

  it('opens order detail dialog when row is clicked', async () => {
    const user = userEvent.setup()
    vi.spyOn(purchasesService, 'listOrders').mockResolvedValueOnce(
      mockOrdersPage,
    )
    vi.spyOn(purchasesService, 'listSuppliers').mockResolvedValueOnce(
      mockSuppliersPage,
    )
    vi.spyOn(purchasesService, 'getOrderDetail').mockResolvedValue(
      mockOrderDetail,
    )

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token',
      role: 'ADMIN',
      username: 'admin',
      branchId: VALID_UUID_2,
    })

    renderWithProviders(<PurchasesDashboard />, { queryClient })

    const row = await screen.findByText('OC-2026-0001')
    await user.click(row)

    expect(await screen.findByText('Despacho urgente')).toBeInTheDocument()
    expect(screen.getByText('30 días')).toBeInTheDocument()
  })

  it('approves purchase order when approve action is confirmed', async () => {
    const user = userEvent.setup()
    vi.spyOn(purchasesService, 'listOrders').mockResolvedValue(mockOrdersPage)
    vi.spyOn(purchasesService, 'listSuppliers').mockResolvedValue(
      mockSuppliersPage,
    )
    const approveSpy = vi
      .spyOn(purchasesService, 'approveOrder')
      .mockResolvedValueOnce({
        ...mockOrderDetail,
        status: 'APPROVED',
      })

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token',
      role: 'ADMIN',
      username: 'admin',
      branchId: VALID_UUID_2,
    })

    renderWithProviders(<PurchasesDashboard />, { queryClient })

    const approveButton = await screen.findByTitle(
      /Aprobar Orden|Approve Order/i,
    )
    await user.click(approveButton)

    const confirmApproveButton = await screen.findByRole('button', {
      name: /Aprobar Orden|Approve Order/i,
    })
    await user.click(confirmApproveButton)

    await waitFor(() => {
      expect(approveSpy).toHaveBeenCalledWith(VALID_UUID_1)
    })
  })

  it('cancels purchase order with mandatory cancellation reason', async () => {
    const user = userEvent.setup()
    vi.spyOn(purchasesService, 'listOrders').mockResolvedValue(mockOrdersPage)
    vi.spyOn(purchasesService, 'listSuppliers').mockResolvedValue(
      mockSuppliersPage,
    )
    const cancelSpy = vi
      .spyOn(purchasesService, 'cancelOrder')
      .mockResolvedValueOnce({
        ...mockOrderDetail,
        status: 'CANCELLED',
        cancellationReason: 'Proveedor sin disponibilidad',
      })

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token',
      role: 'ADMIN',
      username: 'admin',
      branchId: VALID_UUID_2,
    })

    renderWithProviders(<PurchasesDashboard />, { queryClient })

    const cancelButton = await screen.findByTitle(
      /Cancelar Orden|Cancel Order/i,
    )
    await user.click(cancelButton)

    const reasonInput = await screen.findByPlaceholderText(
      /Supplier out of stock|Proveedor sin disponibilidad/i,
    )
    await user.type(reasonInput, 'Proveedor sin disponibilidad')

    const confirmButton = screen.getByRole('button', {
      name: /Confirmar Cancelación|Confirm Cancellation/i,
    })
    await user.click(confirmButton)

    await waitFor(() => {
      expect(cancelSpy).toHaveBeenCalledWith(VALID_UUID_1, {
        reason: 'Proveedor sin disponibilidad',
      })
    })
  })

  it('registers goods reception for approved purchase order', async () => {
    const user = userEvent.setup()
    const approvedOrderSummary = {
      ...mockOrderSummary,
      status: 'APPROVED' as const,
    }
    const approvedOrderDetail = {
      ...mockOrderDetail,
      status: 'APPROVED' as const,
    }

    vi.spyOn(purchasesService, 'listOrders').mockResolvedValue({
      content: [approvedOrderSummary],
      totalElements: 1,
      page: 0,
      size: 15,
    })
    vi.spyOn(purchasesService, 'listSuppliers').mockResolvedValue(
      mockSuppliersPage,
    )
    vi.spyOn(purchasesService, 'getOrderDetail').mockResolvedValue(
      approvedOrderDetail,
    )
    const receptionSpy = vi
      .spyOn(purchasesService, 'registerReception')
      .mockResolvedValueOnce({
        ...approvedOrderDetail,
        status: 'RECEIVED',
        receivedAt: '2026-08-30T12:00:00Z',
      })

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token',
      role: 'ADMIN',
      username: 'admin',
      branchId: VALID_UUID_2,
    })

    renderWithProviders(<PurchasesDashboard />, { queryClient })

    const receiveButton = await screen.findByTitle(
      /Registrar Recepción|Register Reception/i,
    )
    await user.click(receiveButton)

    expect(
      await screen.findByText(
        /Registrar Recepción de Mercancía|Register Goods Reception/i,
      ),
    ).toBeInTheDocument()

    const submitReception = screen.getByRole('button', {
      name: /Confirmar Recepción|Confirm Reception/i,
    })
    await user.click(submitReception)

    await waitFor(() => {
      expect(receptionSpy).toHaveBeenCalledWith(
        VALID_UUID_1,
        expect.objectContaining({
          items: [
            expect.objectContaining({
              itemExternalId: VALID_UUID_3,
              receivedQuantity: 100,
            }),
          ],
        }),
      )
    })
  })

  it('switches to suppliers tab and displays supplier directory', async () => {
    const user = userEvent.setup()
    vi.spyOn(purchasesService, 'listOrders').mockResolvedValue(mockOrdersPage)
    vi.spyOn(purchasesService, 'listSuppliers').mockResolvedValue(
      mockSuppliersPage,
    )

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token',
      role: 'ADMIN',
      username: 'admin',
      branchId: VALID_UUID_2,
    })

    renderWithProviders(<PurchasesDashboard />, { queryClient })

    const suppliersTab = await screen.findByRole('tab', {
      name: /Proveedores|Suppliers/i,
    })
    await user.click(suppliersTab)

    expect(await screen.findByText('1790012345001')).toBeInTheDocument()
    expect(screen.getByText('Ing. Carlos Mendoza')).toBeInTheDocument()
  })

  it('restricts supplier management and approval actions for OPERATOR role', async () => {
    vi.spyOn(purchasesService, 'listOrders').mockResolvedValue(mockOrdersPage)
    vi.spyOn(purchasesService, 'listSuppliers').mockResolvedValue(
      mockSuppliersPage,
    )

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token',
      role: 'OPERATOR',
      username: 'operator1',
      branchId: VALID_UUID_2,
    })

    renderWithProviders(<PurchasesDashboard />, { queryClient })

    expect(await screen.findByText('OC-2026-0001')).toBeInTheDocument()

    // OPERATOR cannot approve or cancel orders
    expect(
      screen.queryByTitle(/Aprobar Orden|Approve Order/i),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByTitle(/Cancelar Orden|Cancel Order/i),
    ).not.toBeInTheDocument()
  })
})
