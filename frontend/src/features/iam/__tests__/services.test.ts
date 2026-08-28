import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiClientModule from '@/lib/api-client.ts'
import { authService } from '../services/auth.service.ts'
import { userService } from '../services/user.service.ts'
import { branchService } from '../services/branch.service.ts'
import { auditService } from '../services/audit.service.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'
const VALID_UUID_3 = 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33'
const VALID_UUID_4 = 'd0eebc99-9c0b-4ef8-bb6d-6bb9bd380a44'

describe('IAM Services', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  describe('authService', () => {
    it('login sends credentials, stores session, and returns response', async () => {
      const mockResponse = {
        accessToken: 'access-jwt-123',
        refreshToken: 'refresh-uuid-456',
        expiresInSeconds: 900,
        role: 'ADMIN',
        branchId: null,
      }

      vi.spyOn(apiClientModule, 'apiClient').mockResolvedValueOnce(mockResponse)

      const result = await authService.login({
        username: 'admin',
        password: 'password123',
      })

      expect(result).toEqual(mockResponse)
      const session = authService.getSession()
      expect(session).not.toBeNull()
      expect(session?.accessToken).toBe('access-jwt-123')
      expect(session?.username).toBe('admin')
      expect(session?.role).toBe('ADMIN')
    })

    it('logout calls endpoint and clears stored session', async () => {
      authService.getSession()
      apiClientModule.saveSession({
        accessToken: 'token',
        refreshToken: 'ref-token',
        expiresInSeconds: 900,
        role: 'ADMIN',
        branchId: null,
      })

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(undefined as unknown as void)

      await authService.logout({ refreshToken: 'ref-token' })

      expect(apiClientSpy).toHaveBeenCalledWith('/api/auth/logout', {
        method: 'POST',
        body: JSON.stringify({ refreshToken: 'ref-token' }),
      })
      expect(authService.getSession()).toBeNull()
    })
  })

  describe('userService', () => {
    it('listUsers formats query parameters and validates response', async () => {
      const mockPage = {
        content: [
          {
            externalId: VALID_UUID_1,
            username: 'user1',
            email: 'user1@optiplant.com',
            fullName: 'User One',
            role: 'OPERATOR',
            branchId: VALID_UUID_2,
            active: true,
          },
        ],
        totalElements: 1,
        page: 0,
        size: 20,
      }

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockPage)

      const result = await userService.listUsers({
        active: true,
        role: 'OPERATOR',
        page: 0,
        size: 20,
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        '/api/admin/users?active=true&role=OPERATOR&page=0&size=20',
        { method: 'GET' },
      )
      expect(result).toEqual(mockPage)
    })

    it('createUser sends POST payload and parses UserResponse', async () => {
      const mockUser = {
        externalId: VALID_UUID_1,
        username: 'newuser',
        email: 'newuser@optiplant.com',
        fullName: 'New User',
        role: 'OPERATOR',
        branchId: VALID_UUID_2,
        active: true,
      }

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockUser)

      const result = await userService.createUser({
        username: 'newuser',
        email: 'newuser@optiplant.com',
        password: 'password123',
        fullName: 'New User',
        role: 'OPERATOR',
        branchId: VALID_UUID_2,
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        '/api/admin/users',
        expect.objectContaining({ method: 'POST' }),
      )
      expect(result).toEqual(mockUser)
    })

    it('disableUser sends PATCH request to disable endpoint', async () => {
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(undefined as unknown as void)

      await userService.disableUser(VALID_UUID_1)

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/admin/users/${VALID_UUID_1}/disable`,
        { method: 'PATCH' },
      )
    })
  })

  describe('branchService', () => {
    it('listBranches fetches and parses branches', async () => {
      const mockBranches = {
        content: [
          {
            externalId: VALID_UUID_2,
            code: 'BOG-01',
            name: 'Bogotá',
            address: 'Calle 100',
            city: 'Bogotá',
            phone: '1234567',
            active: true,
          },
        ],
        totalElements: 1,
        page: 0,
        size: 20,
      }

      vi.spyOn(apiClientModule, 'apiClient').mockResolvedValueOnce(mockBranches)

      const result = await branchService.listBranches({ active: true })
      expect(result).toEqual(mockBranches)
    })

    it('createBranch creates branch successfully', async () => {
      const mockBranch = {
        externalId: VALID_UUID_2,
        code: 'MED-01',
        name: 'Medellín',
        address: 'Calle 10',
        city: 'Medellín',
        phone: null,
        active: true,
      }

      vi.spyOn(apiClientModule, 'apiClient').mockResolvedValueOnce(mockBranch)

      const result = await branchService.createBranch({
        code: 'MED-01',
        name: 'Medellín',
        address: 'Calle 10',
        city: 'Medellín',
        phone: null,
      })

      expect(result).toEqual(mockBranch)
    })
  })

  describe('auditService', () => {
    it('listAuditLogs formats filters and parses audit log records', async () => {
      const mockAudit = {
        content: [
          {
            externalId: VALID_UUID_3,
            actorUserId: VALID_UUID_1,
            branchId: null,
            action: 'USER_CREATE',
            entityName: 'UserAccount',
            entityId: VALID_UUID_4,
            payloadBefore: null,
            payloadAfter: '{}',
            ipAddress: '127.0.0.1',
            createdAt: '2026-08-28T00:00:00Z',
          },
        ],
        totalElements: 1,
        page: 0,
        size: 15,
      }

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockAudit)

      const result = await auditService.listAuditLogs({
        entityName: 'UserAccount',
        action: 'USER_CREATE',
        page: 0,
        size: 15,
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        '/api/audit?entityName=UserAccount&action=USER_CREATE&page=0&size=15',
        { method: 'GET' },
      )
      expect(result).toEqual(mockAudit)
    })
  })
})
