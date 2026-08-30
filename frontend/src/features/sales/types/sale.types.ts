import { z } from 'zod'
import {
  branchRefResponseSchema,
  cancellationRequestSchema,
  priceListRefResponseSchema,
  registerSaleItemRequestSchema,
  registerSaleRequestSchema,
  saleAggregatesResponseSchema,
  saleDetailResponseSchema,
  saleItemResponseSchema,
  salePageResponseSchema,
  saleQuerySchema,
  saleStatusSchema,
  saleSummaryResponseSchema,
  userRefResponseSchema,
} from '../schemas/sale.schema.ts'

export type SaleStatus = z.infer<typeof saleStatusSchema>
export type BranchRefResponse = z.infer<typeof branchRefResponseSchema>
export type UserRefResponse = z.infer<typeof userRefResponseSchema>
export type PriceListRefResponse = z.infer<typeof priceListRefResponseSchema>
export type SaleItemResponse = z.infer<typeof saleItemResponseSchema>
export type SaleDetailResponse = z.infer<typeof saleDetailResponseSchema>
export type SaleSummaryResponse = z.infer<typeof saleSummaryResponseSchema>
export type SaleAggregatesResponse = z.infer<typeof saleAggregatesResponseSchema>
export type SalePageResponse = z.infer<typeof salePageResponseSchema>
export type RegisterSaleItemRequest = z.infer<
  typeof registerSaleItemRequestSchema
>
export type RegisterSaleRequest = z.infer<typeof registerSaleRequestSchema>
export type CancellationRequest = z.infer<typeof cancellationRequestSchema>
export type SaleQueryParams = z.infer<typeof saleQuerySchema>
