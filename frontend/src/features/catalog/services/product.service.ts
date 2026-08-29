import { apiClient } from '@/lib/api-client.ts'
import {
  createProductSchema,
  editProductSchema,
  productDetailResponseSchema,
  productPageResponseSchema,
  productQuerySchema,
} from '../schemas/product.schema.ts'
import type {
  CreateProductInput,
  EditProductInput,
  ProductDetailResponse,
  ProductPageResponse,
  ProductQueryParams,
} from '../types/product.types.ts'

export const productService = {
  async listProducts(
    params?: ProductQueryParams,
  ): Promise<ProductPageResponse> {
    const validatedParams = params
      ? productQuerySchema.parse(params)
      : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.q !== undefined && validatedParams.q !== '') {
        searchParams.set('q', validatedParams.q)
      }
      if (validatedParams.categoryId !== undefined) {
        searchParams.set('categoryId', validatedParams.categoryId)
      }
      if (validatedParams.active !== undefined) {
        searchParams.set('active', validatedParams.active)
      }
      if (validatedParams.sort !== undefined) {
        searchParams.set('sort', validatedParams.sort)
      }
      if (validatedParams.direction !== undefined) {
        searchParams.set('direction', validatedParams.direction)
      }
      if (validatedParams.page !== undefined) {
        searchParams.set('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.set('size', String(validatedParams.size))
      }
    }

    const query = searchParams.toString()
    const path = `/api/catalog/products${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return productPageResponseSchema.parse(raw)
  },

  async getProduct(externalId: string): Promise<ProductDetailResponse> {
    const raw = await apiClient<unknown>(
      `/api/catalog/products/${externalId}`,
      {
        method: 'GET',
      },
    )
    return productDetailResponseSchema.parse(raw)
  },

  async createProduct(
    input: CreateProductInput,
  ): Promise<ProductDetailResponse> {
    const validatedInput = createProductSchema.parse(input)
    const raw = await apiClient<unknown>('/api/catalog/products', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })
    return productDetailResponseSchema.parse(raw)
  },

  async editProduct(
    externalId: string,
    input: EditProductInput,
  ): Promise<ProductDetailResponse> {
    const validatedInput = editProductSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/catalog/products/${externalId}`,
      {
        method: 'PUT',
        body: JSON.stringify(validatedInput),
      },
    )
    return productDetailResponseSchema.parse(raw)
  },

  async disableProduct(externalId: string): Promise<ProductDetailResponse> {
    const raw = await apiClient<unknown>(
      `/api/catalog/products/${externalId}/disable`,
      {
        method: 'PATCH',
      },
    )
    return productDetailResponseSchema.parse(raw)
  },

  async enableProduct(externalId: string): Promise<ProductDetailResponse> {
    const raw = await apiClient<unknown>(
      `/api/catalog/products/${externalId}/enable`,
      {
        method: 'PATCH',
      },
    )
    return productDetailResponseSchema.parse(raw)
  },
}
