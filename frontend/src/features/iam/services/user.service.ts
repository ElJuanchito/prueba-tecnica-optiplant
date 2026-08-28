import { apiClient } from '@/lib/api-client.ts'
import {
  createUserSchema,
  editUserSchema,
  userPageResponseSchema,
  userQuerySchema,
  userResponseSchema,
} from '../schemas/user.schema.ts'
import type {
  CreateUserInput,
  EditUserInput,
  UserPageResponse,
  UserQueryParams,
  UserResponse,
} from '../types/user.types.ts'

export const userService = {
  async listUsers(params?: UserQueryParams): Promise<UserPageResponse> {
    const validatedParams = params ? userQuerySchema.parse(params) : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.active !== undefined) {
        searchParams.set('active', String(validatedParams.active))
      }
      if (validatedParams.role !== undefined) {
        searchParams.set('role', validatedParams.role)
      }
      if (validatedParams.branchId !== undefined) {
        searchParams.set('branchId', validatedParams.branchId)
      }
      if (validatedParams.page !== undefined) {
        searchParams.set('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.set('size', String(validatedParams.size))
      }
    }

    const query = searchParams.toString()
    const path = `/api/admin/users${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return userPageResponseSchema.parse(raw)
  },

  async createUser(input: CreateUserInput): Promise<UserResponse> {
    const validatedInput = createUserSchema.parse(input)
    const raw = await apiClient<unknown>('/api/admin/users', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })
    return userResponseSchema.parse(raw)
  },

  async editUser(
    externalId: string,
    input: EditUserInput,
  ): Promise<UserResponse> {
    const validatedInput = editUserSchema.parse(input)
    const raw = await apiClient<unknown>(`/api/admin/users/${externalId}`, {
      method: 'PUT',
      body: JSON.stringify(validatedInput),
    })
    return userResponseSchema.parse(raw)
  },

  async disableUser(externalId: string): Promise<void> {
    await apiClient<void>(`/api/admin/users/${externalId}/disable`, {
      method: 'PATCH',
    })
  },
}
