import { apiClient } from '@/lib/api-client.ts'
import {
  productUnitItemResponseSchema,
  productUnitListResponseSchema,
  unitRequestSchema,
} from '../schemas/product-unit.schema.ts'
import type {
  ProductUnitItemResponse,
  ProductUnitListResponse,
  UnitRequestInput,
} from '../types/product-unit.types.ts'

export const productUnitService = {
  async listUnits(productExternalId: string): Promise<ProductUnitListResponse> {
    const raw = await apiClient<unknown>(
      `/api/catalog/products/${productExternalId}/units`,
      {
        method: 'GET',
      },
    )
    return productUnitListResponseSchema.parse(raw)
  },

  async addUnit(
    productExternalId: string,
    input: UnitRequestInput,
  ): Promise<ProductUnitItemResponse> {
    const validatedInput = unitRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/catalog/products/${productExternalId}/units`,
      {
        method: 'POST',
        body: JSON.stringify(validatedInput),
      },
    )
    return productUnitItemResponseSchema.parse(raw)
  },

  async replaceUnit(
    productExternalId: string,
    unitExternalId: string,
    input: UnitRequestInput,
  ): Promise<ProductUnitItemResponse> {
    const validatedInput = unitRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/catalog/products/${productExternalId}/units/${unitExternalId}`,
      {
        method: 'PUT',
        body: JSON.stringify(validatedInput),
      },
    )
    return productUnitItemResponseSchema.parse(raw)
  },

  async deleteUnit(
    productExternalId: string,
    unitExternalId: string,
  ): Promise<void> {
    await apiClient<void>(
      `/api/catalog/products/${productExternalId}/units/${unitExternalId}`,
      {
        method: 'DELETE',
      },
    )
  },
}
