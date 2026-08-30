import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  createTestQueryClient,
  renderWithProviders,
} from '@/test/test-utils.tsx'
import { SalesDashboard } from '../components/SalesDashboard.tsx'
import { saleService } from '../services/sale.service.ts'
import { pricingService } from '@/features/pricing/services/pricing.service.ts'
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

describe('SalesDashboard Component Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()

    vi.spyOn(pricingService, 'listPriceLists').mockResolvedValue({
      content: [
        {
          externalId: VALID_UUID_2,
          code: 'RETAIL',
          name: 'Lista General Minorista',
          description: 'Venta mostrador',
          maxDiscountPercent: 20,
          isDefault: true,
          active: true,
          createdAt: '2026-08-30T10:00:00Z',
          updatedAt: null,
        },
      ],
      totalElements: 1,
      page: 0,
      size: 50,
    })

    vi.spyOn(productService, 'listProducts').mockResolvedValue({
      content: [
        {
          externalId: VALID_UUID_1,
          sku: 'FERT-01',
          name: 'Fertilizante NPK',
          baseUnit: 'KG',
          active: true,
          category: {
            externalId: VALID_UUID_2,
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

  const mockSalesPage = {
    content: [
      {
        externalId: VALID_UUID_1,
        invoiceNumber: 'VEN-2026-0001',
        status: 'COMPLETED' as const,
        branch: { externalId: VALID_UUID_2, name: 'Sucursal Matriz' },
        soldBy: { externalId: VALID_UUID_3, username: 'cajero1' },
        priceList: {
          externalId: VALID_UUID_2,
          code: 'RETAIL',
          maxDiscountPercent: 20,
        },
        customerName: 'Hacienda San José',
        totalAmount: 150.0,
        createdAt: '2026-08-30T10:00:00Z',
      },
    ],
    totalElements: 1,
    page: 0,
    size: 15,
    aggregates: {
      salesCount: 1,
      totalAmount: 150.0,
    },
  }

  const mockSaleDetail = {
    externalId: VALID_UUID_1,
    invoiceNumber: 'VEN-2026-0001',
    status: 'COMPLETED' as const,
    branch: { externalId: VALID_UUID_2, name: 'Sucursal Matriz' },
    soldBy: { externalId: VALID_UUID_3, username: 'cajero1' },
    priceList: {
      externalId: VALID_UUID_2,
      code: 'RETAIL',
      maxDiscountPercent: 20,
    },
    customerName: 'Hacienda San José',
    customerTaxId: '1790012345001',
    subtotal: 150.0,
    discountAmount: 0.0,
    taxAmount: 0.0,
    totalAmount: 150.0,
    notes: 'Entrega en bodega',
    cancellationReason: null,
    createdAt: '2026-08-30T10:00:00Z',
    items: [
      {
        externalId: VALID_UUID_3,
        productExternalId: VALID_UUID_1,
        sku: 'FERT-01',
        name: 'Fertilizante NPK',
        quantity: 3,
        listUnitPrice: 50.0,
        unitPrice: 50.0,
        discountPercent: 0,
        subtotal: 150.0,
      },
    ],
  }

  it('renders sales dashboard and displays list of sales with metrics', async () => {
    vi.spyOn(saleService, 'list').mockResolvedValueOnce(mockSalesPage)

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token',
      role: 'ADMIN',
      username: 'admin',
      branchId: VALID_UUID_2,
      branchName: 'Sucursal Matriz',
    })

    renderWithProviders(<SalesDashboard />, { queryClient })

    expect(await screen.findByText('VEN-2026-0001')).toBeInTheDocument()
    expect(screen.getByText('Hacienda San José')).toBeInTheDocument()
    expect(screen.getAllByText('$150.00').length).toBeGreaterThan(0)
  })

  it('opens sale detail dialog when details button is clicked', async () => {
    const user = userEvent.setup()
    vi.spyOn(saleService, 'list').mockResolvedValueOnce(mockSalesPage)
    vi.spyOn(saleService, 'getDetail').mockResolvedValueOnce(mockSaleDetail)

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token',
      role: 'ADMIN',
      username: 'admin',
      branchId: VALID_UUID_2,
    })

    renderWithProviders(<SalesDashboard />, { queryClient })

    const detailsButton = await screen.findByTitle('Details')
    await user.click(detailsButton)

    expect(await screen.findByText('1790012345001', { exact: false })).toBeInTheDocument()
    expect(screen.getByText('Entrega en bodega', { exact: false })).toBeInTheDocument()
  })

  it('opens void dialog and cancels sale with reason', async () => {
    const user = userEvent.setup()
    vi.spyOn(saleService, 'list').mockResolvedValue(mockSalesPage)
    const cancelSpy = vi
      .spyOn(saleService, 'cancel')
      .mockResolvedValueOnce({
        ...mockSaleDetail,
        status: 'CANCELLED',
        cancellationReason: 'Devolución de mercadería',
      })

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token',
      role: 'ADMIN',
      username: 'admin',
      branchId: VALID_UUID_2,
    })

    renderWithProviders(<SalesDashboard />, { queryClient })

    const voidButton = await screen.findByTitle('Void Sale')
    await user.click(voidButton)

    const reasonInput = await screen.findByLabelText(/State the mandatory cancellation reason/i)
    await user.type(reasonInput, 'Devolución de mercadería')

    const confirmButton = screen.getByRole('button', { name: /Confirm Void/i })
    await user.click(confirmButton)

    await waitFor(() => {
      expect(cancelSpy).toHaveBeenCalledWith(VALID_UUID_1, {
        reason: 'Devolución de mercadería',
      })
    })
  })

  it('opens new sale dialog and adds product via central search bar', async () => {
    const user = userEvent.setup()
    vi.spyOn(saleService, 'list').mockResolvedValue(mockSalesPage)
    const registerSpy = vi
      .spyOn(saleService, 'register')
      .mockResolvedValueOnce(mockSaleDetail)

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token',
      role: 'ADMIN',
      username: 'admin',
      branchId: VALID_UUID_2,
    })

    renderWithProviders(<SalesDashboard />, { queryClient })

    const newSaleButton = await screen.findByRole('button', { name: /New Sale/i })
    await user.click(newSaleButton)

    // Customer Name
    const customerInput = await screen.findByLabelText(/Customer Name/i)
    await user.type(customerInput, 'Cliente Prueba S.A.')

    // Empty state should be visible initially
    expect(
      screen.getByText(/No products added to this sale/i),
    ).toBeInTheDocument()

    // Add product via central search bar
    const searchTrigger = screen.getByRole('button', {
      name: /Type to search by SKU or name and add|Escriba para buscar por SKU/i,
    })
    await user.click(searchTrigger)

    const productOption = await screen.findByText('Fertilizante NPK')
    await user.click(productOption)

    // Verify product card is displayed
    expect(screen.getByText('FERT-01')).toBeInTheDocument()

    // Submit sale
    const submitButton = screen.getByRole('button', { name: /Issue Receipt/i })
    await user.click(submitButton)

    await waitFor(() => {
      expect(registerSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          customerName: 'Cliente Prueba S.A.',
          items: [
            expect.objectContaining({
              productExternalId: VALID_UUID_1,
              quantity: 1,
            }),
          ],
        }),
      )
    })
  })
})
