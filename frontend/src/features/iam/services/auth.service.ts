import {
  apiClient,
  clearStoredSession,
  getStoredSession,
  saveSession,
  type SessionData,
} from '@/lib/api-client.ts'
import {
  loginRequestSchema,
  loginResponseSchema,
  logoutRequestSchema,
  refreshRequestSchema,
  refreshResponseSchema,
} from '../schemas/auth.schema.ts'
import type {
  LoginRequest,
  LoginResponse,
  LogoutRequest,
  RefreshRequest,
  RefreshResponse,
} from '../types/auth.types.ts'

export const authService = {
  async login(input: LoginRequest): Promise<LoginResponse> {
    const validatedInput = loginRequestSchema.parse(input)
    const raw = await apiClient<unknown>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })

    const parsed = loginResponseSchema.parse(raw)
    const session: SessionData = {
      accessToken: parsed.accessToken,
      refreshToken: parsed.refreshToken,
      expiresInSeconds: parsed.expiresInSeconds,
      role: parsed.role,
      branchId: parsed.branchId,
      branchName: parsed.branchName ?? null,
      branchCode: parsed.branchCode ?? null,
      username: validatedInput.username,
    }
    saveSession(session)
    return parsed
  },

  async refresh(input: RefreshRequest): Promise<RefreshResponse> {
    const validatedInput = refreshRequestSchema.parse(input)
    const raw = await apiClient<unknown>('/api/auth/refresh', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })

    const parsed = refreshResponseSchema.parse(raw)
    const currentSession = getStoredSession()
    if (currentSession) {
      saveSession({
        ...currentSession,
        accessToken: parsed.accessToken,
        refreshToken: parsed.refreshToken,
        expiresInSeconds: parsed.expiresInSeconds,
      })
    }
    return parsed
  },

  async logout(input: LogoutRequest): Promise<void> {
    const validatedInput = logoutRequestSchema.parse(input)
    try {
      await apiClient<void>('/api/auth/logout', {
        method: 'POST',
        body: JSON.stringify(validatedInput),
      })
    } finally {
      clearStoredSession()
    }
  },

  getSession(): SessionData | null {
    return getStoredSession()
  },
}
