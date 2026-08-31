import { apiClient } from '@/lib/api-client.ts'
import {
  cancellationRequestSchema,
  costHistoryPageResponseSchema,
  costHistoryQuerySchema,
  createPurchaseOrderRequestSchema,
  createSupplierRequestSchema,
  purchaseOrderDetailResponseSchema,
  purchaseOrderPageResponseSchema,
  purchaseOrderQuerySchema,
  registerReceptionRequestSchema,
  supplierPageResponseSchema,
  supplierQuerySchema,
  supplierResponseSchema,
  updatePurchaseOrderRequestSchema,
  updateSupplierRequestSchema,
} from '../schemas/index.ts'
import type {
  CancellationRequest,
  CostHistoryPageResponse,
  CostHistoryQueryParams,
  CreatePurchaseOrderRequest,
  CreateSupplierRequest,
  PurchaseOrderDetailResponse,
  PurchaseOrderPageResponse,
  PurchaseOrderQueryParams,
  RegisterReceptionRequest,
  SupplierPageResponse,
  SupplierQueryParams,
  SupplierResponse,
  UpdatePurchaseOrderRequest,
  UpdateSupplierRequest,
} from '../types/index.ts'

export const purchasesService = {
  // --- Suppliers (CU-COM-01) ---

  async createSupplier(
    input: CreateSupplierRequest,
  ): Promise<SupplierResponse> {
    const validatedInput = createSupplierRequestSchema.parse(input)
    const raw = await apiClient<unknown>('/api/purchases/suppliers', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })
    return supplierResponseSchema.parse(raw)
  },

  async listSuppliers(
    params?: SupplierQueryParams,
  ): Promise<SupplierPageResponse> {
    const validatedParams = params
      ? supplierQuerySchema.parse(params)
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
      if (validatedParams.sort !== undefined && validatedParams.sort !== '') {
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
    const path = `/api/purchases/suppliers${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return supplierPageResponseSchema.parse(raw)
  },

  async getSupplier(externalId: string): Promise<SupplierResponse> {
    const raw = await apiClient<unknown>(
      `/api/purchases/suppliers/${externalId}`,
      { method: 'GET' },
    )
    return supplierResponseSchema.parse(raw)
  },

  async updateSupplier(
    externalId: string,
    input: UpdateSupplierRequest,
  ): Promise<SupplierResponse> {
    const validatedInput = updateSupplierRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/purchases/suppliers/${externalId}`,
      {
        method: 'PUT',
        body: JSON.stringify(validatedInput),
      },
    )
    return supplierResponseSchema.parse(raw)
  },

  async disableSupplier(externalId: string): Promise<SupplierResponse> {
    const raw = await apiClient<unknown>(
      `/api/purchases/suppliers/${externalId}/disable`,
      { method: 'PATCH' },
    )
    return supplierResponseSchema.parse(raw)
  },

  async enableSupplier(externalId: string): Promise<SupplierResponse> {
    const raw = await apiClient<unknown>(
      `/api/purchases/suppliers/${externalId}/enable`,
      { method: 'PATCH' },
    )
    return supplierResponseSchema.parse(raw)
  },

  // --- Purchase Orders (CU-COM-02, CU-COM-03, CU-COM-04, CU-COM-05) ---

  async createOrder(
    input: CreatePurchaseOrderRequest,
  ): Promise<PurchaseOrderDetailResponse> {
    const validatedInput = createPurchaseOrderRequestSchema.parse(input)
    const raw = await apiClient<unknown>('/api/purchases/orders', {
      method: 'POST',
      body: JSON.stringify(validatedInput),
    })
    return purchaseOrderDetailResponseSchema.parse(raw)
  },

  async listOrders(
    params?: PurchaseOrderQueryParams,
  ): Promise<PurchaseOrderPageResponse> {
    const validatedParams = params
      ? purchaseOrderQuerySchema.parse(params)
      : undefined
    const searchParams = new URLSearchParams()

    if (validatedParams) {
      if (validatedParams.supplierExternalId !== undefined) {
        searchParams.set(
          'supplierExternalId',
          validatedParams.supplierExternalId,
        )
      }
      if (validatedParams.productExternalId !== undefined) {
        searchParams.set('productExternalId', validatedParams.productExternalId)
      }
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
    const path = `/api/purchases/orders${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return purchaseOrderPageResponseSchema.parse(raw)
  },

  async getOrderDetail(
    externalId: string,
  ): Promise<PurchaseOrderDetailResponse> {
    const raw = await apiClient<unknown>(
      `/api/purchases/orders/${externalId}`,
      { method: 'GET' },
    )
    return purchaseOrderDetailResponseSchema.parse(raw)
  },

  async updateOrder(
    externalId: string,
    input: UpdatePurchaseOrderRequest,
  ): Promise<PurchaseOrderDetailResponse> {
    const validatedInput = updatePurchaseOrderRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/purchases/orders/${externalId}`,
      {
        method: 'PUT',
        body: JSON.stringify(validatedInput),
      },
    )
    return purchaseOrderDetailResponseSchema.parse(raw)
  },

  async approveOrder(externalId: string): Promise<PurchaseOrderDetailResponse> {
    const raw = await apiClient<unknown>(
      `/api/purchases/orders/${externalId}/approval`,
      { method: 'POST' },
    )
    return purchaseOrderDetailResponseSchema.parse(raw)
  },

  async cancelOrder(
    externalId: string,
    input: CancellationRequest,
  ): Promise<PurchaseOrderDetailResponse> {
    const validatedInput = cancellationRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/purchases/orders/${externalId}/cancellation`,
      {
        method: 'POST',
        body: JSON.stringify(validatedInput),
      },
    )
    return purchaseOrderDetailResponseSchema.parse(raw)
  },

  async registerReception(
    externalId: string,
    input: RegisterReceptionRequest,
  ): Promise<PurchaseOrderDetailResponse> {
    const validatedInput = registerReceptionRequestSchema.parse(input)
    const raw = await apiClient<unknown>(
      `/api/purchases/orders/${externalId}/receptions`,
      {
        method: 'POST',
        body: JSON.stringify(validatedInput),
      },
    )
    return purchaseOrderDetailResponseSchema.parse(raw)
  },

  // --- Agreed Cost History (CU-COM-05, R-26) ---

  async getCostHistory(
    params: CostHistoryQueryParams,
  ): Promise<CostHistoryPageResponse> {
    const validatedParams = costHistoryQuerySchema.parse(params)
    const searchParams = new URLSearchParams()

    searchParams.set('productExternalId', validatedParams.productExternalId)
    if (validatedParams.supplierExternalId !== undefined) {
      searchParams.set('supplierExternalId', validatedParams.supplierExternalId)
    }
    if (validatedParams.from !== undefined) {
      searchParams.set('from', validatedParams.from)
    }
    if (validatedParams.to !== undefined) {
      searchParams.set('to', validatedParams.to)
    }
    if (validatedParams.page !== undefined) {
      searchParams.set('page', String(validatedParams.page))
    }
    if (validatedParams.size !== undefined) {
      searchParams.set('size', String(validatedParams.size))
    }

    const query = searchParams.toString()
    const path = `/api/purchases/cost-history${query ? `?${query}` : ''}`
    const raw = await apiClient<unknown>(path, { method: 'GET' })
    return costHistoryPageResponseSchema.parse(raw)
  },
}
