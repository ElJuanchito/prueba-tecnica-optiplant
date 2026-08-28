import { z } from 'zod'
import { roleSchema, uuidSchema } from './common.schema.ts'

export const loginRequestSchema = z.object({
  username: z.string().trim().min(1, 'Username is required'),
  password: z.string().min(1, 'Password is required'),
})

export const loginResponseSchema = z.object({
  accessToken: z.string().min(1),
  refreshToken: z.string().min(1),
  expiresInSeconds: z.number().positive(),
  role: roleSchema,
  branchId: uuidSchema.nullable(),
  branchName: z.string().nullable().optional(),
  branchCode: z.string().nullable().optional(),
})

export const refreshRequestSchema = z.object({
  refreshToken: z.string().trim().min(1, 'Refresh token is required'),
})

export const refreshResponseSchema = z.object({
  accessToken: z.string().min(1),
  refreshToken: z.string().min(1),
  expiresInSeconds: z.number().positive(),
})

export const logoutRequestSchema = z.object({
  refreshToken: z.string().trim().min(1, 'Refresh token is required'),
})

export type LoginRequest = z.infer<typeof loginRequestSchema>
export type LoginResponse = z.infer<typeof loginResponseSchema>
export type RefreshRequest = z.infer<typeof refreshRequestSchema>
export type RefreshResponse = z.infer<typeof refreshResponseSchema>
export type LogoutRequest = z.infer<typeof logoutRequestSchema>
