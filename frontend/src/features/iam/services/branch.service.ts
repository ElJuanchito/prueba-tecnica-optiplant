import { apiClient } from '@/lib/api-client.ts'
import {
  branchPageResponseSchema,
  branchQuerySchema,
  branchResponseSchema,
  createBranchSchema,
  editBranchSchema,
} from '../schemas/branch.schema.ts'
import type {
  BranchPageResponse,
  BranchQueryParams,
  BranchResponse,
  CreateBranchInput,
  EditBranchInput,
} from '../types/branch.types.ts'

export const branchService = {
  async listBranches(params?: BranchQueryParams): Promise<BranchPageResponse> {
    const validatedParams = params ? branchQuerySchema.parse(params) : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.active !== undefined) {
        searchParams.set('active', String(validatedParams.active))
      }
      if (validatedParams.page !== undefined) {
        searchParams.set('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.set('size', String(validatedParams.size))
      }
    }

    const query = searchParams.toString()
    const path = `/api/admin/branches${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return branchPageResponseSchema.parse(raw)
  },

  async createBranch(input: CreateBranchInput): Promise<BranchResponse> {
    const validatedInput = createBranchSchema.parse(input)
    const raw = await apiClient<unknown>('/api/admin/branches', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })
    return branchResponseSchema.parse(raw)
  },

  async editBranch(
    externalId: string,
    input: EditBranchInput,
  ): Promise<BranchResponse> {
    const validatedInput = editBranchSchema.parse(input)
    const raw = await apiClient<unknown>(`/api/admin/branches/${externalId}`, {
      method: 'PUT',
      body: JSON.stringify(validatedInput),
    })
    return branchResponseSchema.parse(raw)
  },

  async disableBranch(externalId: string): Promise<void> {
    await apiClient<void>(`/api/admin/branches/${externalId}/disable`, {
      method: 'PATCH',
    })
  },
}
