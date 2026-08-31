import type { z } from 'zod'
import type {
  branchRefResponseSchema,
  cancellationRequestSchema,
  costHistoryItemResponseSchema,
  costHistoryPageResponseSchema,
  costHistoryQuerySchema,
  createPurchaseOrderItemRequestSchema,
  createPurchaseOrderRequestSchema,
  createSupplierRequestSchema,
  purchaseOrderDetailResponseSchema,
  purchaseOrderItemResponseSchema,
  purchaseOrderPageResponseSchema,
  purchaseOrderQuerySchema,
  purchaseOrderStatusSchema,
  purchaseOrderSummaryResponseSchema,
  registerReceptionItemRequestSchema,
  registerReceptionRequestSchema,
  supplierPageResponseSchema,
  supplierQuerySchema,
  supplierRefResponseSchema,
  supplierResponseSchema,
  updatePurchaseOrderRequestSchema,
  updateSupplierRequestSchema,
  userRefResponseSchema,
} from '../schemas/purchases.schema.ts'

export type PurchaseOrderStatus = z.infer<typeof purchaseOrderStatusSchema>

export type SupplierResponse = z.infer<typeof supplierResponseSchema>
export type SupplierPageResponse = z.infer<typeof supplierPageResponseSchema>
export type CreateSupplierRequest = z.infer<typeof createSupplierRequestSchema>
export type UpdateSupplierRequest = z.infer<typeof updateSupplierRequestSchema>
export type SupplierQueryParams = z.infer<typeof supplierQuerySchema>

export type BranchRefResponse = z.infer<typeof branchRefResponseSchema>
export type SupplierRefResponse = z.infer<typeof supplierRefResponseSchema>
export type UserRefResponse = z.infer<typeof userRefResponseSchema>

export type PurchaseOrderItemResponse = z.infer<
  typeof purchaseOrderItemResponseSchema
>
export type PurchaseOrderDetailResponse = z.infer<
  typeof purchaseOrderDetailResponseSchema
>
export type PurchaseOrderSummaryResponse = z.infer<
  typeof purchaseOrderSummaryResponseSchema
>
export type PurchaseOrderPageResponse = z.infer<
  typeof purchaseOrderPageResponseSchema
>

export type CreatePurchaseOrderItemRequest = z.infer<
  typeof createPurchaseOrderItemRequestSchema
>
export type CreatePurchaseOrderRequest = z.infer<
  typeof createPurchaseOrderRequestSchema
>
export type UpdatePurchaseOrderRequest = z.infer<
  typeof updatePurchaseOrderRequestSchema
>
export type PurchaseOrderQueryParams = z.infer<typeof purchaseOrderQuerySchema>

export type CancellationRequest = z.infer<typeof cancellationRequestSchema>

export type RegisterReceptionItemRequest = z.infer<
  typeof registerReceptionItemRequestSchema
>
export type RegisterReceptionRequest = z.infer<
  typeof registerReceptionRequestSchema
>

export type CostHistoryItemResponse = z.infer<
  typeof costHistoryItemResponseSchema
>
export type CostHistoryPageResponse = z.infer<
  typeof costHistoryPageResponseSchema
>
export type CostHistoryQueryParams = z.infer<typeof costHistoryQuerySchema>
