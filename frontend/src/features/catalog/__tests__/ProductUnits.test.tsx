import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/test-utils.tsx'
import { ProductUnitsDialog } from '../components/ProductUnitsDialog.tsx'
import { productUnitService } from '../services/product-unit.service.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'

describe('Product Units Dialog Component Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  const mockProduct = {
    externalId: VALID_UUID_1,
    sku: 'FERT-NPK-151515',
    name: 'Fertilizante Triple 15',
    baseUnit: 'KG',
  }

  it('renders alternative units with conversion formulas and default sale indicator', async () => {
    vi.spyOn(productUnitService, 'listUnits').mockResolvedValueOnce([
      {
        externalId: VALID_UUID_2,
        unitName: 'SACO_50KG',
        conversionFactor: 50,
        defaultSaleUnit: true,
        createdAt: '2026-08-28T00:00:00Z',
      },
    ])

    renderWithProviders(
      <ProductUnitsDialog
        open={true}
        onOpenChange={() => {}}
        product={mockProduct}
        currentActorRole="ADMIN"
      />,
    )

    expect(await screen.findByText('SACO_50KG')).toBeInTheDocument()
    expect(screen.getByText('Default Sale')).toBeInTheDocument()
    expect(screen.getByText(/1 SACO_50KG =/)).toBeInTheDocument()
    expect(screen.getByText('50')).toBeInTheDocument()
  })

  it('allows ADMIN to add an alternative unit with conversion factor', async () => {
    const user = userEvent.setup()
    vi.spyOn(productUnitService, 'listUnits').mockResolvedValue([])

    const addSpy = vi
      .spyOn(productUnitService, 'addUnit')
      .mockResolvedValueOnce({
        externalId: VALID_UUID_2,
        unitName: 'BULTO_25KG',
        conversionFactor: 25,
        defaultSaleUnit: false,
        createdAt: '2026-08-28T00:00:00Z',
      })

    renderWithProviders(
      <ProductUnitsDialog
        open={true}
        onOpenChange={() => {}}
        product={mockProduct}
        currentActorRole="ADMIN"
      />,
    )

    const addBtn = await screen.findByRole('button', { name: /add unit/i })
    await user.click(addBtn)

    const unitNameInput = screen.getByLabelText(/unit name/i)
    await user.type(unitNameInput, 'BULTO_25KG')

    const factorInput = screen.getByLabelText(/factor in kg/i)
    await user.clear(factorInput)
    await user.type(factorInput, '25')

    const saveBtn = screen.getByRole('button', { name: /save unit/i })
    await user.click(saveBtn)

    await waitFor(() => {
      expect(addSpy).toHaveBeenCalledWith(VALID_UUID_1, {
        unitName: 'BULTO_25KG',
        conversionFactor: 25,
        defaultSaleUnit: false,
      })
    })
  })
})
