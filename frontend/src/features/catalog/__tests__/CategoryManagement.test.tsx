import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/test-utils.tsx'
import { CategoryTable } from '../components/CategoryTable.tsx'
import { categoryService } from '../services/category.service.ts'
import { ApiError } from '@/lib/api-client.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'

describe('Category Management Component Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('renders category listing with name, status, and active product count', async () => {
    vi.spyOn(categoryService, 'listCategories').mockResolvedValueOnce({
      content: [
        {
          externalId: VALID_UUID_1,
          name: 'Fertilizantes',
          description: 'Nutrición del suelo',
          active: true,
          activeProductCount: 3,
          createdAt: '2026-08-28T00:00:00Z',
          updatedAt: '2026-08-28T00:00:00Z',
        },
        {
          externalId: VALID_UUID_2,
          name: 'Herbicidas',
          description: null,
          active: false,
          activeProductCount: 0,
          createdAt: '2026-08-28T00:00:00Z',
          updatedAt: '2026-08-28T00:00:00Z',
        },
      ],
      totalElements: 2,
      page: 0,
      size: 10,
    })

    renderWithProviders(<CategoryTable currentActorRole="ADMIN" />)

    expect(await screen.findByText('Fertilizantes')).toBeInTheDocument()
    expect(screen.getByText('Nutrición del suelo')).toBeInTheDocument()
    expect(screen.getByText('Herbicidas')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()
    expect(screen.getByText('Disabled')).toBeInTheDocument()
  })

  it('allows ADMIN to create a new category and handles duplicate name error', async () => {
    const user = userEvent.setup()
    vi.spyOn(categoryService, 'listCategories').mockResolvedValue({
      content: [],
      totalElements: 0,
      page: 0,
      size: 10,
    })

    const createSpy = vi
      .spyOn(categoryService, 'createCategory')
      .mockRejectedValueOnce(
        new ApiError(
          409,
          'duplicate_category_name',
          'Category name already in use',
        ),
      )

    renderWithProviders(<CategoryTable currentActorRole="ADMIN" />)

    const newBtn = await screen.findByRole('button', { name: /new category/i })
    await user.click(newBtn)

    expect(
      screen.getByRole('heading', { name: /new product category/i }),
    ).toBeInTheDocument()

    const nameInput = screen.getByLabelText(/category name/i)
    await user.type(nameInput, 'Fertilizantes')

    const submitBtn = screen.getByRole('button', { name: /create category/i })
    await user.click(submitBtn)

    expect(createSpy).toHaveBeenCalledWith({
      name: 'Fertilizantes',
      description: null,
    })

    expect(await screen.findByText(/already exists/i)).toBeInTheDocument()
  })

  it('prevents disabling category if it has active products (R-04 rule guard modal)', async () => {
    const user = userEvent.setup()
    vi.spyOn(categoryService, 'listCategories').mockResolvedValueOnce({
      content: [
        {
          externalId: VALID_UUID_1,
          name: 'Fertilizantes',
          description: null,
          active: true,
          activeProductCount: 4,
          createdAt: '2026-08-28T00:00:00Z',
          updatedAt: '2026-08-28T00:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 10,
    })

    const disableSpy = vi.spyOn(categoryService, 'disableCategory')

    renderWithProviders(<CategoryTable currentActorRole="ADMIN" />)

    const disableBtn = await screen.findByRole('button', { name: /disable/i })
    await user.click(disableBtn)

    // Should open modal dialog and NOT call disableCategory API
    expect(disableSpy).not.toHaveBeenCalled()
    expect(
      await screen.findByRole('heading', {
        name: /cannot disable category/i,
      }),
    ).toBeInTheDocument()
    expect(screen.getByText('4 active products')).toBeInTheDocument()

    const understoodBtn = screen.getByRole('button', { name: /understood/i })
    await user.click(understoodBtn)
  })

  it('opens confirmation modal and disables category when active product count is 0', async () => {
    const user = userEvent.setup()
    vi.spyOn(categoryService, 'listCategories').mockResolvedValueOnce({
      content: [
        {
          externalId: VALID_UUID_1,
          name: 'Semillas',
          description: null,
          active: true,
          activeProductCount: 0,
          createdAt: '2026-08-28T00:00:00Z',
          updatedAt: '2026-08-28T00:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 10,
    })

    const disableSpy = vi
      .spyOn(categoryService, 'disableCategory')
      .mockResolvedValueOnce({
        externalId: VALID_UUID_1,
        name: 'Semillas',
        description: null,
        active: false,
        activeProductCount: 0,
        createdAt: '2026-08-28T00:00:00Z',
        updatedAt: '2026-08-28T00:00:00Z',
      })

    renderWithProviders(<CategoryTable currentActorRole="ADMIN" />)

    const disableBtn = await screen.findByRole('button', { name: /disable/i })
    await user.click(disableBtn)

    expect(
      await screen.findByRole('heading', { name: /disable category/i }),
    ).toBeInTheDocument()

    const confirmBtn = screen.getByRole('button', {
      name: /^disable category$/i,
    })
    await user.click(confirmBtn)

    expect(disableSpy).toHaveBeenCalledWith(VALID_UUID_1)
  })

  it('renders read-only mode for OPERATOR', async () => {
    vi.spyOn(categoryService, 'listCategories').mockResolvedValueOnce({
      content: [
        {
          externalId: VALID_UUID_1,
          name: 'Fertilizantes',
          description: null,
          active: true,
          activeProductCount: 0,
          createdAt: '2026-08-28T00:00:00Z',
          updatedAt: '2026-08-28T00:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 10,
    })

    renderWithProviders(<CategoryTable currentActorRole="OPERATOR" />)

    expect(await screen.findByText('Fertilizantes')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: /new category/i }),
    ).not.toBeInTheDocument()
    expect(screen.getByText(/read-only/i)).toBeInTheDocument()
  })
})
