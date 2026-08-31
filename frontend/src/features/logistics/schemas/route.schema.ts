import { z } from 'zod'
import {
  paginatedResponseSchema,
  uuidSchema,
} from '@/features/iam/schemas/common.schema.ts'

export const ROUTE_PRIORITY = {
  LOW: 'LOW',
  STANDARD: 'STANDARD',
  URGENT: 'URGENT',
} as const

export type RoutePriority = (typeof ROUTE_PRIORITY)[keyof typeof ROUTE_PRIORITY]

export const routePrioritySchema = z.enum(['LOW', 'STANDARD', 'URGENT'])

// The closed allow-list of `GET /api/logistics/routes` sort tokens (RF-LOG-03).
// Each value fixes both a field and a direction, mirroring the backend's
// `RouteSort` enum — there is no separate ascending/descending toggle.
export const ROUTE_SORT = {
  COST_ASC: 'cost_asc',
  DURATION_ASC: 'duration_asc',
  PRIORITY_DESC: 'priority_desc',
} as const

export type RouteSortOption = (typeof ROUTE_SORT)[keyof typeof ROUTE_SORT]

export const routeSortSchema = z.enum([
  'cost_asc',
  'duration_asc',
  'priority_desc',
])

export const branchReferenceSchema = z.object({
  externalId: uuidSchema,
  name: z.string(),
})

export const createRouteRequestSchema = z.object({
  originBranchExternalId: uuidSchema,
  destinationBranchExternalId: uuidSchema,
  estimatedDurationHours: z
    .number({ message: 'Duration must be a valid number' })
    .positive('Estimated duration must be greater than 0'),
  transportCost: z
    .number({ message: 'Transport cost must be a valid number' })
    .nonnegative('Transport cost cannot be negative'),
  priorityLevel: routePrioritySchema,
})

export const updateRouteRequestSchema = z.object({
  estimatedDurationHours: z
    .number({ message: 'Duration must be a valid number' })
    .positive('Estimated duration must be greater than 0'),
  transportCost: z
    .number({ message: 'Transport cost must be a valid number' })
    .nonnegative('Transport cost cannot be negative'),
  priorityLevel: routePrioritySchema,
})

export const routeResponseSchema = z.object({
  externalId: uuidSchema,
  originBranch: branchReferenceSchema.nullable().optional(),
  destinationBranch: branchReferenceSchema.nullable().optional(),
  estimatedDurationHours: z.number(),
  transportCost: z.number(),
  priorityLevel: routePrioritySchema,
  active: z.boolean(),
  createdAt: z.string(),
})

export const routePageResponseSchema =
  paginatedResponseSchema(routeResponseSchema)

export const routeQuerySchema = z.object({
  active: z.boolean().optional(),
  sort: routeSortSchema.optional(),
  page: z.number().int().nonnegative().optional(),
  size: z.number().int().positive().optional(),
})

export type CreateRouteRequest = z.infer<typeof createRouteRequestSchema>
export type UpdateRouteRequest = z.infer<typeof updateRouteRequestSchema>
export type RouteResponse = z.infer<typeof routeResponseSchema>
export type RoutePageResponse = z.infer<typeof routePageResponseSchema>
export type RouteQueryParams = z.infer<typeof routeQuerySchema>
