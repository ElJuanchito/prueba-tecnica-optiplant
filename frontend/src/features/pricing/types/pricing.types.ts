import { z } from 'zod'
import {
  closePriceRequestSchema,
  createPriceListRequestSchema,
  priceListPageResponseSchema,
  priceListQuerySchema,
  priceListResponseSchema,
  pricePageResponseSchema,
  priceQuerySchema,
  priceResponseSchema,
  quoteItemRequestSchema,
  quoteItemResponseSchema,
  quoteRequestSchema,
  quoteResponseSchema,
  setPriceRequestSchema,
  updatePriceListRequestSchema,
} from '../schemas/pricing.schema.ts'

export type PriceListResponse = z.infer<typeof priceListResponseSchema>
export type PriceListPageResponse = z.infer<typeof priceListPageResponseSchema>
export type CreatePriceListRequest = z.infer<
  typeof createPriceListRequestSchema
>
export type UpdatePriceListRequest = z.infer<
  typeof updatePriceListRequestSchema
>
export type PriceResponse = z.infer<typeof priceResponseSchema>
export type PricePageResponse = z.infer<typeof pricePageResponseSchema>
export type SetPriceRequest = z.infer<typeof setPriceRequestSchema>
export type ClosePriceRequest = z.infer<typeof closePriceRequestSchema>
export type QuoteItemRequest = z.infer<typeof quoteItemRequestSchema>
export type QuoteRequest = z.infer<typeof quoteRequestSchema>
export type QuoteItemResponse = z.infer<typeof quoteItemResponseSchema>
export type QuoteResponse = z.infer<typeof quoteResponseSchema>
export type PriceListQueryParams = z.infer<typeof priceListQuerySchema>
export type PriceQueryParams = z.infer<typeof priceQuerySchema>
