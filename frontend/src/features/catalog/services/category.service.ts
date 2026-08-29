import { apiClient } from '@/lib/api-client.ts'
import {
  categoryPageResponseSchema,
  categoryQuerySchema,
  categoryResponseSchema,
  createCategorySchema,
  editCategorySchema,
} from '../schemas/category.schema.ts'
import type {
  CategoryPageResponse,
  CategoryQueryParams,
  CategoryResponse,
  CreateCategoryInput,
  EditCategoryInput,
} from '../types/category.types.ts'

export const categoryService = {
  async listCategories(
    params?: CategoryQueryParams,
  ): Promise<CategoryPageResponse> {
    const validatedParams = params
      ? categoryQuerySchema.parse(params)
      : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.name !== undefined && validatedParams.name !== '') {
        searchParams.set('name', validatedParams.name)
      }
      if (validatedParams.active !== undefined) {
        searchParams.set('active', validatedParams.active)
      }
      if (validatedParams.page !== undefined) {
        searchParams.set('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.set('size', String(validatedParams.size))
      }
    }

    const query = searchParams.toString()
    const path = `/api/catalog/categories${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return categoryPageResponseSchema.parse(raw)
  },

  async getCategory(externalId: string): Promise<CategoryResponse> {
    const raw = await apiClient<unknown>(
      `/api/catalog/categories/${externalId}`,
      {
        method: 'GET',
      },
    )
    return categoryResponseSchema.parse(raw)
  },

  async createCategory(input: CreateCategoryInput): Promise<CategoryResponse> {
    const validatedInput = createCategorySchema.parse(input)
    const raw = await apiClient<unknown>('/api/catalog/categories', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })
    return categoryResponseSchema.parse(raw)
  },

  async editCategory(
    externalId: string,
    input: EditCategoryInput,
  ): Promise<CategoryResponse> {
    const validatedInput = editCategorySchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/catalog/categories/${externalId}`,
      {
        method: 'PUT',
        body: JSON.stringify(validatedInput),
      },
    )
    return categoryResponseSchema.parse(raw)
  },

  async disableCategory(externalId: string): Promise<CategoryResponse> {
    const raw = await apiClient<unknown>(
      `/api/catalog/categories/${externalId}/disable`,
      {
        method: 'PATCH',
      },
    )
    return categoryResponseSchema.parse(raw)
  },

  async enableCategory(externalId: string): Promise<CategoryResponse> {
    const raw = await apiClient<unknown>(
      `/api/catalog/categories/${externalId}/enable`,
      {
        method: 'PATCH',
      },
    )
    return categoryResponseSchema.parse(raw)
  },
}
