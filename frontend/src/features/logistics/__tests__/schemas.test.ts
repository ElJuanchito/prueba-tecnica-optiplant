import { describe, expect, it } from 'vitest'
import {
  activeTransferPageResponseSchema,
  activeTransferQuerySchema,
  activeTransferResponseSchema,
  COMPLIANCE_GROUPING,
  complianceGroupingSchema,
  compliancePageResponseSchema,
  complianceQuerySchema,
  complianceRowResponseSchema,
  createRouteRequestSchema,
  ROUTE_PRIORITY,
  routePageResponseSchema,
  routePrioritySchema,
  routeQuerySchema,
  routeResponseSchema,
  updateRouteRequestSchema,
} from '../schemas/index.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'

describe('Logistics Zod Schemas', () => {
  describe('Route Schemas (CU-LOG-01)', () => {
    it('validates all 3 RoutePriority literals', () => {
      expect(routePrioritySchema.parse(ROUTE_PRIORITY.LOW)).toBe('LOW')
      expect(routePrioritySchema.parse(ROUTE_PRIORITY.STANDARD)).toBe(
        'STANDARD',
      )
      expect(routePrioritySchema.parse(ROUTE_PRIORITY.URGENT)).toBe('URGENT')
      expect(() => routePrioritySchema.parse('CRITICAL')).toThrow()
    })

    it('validates createRouteRequestSchema with positive duration and non-negative cost', () => {
      const valid = createRouteRequestSchema.parse({
        originBranchExternalId: VALID_UUID_1,
        destinationBranchExternalId: VALID_UUID_2,
        estimatedDurationHours: 12.5,
        transportCost: 150.0,
        priorityLevel: 'STANDARD',
      })

      expect(valid.estimatedDurationHours).toBe(12.5)
      expect(valid.transportCost).toBe(150.0)
    })

    it('rejects duration <= 0 or negative transportCost', () => {
      expect(() =>
        createRouteRequestSchema.parse({
          originBranchExternalId: VALID_UUID_1,
          destinationBranchExternalId: VALID_UUID_2,
          estimatedDurationHours: 0,
          transportCost: 100,
          priorityLevel: 'STANDARD',
        }),
      ).toThrow()

      expect(() =>
        createRouteRequestSchema.parse({
          originBranchExternalId: VALID_UUID_1,
          destinationBranchExternalId: VALID_UUID_2,
          estimatedDurationHours: 10,
          transportCost: -1,
          priorityLevel: 'STANDARD',
        }),
      ).toThrow()
    })

    it('validates updateRouteRequestSchema', () => {
      const valid = updateRouteRequestSchema.parse({
        estimatedDurationHours: 18,
        transportCost: 200,
        priorityLevel: 'URGENT',
      })
      expect(valid.priorityLevel).toBe('URGENT')
    })

    it('validates routeResponseSchema and routePageResponseSchema', () => {
      const route = routeResponseSchema.parse({
        externalId: VALID_UUID_1,
        originBranch: { externalId: VALID_UUID_1, name: 'Norte' },
        destinationBranch: { externalId: VALID_UUID_2, name: 'Sur' },
        estimatedDurationHours: 12,
        transportCost: 100,
        priorityLevel: 'STANDARD',
        active: true,
        createdAt: '2026-08-29T00:00:00Z',
      })

      const page = routePageResponseSchema.parse({
        content: [route],
        totalElements: 1,
        page: 0,
        size: 10,
      })

      expect(page.content[0]?.active).toBe(true)
    })

    it('validates routeQuerySchema', () => {
      const query = routeQuerySchema.parse({
        active: true,
        page: 0,
        size: 20,
      })
      expect(query.active).toBe(true)
    })
  })

  describe('Active Transfers Monitor Schemas (CU-LOG-02)', () => {
    it('validates activeTransferResponseSchema with delay indicator', () => {
      const active = activeTransferResponseSchema.parse({
        transferExternalId: VALID_UUID_1,
        transferNumber: 'TRF-2026-0001',
        status: 'IN_TRANSIT',
        originBranch: { externalId: VALID_UUID_1, name: 'Norte' },
        destinationBranch: { externalId: VALID_UUID_2, name: 'Sur' },
        priority: 'URGENT',
        itemCount: 3,
        totalQuantity: 75.5,
        estimatedArrivalAt: '2026-08-29T12:00:00Z',
        isDelayed: true,
      })

      expect(active.isDelayed).toBe(true)
      expect(active.totalQuantity).toBe(75.5)
    })

    it('validates activeTransferPageResponseSchema and query params', () => {
      const page = activeTransferPageResponseSchema.parse({
        content: [],
        totalElements: 0,
        page: 0,
        size: 10,
      })
      expect(page.totalElements).toBe(0)

      const query = activeTransferQuerySchema.parse({
        status: 'IN_TRANSIT',
        delayed: true,
      })
      expect(query.delayed).toBe(true)
    })
  })

  describe('Compliance Report Schemas (CU-LOG-03)', () => {
    it('validates ComplianceGrouping literals', () => {
      expect(complianceGroupingSchema.parse(COMPLIANCE_GROUPING.ROUTE)).toBe(
        'ROUTE',
      )
      expect(complianceGroupingSchema.parse(COMPLIANCE_GROUPING.BRANCH)).toBe(
        'BRANCH',
      )
      expect(() => complianceGroupingSchema.parse('INVALID')).toThrow()
    })

    it('validates complianceRowResponseSchema with average deviation and unmeasured count', () => {
      const row = complianceRowResponseSchema.parse({
        key: 'ROUTE_1_2',
        label: 'Sucursal Norte → Sucursal Sur',
        deliveredCount: 20,
        onTimeCount: 18,
        onTimePercentage: 90.0,
        averageDeviationHours: 1.5,
        unmeasuredCount: 2,
      })

      expect(row.deliveredCount).toBe(20)
      expect(row.onTimePercentage).toBe(90.0)
      expect(row.unmeasuredCount).toBe(2)
    })

    it('validates compliancePageResponseSchema and complianceQuerySchema', () => {
      const row = complianceRowResponseSchema.parse({
        key: 'ROUTE_1_2',
        label: 'Sucursal Norte → Sucursal Sur',
        deliveredCount: 20,
        onTimeCount: 18,
        onTimePercentage: 90.0,
        averageDeviationHours: 1.5,
        unmeasuredCount: 2,
      })

      const page = compliancePageResponseSchema.parse({
        content: [row],
        totalElements: 1,
        page: 0,
        size: 10,
      })
      expect(page.totalElements).toBe(1)

      const query = complianceQuerySchema.parse({
        from: '2026-08-01T00:00:00Z',
        to: '2026-08-29T23:59:59Z',
        groupBy: 'BRANCH',
      })

      expect(query.groupBy).toBe('BRANCH')
    })
  })
})
