import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/test-utils.tsx'
import { BranchTable } from '../components/BranchTable.tsx'
import { BranchFormDialog } from '../components/BranchFormDialog.tsx'
import { branchService } from '../services/branch.service.ts'
import type { BranchResponse } from '../types/branch.types.ts'

describe('Branch Management Components', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('renders BranchTable with branch locations', async () => {
    const mockBranches: BranchResponse[] = [
      {
        externalId: 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        code: 'BOG-01',
        name: 'Sede Bogotá Norte',
        address: 'Cra 15 # 100-20',
        city: 'Bogotá',
        phone: '+57 601 1234567',
        active: true,
      },
    ]

    vi.spyOn(branchService, 'listBranches').mockResolvedValueOnce({
      content: mockBranches,
      totalElements: 1,
      page: 0,
      size: 10,
    })

    renderWithProviders(<BranchTable />)

    expect(await screen.findByText('BOG-01')).toBeInTheDocument()
    expect(screen.getByText('Sede Bogotá Norte')).toBeInTheDocument()
    expect(screen.getByText('Cra 15 # 100-20')).toBeInTheDocument()
    expect(screen.getByText('Bogotá')).toBeInTheDocument()
  })

  it('creates branch via BranchFormDialog', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()

    const createSpy = vi
      .spyOn(branchService, 'createBranch')
      .mockResolvedValueOnce({
        externalId: 'b-new-123',
        code: 'MED-01',
        name: 'Sede Medellín',
        address: 'Calle 10 # 40-50',
        city: 'Medellín',
        phone: null,
        active: true,
      })

    renderWithProviders(
      <BranchFormDialog open={true} onOpenChange={onOpenChange} />,
    )

    await user.type(screen.getByLabelText(/branch code/i), 'MED-01')
    await user.type(screen.getByLabelText(/branch name/i), 'Sede Medellín')
    await user.type(screen.getByLabelText(/address/i), 'Calle 10 # 40-50')
    await user.type(screen.getByLabelText(/city/i), 'Medellín')

    await user.click(screen.getByRole('button', { name: /create branch/i }))

    await waitFor(() => {
      expect(createSpy).toHaveBeenCalledWith({
        code: 'MED-01',
        name: 'Sede Medellín',
        address: 'Calle 10 # 40-50',
        city: 'Medellín',
        phone: null,
      })
      expect(onOpenChange).toHaveBeenCalledWith(false)
    })
  })
})
