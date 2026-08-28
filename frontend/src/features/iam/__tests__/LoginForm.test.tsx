import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/test-utils.tsx'
import { LoginForm } from '../components/LoginForm.tsx'
import { authService } from '../services/auth.service.ts'

describe('LoginForm Component', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('renders login form elements with accessible labels', () => {
    renderWithProviders(<LoginForm />)

    expect(
      screen.getByRole('heading', { name: /optiplant/i }),
    ).toBeInTheDocument()
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
  })

  it('shows validation errors when submitted with empty fields', async () => {
    const user = userEvent.setup()
    renderWithProviders(<LoginForm />)

    const submitBtn = screen.getByRole('button', { name: /sign in/i })
    await user.click(submitBtn)

    expect(await screen.findByText(/username is required/i)).toBeInTheDocument()
    expect(await screen.findByText(/password is required/i)).toBeInTheDocument()
  })

  it('submits valid credentials and triggers onSuccess callback', async () => {
    const user = userEvent.setup()
    const onSuccess = vi.fn()

    vi.spyOn(authService, 'login').mockResolvedValueOnce({
      accessToken: 'token-123',
      refreshToken: 'ref-123',
      expiresInSeconds: 900,
      role: 'ADMIN',
      branchId: null,
    })

    renderWithProviders(<LoginForm onSuccess={onSuccess} />)

    await user.type(screen.getByLabelText(/username/i), 'admin')
    await user.type(screen.getByLabelText(/password/i), 'adminPass123')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalledTimes(1)
    })
  })

  it('displays error alert when login fails with invalid credentials', async () => {
    const user = userEvent.setup()

    vi.spyOn(authService, 'login').mockRejectedValueOnce(
      new Error('Invalid username or password'),
    )

    renderWithProviders(<LoginForm />)

    await user.type(screen.getByLabelText(/username/i), 'wronguser')
    await user.type(screen.getByLabelText(/password/i), 'wrongpassword')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(
      await screen.findByText(/invalid username or password/i),
    ).toBeInTheDocument()
  })
})
