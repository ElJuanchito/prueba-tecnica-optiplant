export interface SessionData {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
  role: 'ADMIN' | 'BRANCH_MANAGER' | 'OPERATOR'
  branchId: string | null
  branchName?: string | null
  branchCode?: string | null
  username?: string
}

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly details?: unknown

  constructor(
    status: number,
    code: string,
    message: string,
    details?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
  }
}

const STORAGE_KEY = 'optiplant_session'

let currentSession: SessionData | null = null

export function getStoredSession(): SessionData | null {
  if (currentSession) {
    return currentSession
  }
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      currentSession = JSON.parse(raw) as SessionData
      return currentSession
    }
  } catch {
    // Ignore storage parse errors
  }
  return null
}

export function saveSession(session: SessionData): void {
  currentSession = session
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } catch {
    // Ignore storage write errors
  }
}

export function clearStoredSession(): void {
  currentSession = null
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Ignore storage remove errors
  }
}

let refreshPromise: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const session = getStoredSession()
  if (!session?.refreshToken) {
    clearStoredSession()
    return null
  }

  if (refreshPromise) {
    return refreshPromise
  }

  refreshPromise = (async () => {
    try {
      const response = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: session.refreshToken }),
      })

      if (!response.ok) {
        clearStoredSession()
        return null
      }

      const data = (await response.json()) as {
        accessToken: string
        refreshToken: string
        expiresInSeconds: number
      }

      const updatedSession: SessionData = {
        ...session,
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        expiresInSeconds: data.expiresInSeconds,
      }
      saveSession(updatedSession)
      return data.accessToken
    } catch {
      clearStoredSession()
      return null
    } finally {
      refreshPromise = null
    }
  })()

  return refreshPromise
}

export async function apiClient<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const session = getStoredSession()
  const headers = new Headers(options.headers)

  if (!headers.has('Content-Type') && options.body) {
    headers.set('Content-Type', 'application/json')
  }

  if (session?.accessToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${session.accessToken}`)
  }

  let response = await fetch(path, {
    ...options,
    headers,
  })

  if (
    response.status === 401 &&
    !path.startsWith('/api/auth/login') &&
    !path.startsWith('/api/auth/refresh')
  ) {
    const newAccessToken = await refreshAccessToken()
    if (newAccessToken) {
      headers.set('Authorization', `Bearer ${newAccessToken}`)
      response = await fetch(path, {
        ...options,
        headers,
      })
    }
  }

  if (!response.ok) {
    let errorCode = 'api_error'
    let errorMessage = `Request failed with status ${response.status}`
    let details: unknown

    try {
      const errorBody = (await response.json()) as {
        code?: string
        message?: string
        error?: string
      }
      if (errorBody.code) errorCode = errorBody.code
      if (errorBody.message) errorMessage = errorBody.message
      else if (errorBody.error) errorMessage = errorBody.error
      details = errorBody
    } catch {
      // Body was not JSON
    }

    throw new ApiError(response.status, errorCode, errorMessage, details)
  }

  if (response.status === 204) {
    return undefined as unknown as T
  }

  return (await response.json()) as T
}
