import { z } from 'zod'
import {
  createCustomerRequestSchema,
  customerPageResponseSchema,
  customerQuerySchema,
  customerRefResponseSchema,
  customerResponseSchema,
  customerSalesHistoryQuerySchema,
  editCustomerRequestSchema,
} from '../schemas/customer.schema.ts'

export type CustomerResponse = z.infer<typeof customerResponseSchema>
export type CustomerRefResponse = z.infer<typeof customerRefResponseSchema>
export type CustomerPageResponse = z.infer<typeof customerPageResponseSchema>
export type CreateCustomerRequest = z.infer<typeof createCustomerRequestSchema>
export type EditCustomerRequest = z.infer<typeof editCustomerRequestSchema>
export type CustomerQueryParams = z.infer<typeof customerQuerySchema>
export type CustomerSalesHistoryQueryParams = z.infer<
  typeof customerSalesHistoryQuerySchema
>
