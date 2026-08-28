import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/test-utils.tsx'
import { UserTable } from '../components/UserTable.tsx'
import { UserFormDialog } from '../components/UserFormDialog.tsx'
import { userService } from '../services/user.service.ts'
import { branchService } from '../services/branch.service.ts'
import type { UserResponse } from '../types/user.types.ts'

const VALID_BRANCH_UUID = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_USER_UUID = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'

describe('User Management Components', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(branchService, 'listBranches').mockResolvedValue({
      content: [
        {
          externalId: VALID_BRANCH_UUID,
          code: 'BOG-01',
          name: 'Sede Bogotá',
          address: 'Calle 100',
          city: 'Bogotá',
          phone: null,
          active: true,
        },
      ],
      totalElements: 1,
      page: 0,
      size: 20,
    })
  })

  it('renders UserTable with users list and badges', async () => {
    const mockUsers: UserResponse[] = [
      {
        externalId: VALID_USER_UUID,
        username: 'admin',
        email: 'admin@optiplant.com',
        fullName: 'Admin General',
        role: 'ADMIN',
        branchId: null,
        active: true,
      },
      {
        externalId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
        username: 'operator1',
        email: 'op1@optiplant.com',
        fullName: 'Operator One',
        role: 'OPERATOR',
        branchId: VALID_BRANCH_UUID,
        branchName: 'Sede Bogotá',
        branchCode: 'BOG-01',
        active: false,
      },
    ]

    vi.spyOn(userService, 'listUsers').mockResolvedValueOnce({
      content: mockUsers,
      totalElements: 2,
      page: 0,
      size: 10,
    })

    renderWithProviders(<UserTable currentActorRole="ADMIN" />)

    expect(await screen.findByText('admin')).toBeInTheDocument()
    expect(screen.getByText('Admin General')).toBeInTheDocument()
    expect(screen.getByText('operator1')).toBeInTheDocument()
    expect(screen.getByText('Sede Bogotá (BOG-01)')).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()
    expect(screen.getByText('Disabled')).toBeInTheDocument()
  })

  it('disables user when disable button is clicked and confirmed', async () => {
    const user = userEvent.setup()
    const mockUser: UserResponse = {
      externalId: VALID_USER_UUID,
      username: 'op_to_disable',
      email: 'op@optiplant.com',
      fullName: 'Operator To Disable',
      role: 'OPERATOR',
      branchId: VALID_BRANCH_UUID,
      active: true,
    }

    vi.spyOn(userService, 'listUsers').mockResolvedValue({
      content: [mockUser],
      totalElements: 1,
      page: 0,
      size: 10,
    })

    const disableSpy = vi
      .spyOn(userService, 'disableUser')
      .mockResolvedValueOnce(undefined as unknown as void)

    vi.spyOn(window, 'confirm').mockReturnValue(true)

    renderWithProviders(<UserTable currentActorRole="ADMIN" />)

    const disableBtn = await screen.findByTitle('Disable user')
    await user.click(disableBtn)

    expect(disableSpy).toHaveBeenCalledWith(mockUser.externalId)
  })

  it('renders UserFormDialog and submits new user data for BRANCH_MANAGER', async () => {
    const user = userEvent.setup()
    const onOpenChange = vi.fn()

    const createSpy = vi
      .spyOn(userService, 'createUser')
      .mockResolvedValueOnce({
        externalId: 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a99',
        username: 'new_operator',
        email: 'new_operator@optiplant.com',
        fullName: 'New Operator Full',
        role: 'OPERATOR',
        branchId: VALID_BRANCH_UUID,
        active: true,
      })

    renderWithProviders(
      <UserFormDialog
        open={true}
        onOpenChange={onOpenChange}
        currentActorRole="BRANCH_MANAGER"
        currentActorBranchId={VALID_BRANCH_UUID}
      />,
    )

    expect(screen.getByText('Create New User')).toBeInTheDocument()

    await user.type(screen.getByLabelText(/username/i), 'new_operator')
    await user.type(screen.getByLabelText(/full name/i), 'New Operator Full')
    await user.type(
      screen.getByLabelText(/email/i),
      'new_operator@optiplant.com',
    )
    await user.type(screen.getByLabelText(/password/i), 'password123')

    await user.click(screen.getByRole('button', { name: /create user/i }))

    await waitFor(() => {
      expect(createSpy).toHaveBeenCalledWith({
        username: 'new_operator',
        fullName: 'New Operator Full',
        email: 'new_operator@optiplant.com',
        password: 'password123',
        role: 'OPERATOR',
        branchId: VALID_BRANCH_UUID,
      })
      expect(onOpenChange).toHaveBeenCalledWith(false)
    })
  })

  it('renders Access Restricted when OPERATOR attempts to render UserTable', () => {
    renderWithProviders(<UserTable currentActorRole="OPERATOR" />)
    expect(screen.getByText('Access Restricted')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /create user/i })).not.toBeInTheDocument()
  })

  it('does not render UserFormDialog for OPERATOR', () => {
    const onOpenChange = vi.fn()
    const { container } = renderWithProviders(
      <UserFormDialog
        open={true}
        onOpenChange={onOpenChange}
        currentActorRole="OPERATOR"
      />,
    )
    expect(container).toBeEmptyDOMElement()
  })
})
