import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  createTestQueryClient,
  renderWithProviders,
} from '@/test/test-utils.tsx'
import { CatalogDashboard } from '../components/CatalogDashboard.tsx'
import { productService } from '../services/product.service.ts'
import { categoryService } from '../services/category.service.ts'
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

describe('Catalog Dashboard Component Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(productService, 'listProducts').mockResolvedValue({
      content: [],
      totalElements: 12,
      page: 0,
      size: 10,
    })
    vi.spyOn(categoryService, 'listCategories').mockResolvedValue({
      content: [],
      totalElements: 4,
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

  it('renders CatalogDashboard with summary stats and tabs', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token-admin',
      refreshToken: 'ref-admin',
      expiresInSeconds: 900,
      role: 'ADMIN',
      branchId: null,
      username: 'admin_user',
    })

    renderWithProviders(<CatalogDashboard />, { queryClient })

    expect(screen.getAllByText('OptiPlant')[0]).toBeInTheDocument()
    expect(screen.getByText('admin_user')).toBeInTheDocument()
    expect(await screen.findByText('12')).toBeInTheDocument()
    expect(await screen.findByText('4')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /products/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /categories/i })).toBeInTheDocument()
  })

  it('allows switching to Categories tab', async () => {
    const user = userEvent.setup()
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(queryKeys.auth.session, {
      accessToken: 'token-op',
      refreshToken: 'ref-op',
      expiresInSeconds: 900,
      role: 'OPERATOR',
      branchId: null,
      username: 'operator_test',
    })

    renderWithProviders(<CatalogDashboard />, { queryClient })

    const categoriesTab = screen.getByRole('tab', { name: /categories/i })
    await user.click(categoriesTab)

    expect(
      screen.getByPlaceholderText(/search category by name/i),
    ).toBeInTheDocument()
  })
})
