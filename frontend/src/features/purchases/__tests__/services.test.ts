import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as apiClientModule from '@/lib/api-client.ts'
import { purchasesService } from '../services/purchases.service.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'
const VALID_UUID_3 = 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33'

describe('Purchases Service Tests (All 14 §6 Endpoints)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  const mockSupplier = {
    externalId: VALID_UUID_1,
    taxId: '1790012345001',
    name: 'AgroQuímica del Norte S.A.',
    contactName: 'Ing. Carlos Mendoza',
    email: 'ventas@agronorte.com',
    phone: '+593 99 123 4567',
    address: 'Av. Panamericana Km 12',
    active: true,
    createdAt: '2026-08-30T10:00:00Z',
    updatedAt: null,
  }

  const mockOrderDetail = {
    externalId: VALID_UUID_1,
    orderNumber: 'OC-2026-0001',
    status: 'PENDING',
    branch: { externalId: VALID_UUID_2, name: 'Sucursal Matriz' },
    supplier: {
      externalId: VALID_UUID_1,
      taxId: '1790012345001',
      name: 'AgroQuímica del Norte S.A.',
    },
    createdBy: { externalId: VALID_UUID_2, username: 'operator1' },
    paymentTerms: '30 días',
    totalAmount: 950.0,
    notes: 'Entregar en bodega',
    cancellationReason: null,
    createdAt: '2026-08-30T10:00:00Z',
    updatedAt: null,
    receivedAt: null,
    items: [
      {
        externalId: VALID_UUID_3,
        productExternalId: VALID_UUID_2,
        sku: 'FERT-01',
        name: 'Fertilizante Foliar 1L',
        orderedQuantity: 100,
        receivedQuantity: 0,
        pendingQuantity: 100,
        unitCost: 10.0,
        discountPercent: 5.0,
        effectiveUnitCost: 9.5,
        subtotal: 950.0,
      },
    ],
  }

  const mockOrderSummary = {
    externalId: VALID_UUID_1,
    orderNumber: 'OC-2026-0001',
    status: 'PENDING',
    branch: { externalId: VALID_UUID_2, name: 'Sucursal Matriz' },
    supplier: {
      externalId: VALID_UUID_1,
      taxId: '1790012345001',
      name: 'AgroQuímica del Norte S.A.',
    },
    totalAmount: 950.0,
    createdAt: '2026-08-30T10:00:00Z',
    receivedAt: null,
  }

  const mockCostHistoryItem = {
    orderExternalId: VALID_UUID_1,
    orderNumber: 'OC-2026-0001',
    supplier: {
      externalId: VALID_UUID_1,
      name: 'AgroQuímica del Norte S.A.',
    },
    unitCost: 10.0,
    discountPercent: 5.0,
    effectiveUnitCost: 9.5,
    quantity: 100,
    orderedAt: '2026-08-30T10:00:00Z',
    receivedAt: '2026-08-30T14:00:00Z',
  }

  // --- 1. Suppliers Endpoints (CU-COM-01) ---
  describe('Supplier Management Endpoints', () => {
    it('1. POST /api/purchases/suppliers -> createSupplier', async () => {
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockSupplier)

      const result = await purchasesService.createSupplier({
        taxId: '1790012345001',
        name: 'AgroQuímica del Norte S.A.',
        contactName: 'Ing. Carlos Mendoza',
        email: 'ventas@agronorte.com',
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        '/api/purchases/suppliers',
        expect.objectContaining({
          method: 'POST',
        }),
      )
      expect(result.taxId).toBe('1790012345001')
    })

    it('2. GET /api/purchases/suppliers -> listSuppliers', async () => {
      const mockPage = {
        content: [mockSupplier],
        totalElements: 1,
        page: 0,
        size: 20,
      }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockPage)

      const result = await purchasesService.listSuppliers({
        search: 'Agro',
        active: true,
        page: 0,
        size: 20,
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        '/api/purchases/suppliers?search=Agro&active=true&page=0&size=20',
        { method: 'GET' },
      )
      expect(result.content).toHaveLength(1)
    })

    it('3. GET /api/purchases/suppliers/{externalId} -> getSupplier', async () => {
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockSupplier)

      const result = await purchasesService.getSupplier(VALID_UUID_1)

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/purchases/suppliers/${VALID_UUID_1}`,
        { method: 'GET' },
      )
      expect(result.name).toBe('AgroQuímica del Norte S.A.')
    })

    it('4. PUT /api/purchases/suppliers/{externalId} -> updateSupplier', async () => {
      const updated = { ...mockSupplier, name: 'Nombre Actualizado' }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(updated)

      const result = await purchasesService.updateSupplier(VALID_UUID_1, {
        name: 'Nombre Actualizado',
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/purchases/suppliers/${VALID_UUID_1}`,
        expect.objectContaining({
          method: 'PUT',
        }),
      )
      expect(result.name).toBe('Nombre Actualizado')
    })

    it('5. PATCH /api/purchases/suppliers/{externalId}/disable -> disableSupplier', async () => {
      const disabled = { ...mockSupplier, active: false }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(disabled)

      const result = await purchasesService.disableSupplier(VALID_UUID_1)

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/purchases/suppliers/${VALID_UUID_1}/disable`,
        { method: 'PATCH' },
      )
      expect(result.active).toBe(false)
    })

    it('6. PATCH /api/purchases/suppliers/{externalId}/enable -> enableSupplier', async () => {
      const enabled = { ...mockSupplier, active: true }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(enabled)

      const result = await purchasesService.enableSupplier(VALID_UUID_1)

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/purchases/suppliers/${VALID_UUID_1}/enable`,
        { method: 'PATCH' },
      )
      expect(result.active).toBe(true)
    })
  })

  // --- 2. Purchase Orders Endpoints (CU-COM-02..05) ---
  describe('Purchase Orders Endpoints', () => {
    it('7. POST /api/purchases/orders -> createOrder', async () => {
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockOrderDetail)

      const result = await purchasesService.createOrder({
        supplierExternalId: VALID_UUID_1,
        paymentTerms: '30 días',
        items: [
          {
            productExternalId: VALID_UUID_2,
            quantity: 100,
            unitCost: 10.0,
            discountPercent: 5.0,
          },
        ],
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        '/api/purchases/orders',
        expect.objectContaining({
          method: 'POST',
        }),
      )
      expect(result.orderNumber).toBe('OC-2026-0001')
    })

    it('8. GET /api/purchases/orders -> listOrders', async () => {
      const mockPage = {
        content: [mockOrderSummary],
        totalElements: 1,
        page: 0,
        size: 15,
      }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockPage)

      const result = await purchasesService.listOrders({
        status: 'PENDING',
        page: 0,
        size: 15,
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        '/api/purchases/orders?status=PENDING&page=0&size=15',
        { method: 'GET' },
      )
      expect(result.content).toHaveLength(1)
    })

    it('9. GET /api/purchases/orders/{externalId} -> getOrderDetail', async () => {
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockOrderDetail)

      const result = await purchasesService.getOrderDetail(VALID_UUID_1)

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/purchases/orders/${VALID_UUID_1}`,
        { method: 'GET' },
      )
      expect(result.orderNumber).toBe('OC-2026-0001')
    })

    it('10. PUT /api/purchases/orders/{externalId} -> updateOrder', async () => {
      const updated = { ...mockOrderDetail, paymentTerms: 'Contado' }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(updated)

      const result = await purchasesService.updateOrder(VALID_UUID_1, {
        supplierExternalId: VALID_UUID_1,
        paymentTerms: 'Contado',
        items: [
          {
            productExternalId: VALID_UUID_2,
            quantity: 50,
            unitCost: 10.0,
          },
        ],
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/purchases/orders/${VALID_UUID_1}`,
        expect.objectContaining({
          method: 'PUT',
        }),
      )
      expect(result.paymentTerms).toBe('Contado')
    })

    it('11. POST /api/purchases/orders/{externalId}/approval -> approveOrder', async () => {
      const approved = { ...mockOrderDetail, status: 'APPROVED' as const }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(approved)

      const result = await purchasesService.approveOrder(VALID_UUID_1)

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/purchases/orders/${VALID_UUID_1}/approval`,
        { method: 'POST' },
      )
      expect(result.status).toBe('APPROVED')
    })

    it('12. POST /api/purchases/orders/{externalId}/cancellation -> cancelOrder', async () => {
      const cancelled = {
        ...mockOrderDetail,
        status: 'CANCELLED' as const,
        cancellationReason: 'Proveedor sin stock',
      }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(cancelled)

      const result = await purchasesService.cancelOrder(VALID_UUID_1, {
        reason: 'Proveedor sin stock',
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/purchases/orders/${VALID_UUID_1}/cancellation`,
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ reason: 'Proveedor sin stock' }),
        }),
      )
      expect(result.status).toBe('CANCELLED')
    })

    it('13. POST /api/purchases/orders/{externalId}/receptions -> registerReception', async () => {
      const received = {
        ...mockOrderDetail,
        status: 'RECEIVED' as const,
        receivedAt: '2026-08-30T14:00:00Z',
      }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(received)

      const result = await purchasesService.registerReception(VALID_UUID_1, {
        notes: 'Guía GR-001',
        items: [
          {
            itemExternalId: VALID_UUID_3,
            receivedQuantity: 100,
          },
        ],
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/purchases/orders/${VALID_UUID_1}/receptions`,
        expect.objectContaining({
          method: 'POST',
        }),
      )
      expect(result.status).toBe('RECEIVED')
    })
  })

  // --- 3. Cost History Endpoint (CU-COM-05, R-26) ---
  describe('Cost History Endpoint', () => {
    it('14. GET /api/purchases/cost-history -> getCostHistory', async () => {
      const mockPage = {
        content: [mockCostHistoryItem],
        totalElements: 1,
        page: 0,
        size: 15,
      }
      const apiClientSpy = vi
        .spyOn(apiClientModule, 'apiClient')
        .mockResolvedValueOnce(mockPage)

      const result = await purchasesService.getCostHistory({
        productExternalId: VALID_UUID_2,
        supplierExternalId: VALID_UUID_1,
        page: 0,
        size: 15,
      })

      expect(apiClientSpy).toHaveBeenCalledWith(
        `/api/purchases/cost-history?productExternalId=${VALID_UUID_2}&supplierExternalId=${VALID_UUID_1}&page=0&size=15`,
        { method: 'GET' },
      )
      expect(result.content).toHaveLength(1)
      expect(result.content[0]?.effectiveUnitCost).toBe(9.5)
    })
  })
})
