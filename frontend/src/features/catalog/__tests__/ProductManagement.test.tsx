import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/test-utils.tsx'
import { ProductTable } from '../components/ProductTable.tsx'
import { ProductFormDialog } from '../components/ProductFormDialog.tsx'
import { productService } from '../services/product.service.ts'
import { categoryService } from '../services/category.service.ts'
import { ApiError } from '@/lib/api-client.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'

describe('Product Management Component Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(categoryService, 'listCategories').mockResolvedValue({
      content: [
        {
          externalId: VALID_UUID_2,
          name: 'Fertilizantes',
          description: null,
          active: true,
          activeProductCount: 1,
          createdAt: '2026-08-28T00:00:00Z',
          updatedAt: '2026-08-28T00:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 100,
    })
  })

  it('renders product listing with SKU, name, category, and base unit', async () => {
    vi.spyOn(productService, 'listProducts').mockResolvedValueOnce({
      content: [
        {
          externalId: VALID_UUID_1,
          sku: 'FERT-NPK-151515',
          name: 'Fertilizante Triple 15',
          baseUnit: 'KG',
          active: true,
          category: {
            externalId: VALID_UUID_2,
            name: 'Fertilizantes',
            active: true,
          },
          createdAt: '2026-08-28T00:00:00Z',
          updatedAt: '2026-08-28T00:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 10,
    })

    renderWithProviders(<ProductTable currentActorRole="ADMIN" />)

    expect(await screen.findByText('FERT-NPK-151515')).toBeInTheDocument()
    expect(screen.getByText('Fertilizante Triple 15')).toBeInTheDocument()
    expect(screen.getAllByText('Fertilizantes').length).toBeGreaterThan(0)
    expect(screen.getByText('KG')).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()
  })

  it('allows ADMIN to register a new product via ProductFormDialog', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()

    const createSpy = vi
      .spyOn(productService, 'createProduct')
      .mockResolvedValueOnce({
        externalId: VALID_UUID_1,
        sku: 'FERT-UREA-46',
        name: 'Urea Granulada 46%',
        description: null,
        baseUnit: 'KG',
        active: true,
        category: {
          externalId: VALID_UUID_2,
          name: 'Fertilizantes',
          active: true,
        },
        units: [],
        createdAt: '2026-08-28T00:00:00Z',
        updatedAt: '2026-08-28T00:00:00Z',
      })

    renderWithProviders(
      <ProductFormDialog open={true} onOpenChange={onOpenChange} />,
    )

    expect(
      screen.getByRole('heading', { name: /register new product/i }),
    ).toBeInTheDocument()

    const skuInput = screen.getByLabelText(/sku/i)
    await user.type(skuInput, 'FERT-UREA-46')

    const nameInput = screen.getByLabelText(/product name/i)
    await user.type(nameInput, 'Urea Granulada 46%')

    const baseUnitInput = screen.getByLabelText(/base unit of measure/i)
    await user.clear(baseUnitInput)
    await user.type(baseUnitInput, 'KG')

    const categorySelect = screen.getByRole('combobox', { name: /category/i })
    await user.click(categorySelect)

    const categoryOption = await screen.findByRole('option', {
      name: 'Fertilizantes',
    })
    await user.click(categoryOption)

    const submitBtn = screen.getByRole('button', { name: /register product/i })
    await user.click(submitBtn)

    await waitFor(() => {
      expect(createSpy).toHaveBeenCalledWith({
        sku: 'FERT-UREA-46',
        name: 'Urea Granulada 46%',
        description: null,
        categoryExternalId: VALID_UUID_2,
        baseUnit: 'KG',
      })
      expect(onOpenChange).toHaveBeenCalledWith(false)
    })
  })

  it('displays error if duplicate SKU is returned by backend', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()

    vi.spyOn(productService, 'createProduct').mockRejectedValueOnce(
      new ApiError(409, 'duplicate_sku', 'SKU already registered'),
    )

    renderWithProviders(
      <ProductFormDialog open={true} onOpenChange={onOpenChange} />,
    )

    await user.type(screen.getByLabelText(/sku/i), 'FERT-NPK-151515')
    await user.type(screen.getByLabelText(/product name/i), 'Fertilizante')

    const categorySelect = screen.getByRole('combobox', { name: /category/i })
    await user.click(categorySelect)
    const categoryOption = await screen.findByRole('option', {
      name: 'Fertilizantes',
    })
    await user.click(categoryOption)

    const submitBtn = screen.getByRole('button', { name: /register product/i })
    await user.click(submitBtn)

    expect(await screen.findByText(/already in use/i)).toBeInTheDocument()
  })

  it('displays read-only base unit in edit dialog (PA-08 invariant)', async () => {
    const onOpenChange = vi.fn()
    const productToEdit = {
      externalId: VALID_UUID_1,
      sku: 'FERT-NPK-151515',
      name: 'Fertilizante Triple 15',
      description: null,
      baseUnit: 'KG',
      active: true,
      category: {
        externalId: VALID_UUID_2,
        name: 'Fertilizantes',
        active: true,
      },
      createdAt: '2026-08-28T00:00:00Z',
      updatedAt: '2026-08-28T00:00:00Z',
    }

    renderWithProviders(
      <ProductFormDialog
        open={true}
        onOpenChange={onOpenChange}
        productToEdit={productToEdit}
      />,
    )

    expect(
      screen.getByRole('heading', { name: /edit product/i }),
    ).toBeInTheDocument()

    expect(
      screen.getByText(/base unit is fixed at creation/i),
    ).toBeInTheDocument()
    expect(screen.getByText('KG')).toBeInTheDocument()
  })
})
