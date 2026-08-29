import { describe, expect, it } from 'vitest'
import {
  createCategorySchema,
  editCategorySchema,
  categoryResponseSchema,
  categoryQuerySchema,
} from '../schemas/category.schema.ts'
import {
  createProductSchema,
  editProductSchema,
  productDetailResponseSchema,
  productListItemResponseSchema,
  productQuerySchema,
  unitPayloadRequestSchema,
} from '../schemas/product.schema.ts'
import {
  unitRequestSchema,
  productUnitItemResponseSchema,
  productUnitListResponseSchema,
} from '../schemas/product-unit.schema.ts'

const VALID_UUID = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'

describe('Catalog Zod Schemas', () => {
  describe('Category Schemas', () => {
    it('validates valid CreateCategoryInput and trims whitespace', () => {
      const valid = createCategorySchema.parse({
        name: '  Fertilizantes  ',
        description: '  Insumos agrícolas  ',
      })
      expect(valid.name).toBe('Fertilizantes')
      expect(valid.description).toBe('Insumos agrícolas')
    })

    it('rejects empty or oversized category name', () => {
      expect(() => createCategorySchema.parse({ name: '' })).toThrow()
      expect(() =>
        createCategorySchema.parse({ name: 'a'.repeat(101) }),
      ).toThrow()
    })

    it('validates valid EditCategoryInput and trims whitespace', () => {
      const valid = editCategorySchema.parse({
        name: '  Fertilizantes Editados  ',
        description: '  Insumos  ',
      })
      expect(valid.name).toBe('Fertilizantes Editados')
      expect(valid.description).toBe('Insumos')
    })

    it('parses category response shape', () => {
      const parsed = categoryResponseSchema.parse({
        externalId: VALID_UUID,
        name: 'Herbicidas',
        description: null,
        active: true,
        activeProductCount: 5,
        createdAt: '2026-08-28T00:00:00Z',
        updatedAt: '2026-08-28T00:00:00Z',
      })
      expect(parsed.externalId).toBe(VALID_UUID)
      expect(parsed.activeProductCount).toBe(5)
    })

    it('validates category query params', () => {
      const parsed = categoryQuerySchema.parse({
        name: 'fert',
        active: 'true',
        page: 0,
        size: 20,
      })
      expect(parsed.name).toBe('fert')
      expect(parsed.active).toBe('true')
    })
  })

  describe('Product Schemas', () => {
    it('validates valid CreateProductInput with valid SKU and BaseUnit', () => {
      const parsed = createProductSchema.parse({
        sku: 'FERT-NPK-151515',
        name: 'Fertilizante Triple 15',
        description: 'Saco de fertilizante',
        categoryExternalId: VALID_UUID,
        baseUnit: 'KG',
        units: [
          {
            unitName: 'SACO_50KG',
            conversionFactor: 50,
            defaultSaleUnit: true,
          },
        ],
      })
      expect(parsed.sku).toBe('FERT-NPK-151515')
      expect(parsed.baseUnit).toBe('KG')
      expect(parsed.units?.[0]?.conversionFactor).toBe(50)
    })

    it('rejects invalid baseUnit format (lowercase or special characters)', () => {
      expect(() =>
        createProductSchema.parse({
          sku: 'SKU-1',
          name: 'Product 1',
          categoryExternalId: VALID_UUID,
          baseUnit: 'Saco de 50',
        }),
      ).toThrow()
    })

    it('validates unitPayloadRequest with positive factor', () => {
      const valid = unitPayloadRequestSchema.parse({
        unitName: 'CAJA',
        conversionFactor: 12,
        defaultSaleUnit: false,
      })
      expect(valid.conversionFactor).toBe(12)

      expect(() =>
        unitPayloadRequestSchema.parse({
          unitName: 'CAJA',
          conversionFactor: 0,
          defaultSaleUnit: false,
        }),
      ).toThrow()

      expect(() =>
        unitPayloadRequestSchema.parse({
          unitName: 'CAJA',
          conversionFactor: -5,
          defaultSaleUnit: false,
        }),
      ).toThrow()
    })

    it('validates editProductSchema without baseUnit', () => {
      const parsed = editProductSchema.parse({
        sku: 'SKU-UPDATED',
        name: 'Product Updated',
        description: null,
        categoryExternalId: VALID_UUID,
      })
      expect(parsed.sku).toBe('SKU-UPDATED')
    })

    it('validates productDetailResponseSchema', () => {
      const detail = productDetailResponseSchema.parse({
        externalId: VALID_UUID,
        sku: 'SKU-TEST',
        name: 'Test Product',
        description: 'Details',
        baseUnit: 'UNIDAD',
        active: true,
        category: {
          externalId: VALID_UUID,
          name: 'General',
          active: true,
        },
        units: [
          {
            externalId: VALID_UUID,
            unitName: 'DOCENA',
            conversionFactor: 12,
            defaultSaleUnit: true,
          },
        ],
        createdAt: '2026-08-28T00:00:00Z',
        updatedAt: '2026-08-28T00:00:00Z',
      })
      expect(detail.sku).toBe('SKU-TEST')
      expect(detail.units.length).toBe(1)
    })

    it('validates productListItemResponseSchema', () => {
      const item = productListItemResponseSchema.parse({
        externalId: VALID_UUID,
        sku: 'SKU-ITEM',
        name: 'Item Summary',
        baseUnit: 'KG',
        active: true,
        category: {
          externalId: VALID_UUID,
          name: 'Fertilizantes',
          active: true,
        },
        createdAt: '2026-08-28T00:00:00Z',
        updatedAt: '2026-08-28T00:00:00Z',
      })
      expect(item.sku).toBe('SKU-ITEM')
    })

    it('validates product query parameters', () => {
      const query = productQuerySchema.parse({
        q: 'npk',
        categoryId: VALID_UUID,
        active: 'all',
        sort: 'name',
        direction: 'desc',
        page: 1,
        size: 50,
      })
      expect(query.q).toBe('npk')
      expect(query.sort).toBe('name')
      expect(query.direction).toBe('desc')
    })
  })

  describe('Product Unit Schemas', () => {
    it('validates unitRequestSchema', () => {
      const unit = unitRequestSchema.parse({
        unitName: 'PALLET_40',
        conversionFactor: 40,
        defaultSaleUnit: false,
      })
      expect(unit.unitName).toBe('PALLET_40')
      expect(unit.conversionFactor).toBe(40)
    })

    it('validates productUnitItemResponseSchema', () => {
      const item = productUnitItemResponseSchema.parse({
        externalId: VALID_UUID,
        unitName: 'CAJA',
        conversionFactor: 12,
        defaultSaleUnit: true,
        createdAt: '2026-08-28T00:00:00Z',
      })
      expect(item.externalId).toBe(VALID_UUID)
      expect(item.unitName).toBe('CAJA')
    })

    it('validates productUnitListResponseSchema', () => {
      const list = productUnitListResponseSchema.parse([
        {
          externalId: VALID_UUID,
          unitName: 'CAJA',
          conversionFactor: 12,
          defaultSaleUnit: true,
          createdAt: '2026-08-28T00:00:00Z',
        },
      ])
      expect(list.length).toBe(1)
      expect(list[0]?.unitName).toBe('CAJA')
    })
  })
})
