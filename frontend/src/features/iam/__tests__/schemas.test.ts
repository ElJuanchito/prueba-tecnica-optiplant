import { describe, expect, it } from 'vitest'
import {
  loginRequestSchema,
  loginResponseSchema,
} from '../schemas/auth.schema.ts'
import {
  createBranchSchema,
  editBranchSchema,
  branchResponseSchema,
} from '../schemas/branch.schema.ts'
import {
  createUserSchema,
  editUserSchema,
  userResponseSchema,
} from '../schemas/user.schema.ts'
import { auditEntryResponseSchema } from '../schemas/audit.schema.ts'
import { uuidSchema } from '../schemas/common.schema.ts'

describe('IAM Schemas', () => {
  describe('UUID Schema', () => {
    it('validates standard seed UUIDs with deterministic hex prefixes', () => {
      expect(uuidSchema.parse('b0000000-0000-0000-0000-000000000001')).toBe(
        'b0000000-0000-0000-0000-000000000001',
      )
      expect(uuidSchema.parse('e0000000-0000-0000-0000-000000000001')).toBe(
        'e0000000-0000-0000-0000-000000000001',
      )
      expect(uuidSchema.parse('c0000000-0000-0000-0000-000000000001')).toBe(
        'c0000000-0000-0000-0000-000000000001',
      )
      expect(uuidSchema.parse('10000000-0000-0000-0000-000000000001')).toBe(
        '10000000-0000-0000-0000-000000000001',
      )
    })

    it('validates standard RFC 4122 random UUIDs', () => {
      expect(uuidSchema.parse('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11')).toBe(
        'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      )
    })

    it('rejects invalid UUID strings', () => {
      expect(() => uuidSchema.parse('not-a-uuid')).toThrow()
      expect(() => uuidSchema.parse('12345')).toThrow()
      expect(() =>
        uuidSchema.parse('g0000000-0000-0000-0000-000000000001'),
      ).toThrow() // 'g' is not hex
    })
  })

  describe('Auth Schemas', () => {
    it('validates valid login request', () => {
      const valid = { username: 'admin', password: 'password123' }
      expect(loginRequestSchema.parse(valid)).toEqual(valid)
    })

    it('rejects empty username or password', () => {
      expect(() =>
        loginRequestSchema.parse({ username: '', password: '123' }),
      ).toThrow()
      expect(() =>
        loginRequestSchema.parse({ username: 'admin', password: '' }),
      ).toThrow()
    })

    it('validates valid login response with seed UUID branchId', () => {
      const response = {
        accessToken: 'access-token-jwt',
        refreshToken: 'refresh-token-uuid',
        expiresInSeconds: 900,
        role: 'BRANCH_MANAGER',
        branchId: 'b0000000-0000-0000-0000-000000000001',
      }
      expect(loginResponseSchema.parse(response)).toEqual(response)
    })

    it('validates login response with null branchId for corporate ADMIN', () => {
      const response = {
        accessToken: 'access-token-jwt',
        refreshToken: 'refresh-token-uuid',
        expiresInSeconds: 900,
        role: 'ADMIN',
        branchId: null,
      }
      expect(loginResponseSchema.parse(response)).toEqual(response)
    })
  })

  describe('User Schemas', () => {
    it('validates valid user creation input with seed branch UUID', () => {
      const input = {
        username: 'oper01',
        email: 'oper01@optiplant.com',
        password: 'securePassword123',
        fullName: 'Operador Uno',
        role: 'OPERATOR',
        branchId: 'b0000000-0000-0000-0000-000000000001',
      }
      expect(createUserSchema.parse(input)).toEqual(input)
    })

    it('rejects password shorter than 8 characters', () => {
      const input = {
        username: 'oper01',
        email: 'oper01@optiplant.com',
        password: 'short',
        fullName: 'Operador Uno',
        role: 'OPERATOR',
        branchId: 'b0000000-0000-0000-0000-000000000001',
      }
      expect(() => createUserSchema.parse(input)).toThrow()
    })

    it('rejects invalid email format', () => {
      const input = {
        username: 'oper01',
        email: 'not-an-email',
        password: 'securePassword123',
        fullName: 'Operador Uno',
        role: 'OPERATOR',
        branchId: 'b0000000-0000-0000-0000-000000000001',
      }
      expect(() => createUserSchema.parse(input)).toThrow()
    })

    it('validates edit user without username or password', () => {
      const input = {
        email: 'updated@optiplant.com',
        fullName: 'Updated Name',
        role: 'BRANCH_MANAGER',
        branchId: 'b0000000-0000-0000-0000-000000000001',
      }
      expect(editUserSchema.parse(input)).toEqual(input)
    })

    it('validates user response DTO structure with seed external_id and branch_id', () => {
      const dto = {
        externalId: 'e0000000-0000-0000-0000-000000000002',
        username: 'gerente.bogota',
        email: 'gerente.bog@optiplant.com',
        fullName: 'Adriana Morales (Gerente Bogotá)',
        role: 'BRANCH_MANAGER',
        branchId: 'b0000000-0000-0000-0000-000000000001',
        active: true,
      }
      expect(userResponseSchema.parse(dto)).toEqual(dto)
    })
  })

  describe('Branch Schemas', () => {
    it('validates valid branch creation', () => {
      const input = {
        code: 'BOG-01',
        name: 'Sede Bogotá Norte',
        address: 'Cra 15 # 100-20',
        city: 'Bogotá',
        phone: '+57 601 1234567',
      }
      expect(createBranchSchema.parse(input)).toEqual(input)
    })

    it('validates edit branch without code', () => {
      const input = {
        name: 'Sede Bogotá Principal',
        address: 'Cra 15 # 100-30',
        city: 'Bogotá',
        phone: null,
      }
      expect(editBranchSchema.parse(input)).toEqual(input)
    })

    it('validates branch response DTO with seed UUID', () => {
      const dto = {
        externalId: 'b0000000-0000-0000-0000-000000000001',
        code: 'SUC-BOG',
        name: 'Sucursal Central Bogotá',
        address: 'Av. El Dorado #68-90, Zona Industrial',
        city: 'Bogotá D.C.',
        phone: '+57 601 7458900',
        active: true,
      }
      expect(branchResponseSchema.parse(dto)).toEqual(dto)
    })
  })

  describe('Audit Schemas', () => {
    it('validates audit entry response DTO with seed UUIDs', () => {
      const dto = {
        externalId: 'a0000000-0000-0000-0000-000000000001',
        actorUserId: 'e0000000-0000-0000-0000-000000000001',
        branchId: 'b0000000-0000-0000-0000-000000000001',
        action: 'USER_CREATE',
        entityName: 'UserAccount',
        entityId: 'e0000000-0000-0000-0000-000000000005',
        payloadBefore: null,
        payloadAfter: '{"username":"operador.bogota","role":"OPERATOR"}',
        ipAddress: '127.0.0.1',
        createdAt: '2026-08-28T07:30:00Z',
      }
      expect(auditEntryResponseSchema.parse(dto)).toEqual(dto)
    })

    it('validates audit entry response DTO with null ipAddress and null actorUserId', () => {
      const dto = {
        externalId: 'a0000000-0000-0000-0000-000000000002',
        actorUserId: null,
        branchId: null,
        action: 'SYSTEM_STARTUP',
        entityName: 'System',
        entityId: 'SYS-INIT',
        payloadBefore: null,
        payloadAfter: null,
        ipAddress: null,
        createdAt: '2026-08-28T07:30:00Z',
      }
      expect(auditEntryResponseSchema.parse(dto)).toEqual(dto)
    })
  })
})
