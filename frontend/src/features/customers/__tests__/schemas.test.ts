import { describe, expect, it } from 'vitest'
import {
  createCustomerRequestSchema,
  customerPageResponseSchema,
  customerQuerySchema,
  customerRefResponseSchema,
  customerResponseSchema,
  customerSalesHistoryQuerySchema,
  editCustomerRequestSchema,
} from '../schemas/customer.schema.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'

describe('Customers Feature — Zod Schemas Test Suite', () => {
  describe('createCustomerRequestSchema and editCustomerRequestSchema', () => {
    it('validates a complete valid customer creation payload', () => {
      const valid = {
        name: 'Agrícola San Pedro S.A.',
        taxId: '1790012345001',
        email: 'contacto@sanpedro.com',
        phone: '+593 99 123 4567',
        address: 'Km 14.5 Vía a Daule',
      }
      const parsed = createCustomerRequestSchema.parse(valid)
      expect(parsed.name).toBe('Agrícola San Pedro S.A.')
      expect(parsed.taxId).toBe('1790012345001')
      expect(parsed.email).toBe('contacto@sanpedro.com')
      expect(parsed.phone).toBe('+593 99 123 4567')
      expect(parsed.address).toBe('Km 14.5 Vía a Daule')
    })

    it('validates a minimal customer creation with only name', () => {
      const minimal = {
        name: 'Juan Pérez',
      }
      const parsed = createCustomerRequestSchema.parse(minimal)
      expect(parsed.name).toBe('Juan Pérez')
      expect(parsed.taxId).toBeUndefined()
      expect(parsed.email).toBeUndefined()
    })

    it('trims whitespace on fields', () => {
      const untrimmed = {
        name: '   María Gómez   ',
        taxId: '  0912345678  ',
        email: '  maria@ejemplo.com  ',
      }
      const parsed = createCustomerRequestSchema.parse(untrimmed)
      expect(parsed.name).toBe('María Gómez')
      expect(parsed.taxId).toBe('0912345678')
      expect(parsed.email).toBe('maria@ejemplo.com')
    })

    it('rejects empty or blank name', () => {
      expect(() =>
        createCustomerRequestSchema.parse({ name: '' }),
      ).toThrow()
      expect(() =>
        createCustomerRequestSchema.parse({ name: '   ' }),
      ).toThrow()
    })

    it('rejects name exceeding 150 characters', () => {
      const longName = 'A'.repeat(151)
      expect(() =>
        createCustomerRequestSchema.parse({ name: longName }),
      ).toThrow()
    })

    it('rejects taxId exceeding 30 characters', () => {
      const longTaxId = '1'.repeat(31)
      expect(() =>
        createCustomerRequestSchema.parse({
          name: 'Cliente Valido',
          taxId: longTaxId,
        }),
      ).toThrow()
    })

    it('rejects email exceeding 100 characters', () => {
      const longEmail = 'a'.repeat(101) + '@ejemplo.com'
      expect(() =>
        createCustomerRequestSchema.parse({
          name: 'Cliente Valido',
          email: longEmail,
        }),
      ).toThrow()
    })

    it('rejects phone exceeding 50 characters', () => {
      const longPhone = '9'.repeat(51)
      expect(() =>
        createCustomerRequestSchema.parse({
          name: 'Cliente Valido',
          phone: longPhone,
        }),
      ).toThrow()
    })

    it('rejects address exceeding 255 characters', () => {
      const longAddress = 'X'.repeat(256)
      expect(() =>
        createCustomerRequestSchema.parse({
          name: 'Cliente Valido',
          address: longAddress,
        }),
      ).toThrow()
    })

    it('editCustomerRequestSchema accepts same shape', () => {
      const editData = {
        name: 'Nombre Actualizado',
        taxId: null,
      }
      expect(editCustomerRequestSchema.parse(editData).name).toBe(
        'Nombre Actualizado',
      )
    })
  })

  describe('customerResponseSchema', () => {
    it('parses full customer entity response', () => {
      const payload = {
        externalId: VALID_UUID_1,
        name: 'Hacienda La Gloria',
        taxId: '1790098765001',
        email: 'info@lagloria.com',
        phone: '+593 4 2123456',
        address: 'Sector Los Lojas',
        active: true,
        createdAt: '2026-08-30T10:00:00Z',
        updatedAt: '2026-08-30T10:00:00Z',
      }
      const parsed = customerResponseSchema.parse(payload)
      expect(parsed.externalId).toBe(VALID_UUID_1)
      expect(parsed.name).toBe('Hacienda La Gloria')
      expect(parsed.active).toBe(true)
    })

    it('rejects invalid externalId UUID', () => {
      const invalid = {
        externalId: 'not-a-valid-uuid',
        name: 'Cliente Invalido',
        active: true,
        createdAt: '2026-08-30T10:00:00Z',
        updatedAt: '2026-08-30T10:00:00Z',
      }
      expect(() => customerResponseSchema.parse(invalid)).toThrow()
    })
  })

  describe('customerRefResponseSchema', () => {
    it('parses customer reference object', () => {
      const ref = {
        externalId: VALID_UUID_1,
        name: 'Cliente Referencia',
        taxId: '1234567890',
      }
      const parsed = customerRefResponseSchema.parse(ref)
      expect(parsed.externalId).toBe(VALID_UUID_1)
      expect(parsed.name).toBe('Cliente Referencia')
    })
  })

  describe('customerPageResponseSchema', () => {
    it('parses paginated customers list response', () => {
      const payload = {
        content: [
          {
            externalId: VALID_UUID_1,
            name: 'Cliente 1',
            taxId: null,
            email: null,
            phone: null,
            address: null,
            active: true,
            createdAt: '2026-08-30T10:00:00Z',
            updatedAt: '2026-08-30T10:00:00Z',
          },
        ],
        totalElements: 1,
        page: 0,
        size: 20,
      }
      const parsed = customerPageResponseSchema.parse(payload)
      expect(parsed.content).toHaveLength(1)
      expect(parsed.totalElements).toBe(1)
      expect(parsed.size).toBe(20)
    })
  })

  describe('customerQuerySchema', () => {
    it('parses customer list query parameters', () => {
      const query = {
        search: 'Agrícola',
        active: true,
        page: 1,
        size: 15,
        sort: 'name,asc',
      }
      const parsed = customerQuerySchema.parse(query)
      expect(parsed.search).toBe('Agrícola')
      expect(parsed.active).toBe(true)
      expect(parsed.page).toBe(1)
    })
  })

  describe('customerSalesHistoryQuerySchema', () => {
    it('parses customer sales history query parameters', () => {
      const historyQuery = {
        status: 'COMPLETED' as const,
        from: '2026-08-01T00:00:00Z',
        to: '2026-08-31T23:59:59Z',
        page: 0,
        size: 10,
        sort: 'createdAt,desc',
      }
      const parsed = customerSalesHistoryQuerySchema.parse(historyQuery)
      expect(parsed.status).toBe('COMPLETED')
      expect(parsed.page).toBe(0)
    })
  })
})
