import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  createTestQueryClient,
  renderWithProviders,
} from '@/test/test-utils.tsx'
import { PricingDashboard } from '../components/PricingDashboard.tsx'
import { pricingService } from '../services/pricing.service.ts'
import { productService } from '@/features/catalog/services/product.service.ts'
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

describe('PricingDashboard Component Tests', () => {
  const mockPriceListsPage = {
    content: [
      {
        externalId: VALID_UUID_1,
        code: 'RETAIL',
        name: 'Lista General Minorista',
        description: 'Mostrador',
        maxDiscountPercent: 20,
        isDefault: true,
        active: true,
        createdAt: '2026-08-30T10:00:00Z',
        updatedAt: null,
      },
    ],
    totalElements: 1,
    page: 0,
    size: 10,
  }

  const mockPriceDetail = {
    externalId: VALID_UUID_1,
    code: 'RETAIL',
    name: 'Lista General Minorista',
    description: 'Mostrador',
    maxDiscountPercent: 20,
    isDefault: true,
    active: true,
    createdAt: '2026-08-30T10:00:00Z',
    updatedAt: null,
  }

  const mockPricesPage = {
    content: [
      {
        externalId: VALID_UUID_1,
        priceListExternalId: VALID_UUID_1,
        productExternalId: VALID_UUID_2,
        branchExternalId: null,
        unitPrice: 55.0,
        validFrom: '2026-08-30',
        validTo: null,
        createdAt: '2026-08-30T10:00:00Z',
      },
    ],
    totalElements: 1,
    page: 0,
    size: 15,
  }

  const mockProductsPage = {
    content: [
      {
        externalId: VALID_UUID_2,
        sku: 'FERT-01',
        name: 'Fertilizante Foliar',
        baseUnit: 'LT',
        active: true,
        category: null,
        createdAt: '2026-08-30T10:00:00Z',
        updatedAt: '2026-08-30T10:00:00Z',
      },
    ],
    totalElements: 1,
    page: 0,
    size: 100,
  }

  const mockBranchesPage = {
    content: [
      {
        externalId: VALID_UUID_1,
        code: 'MAT-01',
        name: 'Matriz Principal',
        address: 'Av. Principal',
        city: 'Quito',
        phone: null,
        active: true,
      },
    ],
    totalElements: 1,
    page: 0,
    size: 50,
  }

  beforeEach(() => {
    vi.restoreAllMocks()

    vi.spyOn(pricingService, 'listPriceLists').mockResolvedValue(
      mockPriceListsPage,
    )
    vi.spyOn(pricingService, 'getPriceList').mockResolvedValue(mockPriceDetail)
    vi.spyOn(pricingService, 'listPrices').mockResolvedValue(mockPricesPage)
    vi.spyOn(productService, 'listProducts').mockResolvedValue(mockProductsPage)
    vi.spyOn(branchService, 'listBranches').mockResolvedValue(mockBranchesPage)
  })

  it('renders pricing dashboard with price lists tab', async () => {
    vi.spyOn(pricingService, 'listPriceLists').mockResolvedValue(
      mockPriceListsPage,
    )

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token',
      role: 'ADMIN',
      username: 'admin',
    })
    queryClient.setQueryData(
      queryKeys.pricing.priceLists.list({ page: 0, size: 10 }),
      mockPriceListsPage,
    )

    renderWithProviders(<PricingDashboard />, { queryClient })

    expect(
      await screen.findByText('Lista General Minorista'),
    ).toBeInTheDocument()
    expect(screen.getByText('RETAIL')).toBeInTheDocument()
    expect(screen.getByText('20%')).toBeInTheDocument()
  })

  it('switches to rates tab and displays product prices', async () => {
    const user = userEvent.setup()
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token',
      role: 'ADMIN',
      username: 'admin',
    })

    renderWithProviders(<PricingDashboard />, { queryClient })

    const ratesTab = await screen.findByRole('tab', {
      name: /Rates & Product Prices/i,
    })
    await user.click(ratesTab)

    expect(await screen.findByText('Fertilizante Foliar')).toBeInTheDocument()
    expect(screen.getByText('55.00')).toBeInTheDocument()
  })

  it('switches to quote simulator tab and calculates real-time quote', async () => {
    const user = userEvent.setup()
    vi.spyOn(pricingService, 'quote').mockResolvedValueOnce({
      code: 'RETAIL',
      maxDiscountPercent: 20,
      items: [
        {
          productExternalId: VALID_UUID_2,
          listUnitPrice: 55.0,
          unitPrice: 49.5,
          subtotal: 99.0,
        },
      ],
    })

    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token',
      role: 'ADMIN',
      username: 'admin',
    })

    renderWithProviders(<PricingDashboard />, { queryClient })

    const quotesTab = await screen.findByRole('tab', {
      name: /Quote Calculator/i,
    })
    await user.click(quotesTab)

    expect(
      await screen.findByText(/Calculate real-time unit price/i),
    ).toBeInTheDocument()

    // Trigger product selection
    const selectTrigger = screen.getByRole('button', {
      name: /Select product to quote|Seleccionar producto a cotizar/i,
    })
    await user.click(selectTrigger)

    const productOption = await screen.findByText('Fertilizante Foliar')
    await user.click(productOption)

    const calculateButton = screen.getByRole('button', {
      name: /Calculate Quote/i,
    })
    await user.click(calculateButton)

    await waitFor(() => {
      expect(screen.getAllByText('$99.00').length).toBeGreaterThan(0)
    })
  })
})
