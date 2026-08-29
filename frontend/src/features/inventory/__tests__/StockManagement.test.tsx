import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/test-utils.tsx'
import { AdjustStockDialog } from '../components/AdjustStockDialog.tsx'
import { NetworkAvailabilityDialog } from '../components/NetworkAvailabilityDialog.tsx'
import { StockTable } from '../components/StockTable.tsx'
import { ThresholdDialog } from '../components/ThresholdDialog.tsx'
import { WriteOffDialog } from '../components/WriteOffDialog.tsx'
import { inventoryService } from '../services/inventory.service.ts'
import type { StockLineResponse } from '../types/index.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'

const mockProduct: StockLineResponse = {
  productExternalId: VALID_UUID_1,
  sku: 'FERT-UREA-46',
  name: 'Urea Granulada 46%',
  currentStock: 100,
  reservedStock: 10,
  inTransitStock: 5,
  availableStock: 90,
  minStockThreshold: 20,
  averageCost: 15.5,
  lastUpdatedAt: '2026-08-29T00:00:00Z',
}

import { productService } from '@/features/catalog/services/product.service.ts'

describe('Stock Management Component Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(productService, 'listProducts').mockResolvedValue({
      content: [
        {
          externalId: VALID_UUID_1,
          sku: 'FERT-UREA-46',
          name: 'Urea Granulada 46%',
          baseUnit: 'KILOGRAMO',
          active: true,
          createdAt: '2026-08-29T00:00:00Z',
          updatedAt: '2026-08-29T00:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 15,
    })
  })

  it('renders StockTable with products, balances, valuation, and status badges for BRANCH_MANAGER', async () => {
    vi.spyOn(inventoryService, 'listStock').mockResolvedValueOnce({
      content: [mockProduct],
      totalElements: 1,
      page: 0,
      size: 15,
    })

    renderWithProviders(<StockTable currentActorRole="BRANCH_MANAGER" />)

    expect(await screen.findByText('Urea Granulada 46%')).toBeInTheDocument()
    expect(screen.getByText('FERT-UREA-46')).toBeInTheDocument()
    expect(screen.getByText('100')).toBeInTheDocument()
    expect(screen.getByText('90')).toBeInTheDocument()
    expect(screen.getByText('In Stock')).toBeInTheDocument()
    expect(screen.getByText('$15.50/u')).toBeInTheDocument()
  })

  it('renders StockTable in Corporate Network Explorer mode for ADMIN', async () => {
    renderWithProviders(<StockTable currentActorRole="ADMIN" />)

    expect(await screen.findByText('Corporate Network Explorer')).toBeInTheDocument()
    expect(await screen.findByText('Urea Granulada 46%')).toBeInTheDocument()
    expect(screen.getByText('FERT-UREA-46')).toBeInTheDocument()
    expect(screen.getByText('Active Master')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Network Availability/i })).toBeInTheDocument()
  })

  it('displays low stock badge when currentStock is below or equal to threshold', async () => {
    vi.spyOn(inventoryService, 'listStock').mockResolvedValueOnce({
      content: [
        {
          ...mockProduct,
          currentStock: 15,
          availableStock: 15,
          minStockThreshold: 20,
        },
      ],
      totalElements: 1,
      page: 0,
      size: 15,
    })

    renderWithProviders(<StockTable currentActorRole="BRANCH_MANAGER" />)

    expect(await screen.findByText('Low Stock')).toBeInTheDocument()
  })

  it('allows BRANCH_MANAGER to perform physical adjustment via AdjustStockDialog', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()

    const adjustSpy = vi.spyOn(inventoryService, 'adjustStock').mockResolvedValueOnce({
      movementExternalId: VALID_UUID_2,
      movementType: 'ADJUSTMENT_NEG',
      quantity: 8,
      previousStock: 100,
      resultingStock: 92,
      createdAt: '2026-08-29T00:00:00Z',
    })

    renderWithProviders(
      <AdjustStockDialog
        product={mockProduct}
        open={true}
        onOpenChange={onOpenChange}
      />,
    )

    expect(screen.getByText('Physical Inventory Adjustment')).toBeInTheDocument()
    expect(screen.getByText('Urea Granulada 46%')).toBeInTheDocument()

    // Change counted physical count to 92
    const countInput = screen.getByLabelText(/Counted Physical Quantity/i)
    await user.clear(countInput)
    await user.type(countInput, '92')

    // Check difference live calculation
    expect(screen.getByText(/Negative Adjustment \(-8\)/i)).toBeInTheDocument()

    // Enter mandatory reason
    const reasonInput = screen.getByLabelText(/Justification Reason/i)
    await user.type(reasonInput, 'Annual audit discrepancy')

    // Submit
    const submitBtn = screen.getByRole('button', { name: /Confirm Adjustment/i })
    await user.click(submitBtn)

    await waitFor(() => {
      expect(adjustSpy).toHaveBeenCalledWith({
        productExternalId: VALID_UUID_1,
        countedQuantity: 92,
        reason: 'Annual audit discrepancy',
      })
    })

    expect(
      await screen.findByText(/Adjustment recorded successfully/i),
    ).toBeInTheDocument()
  })

  it('allows OPERATOR to register write-off via WriteOffDialog', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()

    const writeOffSpy = vi.spyOn(inventoryService, 'writeOffStock').mockResolvedValueOnce({
      movementExternalId: VALID_UUID_2,
      movementType: 'DAMAGE_WASTE',
      quantity: 5,
      previousStock: 100,
      resultingStock: 95,
      createdAt: '2026-08-29T00:00:00Z',
    })

    renderWithProviders(
      <WriteOffDialog
        product={mockProduct}
        open={true}
        onOpenChange={onOpenChange}
      />,
    )

    expect(screen.getByText('Register Stock Write-Off')).toBeInTheDocument()
    expect(screen.getAllByText('$15.50').length).toBeGreaterThan(0)

    const qtyInput = screen.getByLabelText(/Units to Write Off/i)
    await user.clear(qtyInput)
    await user.type(qtyInput, '5')

    expect(screen.getByText('$77.50')).toBeInTheDocument() // 5 units * $15.50

    const reasonInput = screen.getByLabelText(/Reason for Write-Off/i)
    await user.type(reasonInput, 'Damaged during unloading')

    const submitBtn = screen.getByRole('button', { name: /Confirm Write-Off/i })
    await user.click(submitBtn)

    await waitFor(() => {
      expect(writeOffSpy).toHaveBeenCalledWith({
        productExternalId: VALID_UUID_1,
        quantity: 5,
        reason: 'Damaged during unloading',
      })
    })

    expect(
      await screen.findByText(/Write-off registered successfully/i),
    ).toBeInTheDocument()
  })

  it('allows updating safety threshold via ThresholdDialog', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()

    const thresholdSpy = vi.spyOn(inventoryService, 'setThreshold').mockResolvedValueOnce({
      productExternalId: VALID_UUID_1,
      minStockThreshold: 35,
    })

    renderWithProviders(
      <ThresholdDialog
        product={mockProduct}
        open={true}
        onOpenChange={onOpenChange}
      />,
    )

    expect(screen.getByText('Configure Minimum Stock Threshold')).toBeInTheDocument()

    const input = screen.getByLabelText(/New Minimum Stock Threshold/i)
    await user.clear(input)
    await user.type(input, '35')

    const submitBtn = screen.getByRole('button', { name: /Save Threshold/i })
    await user.click(submitBtn)

    await waitFor(() => {
      expect(thresholdSpy).toHaveBeenCalledWith(VALID_UUID_1, {
        minStockThreshold: 35,
      })
    })

    expect(
      await screen.findByText(/Minimum stock threshold updated to 35 units/i),
    ).toBeInTheDocument()
  })

  it('renders network availability modal with branch breakdowns', async () => {
    vi.spyOn(inventoryService, 'getNetworkAvailability').mockResolvedValueOnce({
      productExternalId: VALID_UUID_1,
      sku: 'FERT-UREA-46',
      name: 'Urea Granulada 46%',
      branches: [
        {
          branchExternalId: VALID_UUID_1,
          branchName: 'Sucursal Norte',
          currentStock: 100,
          reservedStock: 10,
          inTransitStock: 5,
          availableStock: 90,
          isOwnBranch: true,
        },
        {
          branchExternalId: VALID_UUID_2,
          branchName: 'Sucursal Sur',
          currentStock: 50,
          reservedStock: 0,
          inTransitStock: 0,
          availableStock: 50,
          isOwnBranch: false,
        },
      ],
      networkTotal: 150,
    })

    renderWithProviders(
      <NetworkAvailabilityDialog
        product={mockProduct}
        open={true}
        onOpenChange={vi.fn()}
      />,
    )

    expect(await screen.findByText('Network Stock Availability')).toBeInTheDocument()
    expect(await screen.findByText('150 units')).toBeInTheDocument()
    expect(screen.getByText('Sucursal Norte')).toBeInTheDocument()
    expect(screen.getByText('Sucursal Sur')).toBeInTheDocument()
    expect(screen.getByText('Own Branch')).toBeInTheDocument()
  })
})
