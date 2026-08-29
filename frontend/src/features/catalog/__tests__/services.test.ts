import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiClientModule from '@/lib/api-client.ts'
import { categoryService } from '../services/category.service.ts'
import { productService } from '../services/product.service.ts'
import { productUnitService } from '../services/product-unit.service.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'

describe('Catalog Services', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  describe('categoryService', () => {
    it('calls listCategories with query params and returns parsed page response', async () => {
      const mockResponse = {
        content: [
          {
            externalId: VALID_UUID_1,
            name: 'Fertilizantes',
            description: 'Nutrición vegetal',
            active: true,
            activeProductCount: 10,
            createdAt: '2026-08-28T00:00:00Z',
            updatedAt: '2026-08-28T00:00:00Z',
          },
        ],
        totalElements: 1,
        page: 0,
        size: 10,
      }

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockResponse)

      const result = await categoryService.listCategories({
        name: 'Fert',
        active: 'true',
        page: 0,
        size: 10,
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        '/api/catalog/categories?name=Fert&active=true&page=0&size=10',
        { method: 'GET' },
      )
      expect(result.content.length).toBe(1)
      expect(result.content[0]?.name).toBe('Fertilizantes')
    })

    it('calls createCategory with POST payload', async () => {
      const mockCategory = {
        externalId: VALID_UUID_1,
        name: 'Semillas',
        description: null,
        active: true,
        activeProductCount: 0,
        createdAt: '2026-08-28T00:00:00Z',
        updatedAt: '2026-08-28T00:00:00Z',
      }

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockCategory)

      const result = await categoryService.createCategory({
        name: 'Semillas',
      })

      expect(apiClientSpy).toHaveBeenCalledWith('/api/catalog/categories', {
        method: 'POST',
        body: JSON.stringify({ name: 'Semillas' }),
      })
      expect(result.name).toBe('Semillas')
    })

    it('calls editCategory with PUT payload', async () => {
      const mockCategory = {
        externalId: VALID_UUID_1,
        name: 'Semillas Certificadas',
        description: 'Alta pureza',
        active: true,
        activeProductCount: 2,
        createdAt: '2026-08-28T00:00:00Z',
        updatedAt: '2026-08-28T00:00:00Z',
      }

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockCategory)

      const result = await categoryService.editCategory(VALID_UUID_1, {
        name: 'Semillas Certificadas',
        description: 'Alta pureza',
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/catalog/categories/${VALID_UUID_1}`,
        {
          method: 'PUT',
          body: JSON.stringify({
            name: 'Semillas Certificadas',
            description: 'Alta pureza',
          }),
        },
      )
      expect(result.name).toBe('Semillas Certificadas')
    })

    it('calls disableCategory with PATCH', async () => {
      const mockCategory = {
        externalId: VALID_UUID_1,
        name: 'Semillas',
        description: null,
        active: false,
        activeProductCount: 0,
        createdAt: '2026-08-28T00:00:00Z',
        updatedAt: '2026-08-28T00:00:00Z',
      }

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockCategory)

      const result = await categoryService.disableCategory(VALID_UUID_1)

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/catalog/categories/${VALID_UUID_1}/disable`,
        { method: 'PATCH' },
      )
      expect(result.active).toBe(false)
    })

    it('calls enableCategory with PATCH', async () => {
      const mockCategory = {
        externalId: VALID_UUID_1,
        name: 'Semillas',
        description: null,
        active: true,
        activeProductCount: 0,
        createdAt: '2026-08-28T00:00:00Z',
        updatedAt: '2026-08-28T00:00:00Z',
      }

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockCategory)

      const result = await categoryService.enableCategory(VALID_UUID_1)

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/catalog/categories/${VALID_UUID_1}/enable`,
        { method: 'PATCH' },
      )
      expect(result.active).toBe(true)
    })
  })

  describe('productService', () => {
    it('calls listProducts with query filters and returns parsed page response', async () => {
      const mockResponse = {
        content: [
          {
            externalId: VALID_UUID_1,
            sku: 'FERT-NPK-151515',
            name: 'Fertilizante Triple 15',
            baseUnit: 'KG',
            active: true,
            category: {
              externalId: VALID_UUID_2,
              name: 'Fertilizantes',
              active: true,
            },
            createdAt: '2026-08-28T00:00:00Z',
            updatedAt: '2026-08-28T00:00:00Z',
          },
        ],
        totalElements: 1,
        page: 0,
        size: 10,
      }

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockResponse)

      const result = await productService.listProducts({
        q: 'npk',
        categoryId: VALID_UUID_2,
        active: 'true',
        sort: 'sku',
        direction: 'asc',
        page: 0,
        size: 10,
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/catalog/products?q=npk&categoryId=${VALID_UUID_2}&active=true&sort=sku&direction=asc&page=0&size=10`,
        { method: 'GET' },
      )
      expect(result.content[0]?.sku).toBe('FERT-NPK-151515')
    })

    it('calls createProduct with POST payload', async () => {
      const mockDetail = {
        externalId: VALID_UUID_1,
        sku: 'FERT-UREA-46',
        name: 'Urea Agrícola 46%',
        description: 'Nitrógeno concentrado',
        baseUnit: 'KG',
        active: true,
        category: {
          externalId: VALID_UUID_2,
          name: 'Fertilizantes',
          active: true,
        },
        units: [],
        createdAt: '2026-08-28T00:00:00Z',
        updatedAt: '2026-08-28T00:00:00Z',
      }

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockDetail)

      const payload = {
        sku: 'FERT-UREA-46',
        name: 'Urea Agrícola 46%',
        description: 'Nitrógeno concentrado',
        categoryExternalId: VALID_UUID_2,
        baseUnit: 'KG',
      }

      const result = await productService.createProduct(payload)

      expect(apiClientSpy).toHaveBeenCalledWith('/api/catalog/products', {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      expect(result.sku).toBe('FERT-UREA-46')
    })

    it('calls editProduct with PUT payload', async () => {
      const mockDetail = {
        externalId: VALID_UUID_1,
        sku: 'FERT-UREA-46-MOD',
        name: 'Urea Agrícola Modificada',
        description: null,
        baseUnit: 'KG',
        active: true,
        category: {
          externalId: VALID_UUID_2,
          name: 'Fertilizantes',
          active: true,
        },
        units: [],
        createdAt: '2026-08-28T00:00:00Z',
        updatedAt: '2026-08-28T00:00:00Z',
      }

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockDetail)

      const editInput = {
        sku: 'FERT-UREA-46-MOD',
        name: 'Urea Agrícola Modificada',
        categoryExternalId: VALID_UUID_2,
      }

      const result = await productService.editProduct(VALID_UUID_1, editInput)

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/catalog/products/${VALID_UUID_1}`,
        {
          method: 'PUT',
          body: JSON.stringify(editInput),
        },
      )
      expect(result.sku).toBe('FERT-UREA-46-MOD')
    })

    it('calls disableProduct and enableProduct with PATCH', async () => {
      const mockProduct = {
        externalId: VALID_UUID_1,
        sku: 'FERT-1',
        name: 'Product 1',
        baseUnit: 'KG',
        active: false,
        category: null,
        units: [],
        createdAt: '2026-08-28T00:00:00Z',
        updatedAt: '2026-08-28T00:00:00Z',
      }

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockProduct)
        .mockResolvedValueOnce({ ...mockProduct, active: true })

      const disabled = await productService.disableProduct(VALID_UUID_1)
      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/catalog/products/${VALID_UUID_1}/disable`,
        { method: 'PATCH' },
      )
      expect(disabled.active).toBe(false)

      const enabled = await productService.enableProduct(VALID_UUID_1)
      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/catalog/products/${VALID_UUID_1}/enable`,
        { method: 'PATCH' },
      )
      expect(enabled.active).toBe(true)
    })
  })

  describe('productUnitService', () => {
    it('calls listUnits with GET', async () => {
      const mockUnits = [
        {
          externalId: VALID_UUID_2,
          unitName: 'SACO_50KG',
          conversionFactor: 50,
          defaultSaleUnit: true,
          createdAt: '2026-08-28T00:00:00Z',
        },
      ]

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockUnits)

      const result = await productUnitService.listUnits(VALID_UUID_1)

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/catalog/products/${VALID_UUID_1}/units`,
        { method: 'GET' },
      )
      expect(result[0]?.unitName).toBe('SACO_50KG')
    })

    it('calls addUnit with POST', async () => {
      const mockUnit = {
        externalId: VALID_UUID_2,
        unitName: 'CAJA_12',
        conversionFactor: 12,
        defaultSaleUnit: false,
        createdAt: '2026-08-28T00:00:00Z',
      }

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockUnit)

      const payload = {
        unitName: 'CAJA_12',
        conversionFactor: 12,
        defaultSaleUnit: false,
      }

      const result = await productUnitService.addUnit(VALID_UUID_1, payload)

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/catalog/products/${VALID_UUID_1}/units`,
        {
          method: 'POST',
          body: JSON.stringify(payload),
        },
      )
      expect(result.unitName).toBe('CAJA_12')
    })

    it('calls replaceUnit with PUT and deleteUnit with DELETE', async () => {
      const mockUnit = {
        externalId: VALID_UUID_2,
        unitName: 'CAJA_24',
        conversionFactor: 24,
        defaultSaleUnit: true,
        createdAt: '2026-08-28T00:00:00Z',
      }

      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockUnit)
        .mockResolvedValueOnce(undefined)

      const replacePayload = {
        unitName: 'CAJA_24',
        conversionFactor: 24,
        defaultSaleUnit: true,
      }

      const replaced = await productUnitService.replaceUnit(
        VALID_UUID_1,
        VALID_UUID_2,
        replacePayload,
      )
      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/catalog/products/${VALID_UUID_1}/units/${VALID_UUID_2}`,
        {
          method: 'PUT',
          body: JSON.stringify(replacePayload),
        },
      )
      expect(replaced.conversionFactor).toBe(24)

      await productUnitService.deleteUnit(VALID_UUID_1, VALID_UUID_2)
      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/catalog/products/${VALID_UUID_1}/units/${VALID_UUID_2}`,
        { method: 'DELETE' },
      )
    })
  })
})
