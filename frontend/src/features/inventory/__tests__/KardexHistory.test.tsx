import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { userService } from '@/features/iam/services/user.service.ts'
import { productService } from '@/features/catalog/services/product.service.ts'
import { renderWithProviders } from '@/test/test-utils.tsx'
import { KardexTable } from '../components/KardexTable.tsx'
import { inventoryService } from '../services/inventory.service.ts'
import type { KardexLineResponse } from '../types/index.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'

const mockKardexMovements: KardexLineResponse[] = [
  {
    externalId: VALID_UUID_1,
    productExternalId: VALID_UUID_2,
    movementType: 'PURCHASE_RECEIPT',
    quantity: 100,
    unitCost: 12.0,
    totalCost: 1200.0,
    previousStock: 0,
    resultingStock: 100,
    referenceType: 'PO',
    referenceId: 'PO-2026-001',
    notes: 'Initial receipt from AgroSupplier',
    userExternalId: VALID_UUID_1,
    createdAt: '2026-08-28T10:00:00Z',
  },
  {
    externalId: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33',
    productExternalId: VALID_UUID_2,
    movementType: 'SALE',
    quantity: 15,
    unitCost: 12.0,
    totalCost: 180.0,
    previousStock: 100,
    resultingStock: 85,
    referenceType: 'INVOICE',
    referenceId: 'INV-889',
    notes: 'Direct farm sale',
    userExternalId: VALID_UUID_1,
    createdAt: '2026-08-28T15:30:00Z',
  },
  {
    externalId: 'd0eebc99-9c0b-4ef8-bb6d-6bb9bd380a44',
    productExternalId: VALID_UUID_2,
    movementType: 'ADJUSTMENT_NEG',
    quantity: 5,
    unitCost: 12.0,
    totalCost: 60.0,
    previousStock: 85,
    resultingStock: 80,
    referenceType: 'AUDIT',
    referenceId: null,
    notes: 'Physical audit variance',
    userExternalId: VALID_UUID_1,
    createdAt: '2026-08-29T08:00:00Z',
  },
]

describe('Kardex History Component Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(userService, 'listUsers').mockResolvedValue({
      content: [
        {
          externalId: VALID_UUID_1,
          username: 'john_operator',
          fullName: 'John Operator',
          email: 'john@example.com',
          role: 'OPERATOR',
          branchId: VALID_UUID_2,
          branchName: 'Sucursal Central',
          active: true,
        },
      ],
      totalElements: 1,
      page: 0,
      size: 10,
    })
    vi.spyOn(productService, 'listProducts').mockResolvedValue({
      content: [
        {
          externalId: VALID_UUID_2,
          sku: 'SKU-FERT-01',
          name: 'Fertilizante NPK 10-20-20',
          baseUnit: 'KILOGRAMO',
          active: true,
          createdAt: '2026-08-28T10:00:00Z',
          updatedAt: '2026-08-28T10:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 10,
    })
  })

  it('renders Kardex ledger with movement type badges, quantities, and balance progressions', async () => {
    vi.spyOn(inventoryService, 'listKardex').mockResolvedValueOnce({
      content: mockKardexMovements,
      totalElements: 3,
      page: 0,
      size: 20,
    })

    renderWithProviders(<KardexTable />)

    expect(
      await screen.findByText('Immutable Kardex Audit Ledger'),
    ).toBeInTheDocument()

    // Badges & Movement Types
    expect(await screen.findByText('PURCHASE_RECEIPT')).toBeInTheDocument()
    expect(screen.getByText('SALE')).toBeInTheDocument()
    expect(screen.getByText('ADJUSTMENT_NEG')).toBeInTheDocument()

    // Quantities with signs
    expect(screen.getByText('+100')).toBeInTheDocument()
    expect(screen.getByText('-15')).toBeInTheDocument()
    expect(screen.getByText('-5')).toBeInTheDocument()

    // Balance progression values
    expect(screen.getAllByText('100').length).toBeGreaterThan(0)
    expect(screen.getAllByText('85').length).toBeGreaterThan(0)
    expect(screen.getByText('80')).toBeInTheDocument()

    // Responsible user names & products resolved from lookup
    expect(await screen.findAllByText('john_operator')).toHaveLength(3)
    expect(
      screen.getAllByText('Fertilizante NPK 10-20-20').length,
    ).toBeGreaterThan(0)
    expect(screen.queryByText('a0eebc99...')).toBeNull()

    // Notes / References
    expect(
      screen.getByText('Initial receipt from AgroSupplier'),
    ).toBeInTheDocument()
    expect(screen.getByText(/Ref: PO \(PO-2026-001\)/i)).toBeInTheDocument()
  })

  it('displays empty state when no kardex records are found', async () => {
    vi.spyOn(inventoryService, 'listKardex').mockResolvedValueOnce({
      content: [],
      totalElements: 0,
      page: 0,
      size: 20,
    })

    renderWithProviders(<KardexTable />)

    expect(
      await screen.findByText('No Kardex records found'),
    ).toBeInTheDocument()
  })
})
