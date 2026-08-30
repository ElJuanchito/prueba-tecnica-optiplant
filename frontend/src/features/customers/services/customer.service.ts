import { apiClient } from '@/lib/api-client.ts'
import { salePageResponseSchema } from '@/features/sales/schemas/index.ts'
import type { SalePageResponse } from '@/features/sales/types/index.ts'
import {
  createCustomerRequestSchema,
  customerPageResponseSchema,
  customerQuerySchema,
  customerResponseSchema,
  customerSalesHistoryQuerySchema,
  editCustomerRequestSchema,
} from '../schemas/index.ts'
import type {
  CreateCustomerRequest,
  CustomerPageResponse,
  CustomerQueryParams,
  CustomerResponse,
  CustomerSalesHistoryQueryParams,
  EditCustomerRequest,
} from '../types/index.ts'

export const customerService = {
  async create(input: CreateCustomerRequest): Promise<CustomerResponse> {
    const validatedInput = createCustomerRequestSchema.parse(input)
    const raw = await apiClient<unknown>('/api/sales/customers', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })
    return customerResponseSchema.parse(raw)
  },

  async list(params?: CustomerQueryParams): Promise<CustomerPageResponse> {
    const validatedParams = params
      ? customerQuerySchema.parse(params)
      : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (
        validatedParams.search !== undefined &&
        validatedParams.search !== ''
      ) {
        searchParams.set('search', validatedParams.search)
      }
      if (validatedParams.active !== undefined) {
        searchParams.set('active', String(validatedParams.active))
      }
      if (validatedParams.sort !== undefined) {
        searchParams.set('sort', validatedParams.sort)
      }
      if (validatedParams.page !== undefined) {
        searchParams.set('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.set('size', String(validatedParams.size))
      }
    }

    const query = searchParams.toString()
    const path = `/api/sales/customers${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return customerPageResponseSchema.parse(raw)
  },

  async get(externalId: string): Promise<CustomerResponse> {
    const raw = await apiClient<unknown>(`/api/sales/customers/${externalId}`, {
      method: 'GET',
    })
    return customerResponseSchema.parse(raw)
  },

  async edit(
    externalId: string,
    input: EditCustomerRequest,
  ): Promise<CustomerResponse> {
    const validatedInput = editCustomerRequestSchema.parse(input)
    const raw = await apiClient<unknown>(`/api/sales/customers/${externalId}`, {
      method: 'PUT',
      body: JSON.stringify(validatedInput),
    })
    return customerResponseSchema.parse(raw)
  },

  async disable(externalId: string): Promise<CustomerResponse> {
    const raw = await apiClient<unknown>(
      `/api/sales/customers/${externalId}/disable`,
      {
        method: 'PATCH',
      },
    )
    return customerResponseSchema.parse(raw)
  },

  async enable(externalId: string): Promise<CustomerResponse> {
    const raw = await apiClient<unknown>(
      `/api/sales/customers/${externalId}/enable`,
      {
        method: 'PATCH',
      },
    )
    return customerResponseSchema.parse(raw)
  },

  async getSalesHistory(
    externalId: string,
    params?: CustomerSalesHistoryQueryParams,
  ): Promise<SalePageResponse> {
    const validatedParams = params
      ? customerSalesHistoryQuerySchema.parse(params)
      : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.status !== undefined) {
        searchParams.set('status', validatedParams.status)
      }
      if (validatedParams.from !== undefined) {
        searchParams.set('from', validatedParams.from)
      }
      if (validatedParams.to !== undefined) {
        searchParams.set('to', validatedParams.to)
      }
      if (validatedParams.sort !== undefined) {
        searchParams.set('sort', validatedParams.sort)
      }
      if (validatedParams.page !== undefined) {
        searchParams.set('page', String(validatedParams.page))
      }
      if (validatedParams.size !== undefined) {
        searchParams.set('size', String(validatedParams.size))
      }
    }

    const query = searchParams.toString()
    const path = `/api/sales/customers/${externalId}/sales${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return salePageResponseSchema.parse(raw)
  },
}
