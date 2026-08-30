import { describe, expect, it } from 'vitest'
import {
  approvalRequestSchema,
  dispatchRequestSchema,
  reasonRequestSchema,
  receiptRequestSchema,
  requestTransferRequestSchema,
  TRANSFER_DIRECTION,
  TRANSFER_PRIORITY,
  TRANSFER_STATUS,
  transferDetailResponseSchema,
  transferDirectionSchema,
  transferPageResponseSchema,
  transferPrioritySchema,
  transferQuerySchema,
  transferStatusSchema,
  transferSummaryResponseSchema,
} from '../schemas/index.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
const VALID_UUID_2 = 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22'
const VALID_UUID_3 = 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33'

describe('Transfers Zod Schemas', () => {
  describe('Status, Priority, and Direction Literals', () => {
    it('validates all 6 TransferStatus literals', () => {
      const statuses = [
        TRANSFER_STATUS.REQUESTED,
        TRANSFER_STATUS.IN_PREPARATION,
        TRANSFER_STATUS.IN_TRANSIT,
        TRANSFER_STATUS.RECEIVED,
        TRANSFER_STATUS.RECEIVED_WITH_DISCREPANCY,
        TRANSFER_STATUS.CANCELLED,
      ]

      statuses.forEach((status) => {
        expect(transferStatusSchema.parse(status)).toBe(status)
      })

      expect(() => transferStatusSchema.parse('INVALID_STATUS')).toThrow()
    })

    it('validates all 3 TransferPriority literals', () => {
      const priorities = [
        TRANSFER_PRIORITY.LOW,
        TRANSFER_PRIORITY.STANDARD,
        TRANSFER_PRIORITY.URGENT,
      ]

      priorities.forEach((priority) => {
        expect(transferPrioritySchema.parse(priority)).toBe(priority)
      })

      expect(() => transferPrioritySchema.parse('CRITICAL')).toThrow()
    })

    it('validates TransferDirection literals', () => {
      expect(transferDirectionSchema.parse(TRANSFER_DIRECTION.INBOUND)).toBe(
        'INBOUND',
      )
      expect(transferDirectionSchema.parse(TRANSFER_DIRECTION.OUTBOUND)).toBe(
        'OUTBOUND',
      )
      expect(() => transferDirectionSchema.parse('UNKNOWN')).toThrow()
    })
  })

  describe('Request Transfer Schemas (CU-TRA-01)', () => {
    it('validates valid requestTransferRequestSchema', () => {
      const valid = requestTransferRequestSchema.parse({
        originBranchExternalId: VALID_UUID_1,
        priority: 'URGENT',
        notes: 'Urgent stock replenishment',
        items: [
          { productExternalId: VALID_UUID_2, requestedQuantity: 25.5 },
          { productExternalId: VALID_UUID_3, requestedQuantity: 10 },
        ],
      })

      expect(valid.originBranchExternalId).toBe(VALID_UUID_1)
      expect(valid.priority).toBe('URGENT')
      expect(valid.items).toHaveLength(2)
    })

    it('rejects empty items array or non-positive quantity', () => {
      expect(() =>
        requestTransferRequestSchema.parse({
          originBranchExternalId: VALID_UUID_1,
          priority: 'STANDARD',
          items: [],
        }),
      ).toThrow()

      expect(() =>
        requestTransferRequestSchema.parse({
          originBranchExternalId: VALID_UUID_1,
          priority: 'STANDARD',
          items: [{ productExternalId: VALID_UUID_2, requestedQuantity: 0 }],
        }),
      ).toThrow()

      expect(() =>
        requestTransferRequestSchema.parse({
          originBranchExternalId: VALID_UUID_1,
          priority: 'STANDARD',
          items: [{ productExternalId: VALID_UUID_2, requestedQuantity: -5 }],
        }),
      ).toThrow()
    })
  })

  describe('Approval & Rejection Schemas (CU-TRA-02)', () => {
    it('validates valid approvalRequestSchema', () => {
      const valid = approvalRequestSchema.parse({
        items: [{ itemExternalId: VALID_UUID_1, approvedQuantity: 20 }],
        notes: 'Approved partial batch due to local reserve',
      })

      expect(valid.items[0]?.approvedQuantity).toBe(20)
    })

    it('validates reasonRequestSchema and rejects empty/whitespace strings', () => {
      const valid = reasonRequestSchema.parse({
        reason: '  Insufficient stock  ',
      })
      expect(valid.reason).toBe('Insufficient stock')

      expect(() => reasonRequestSchema.parse({ reason: '' })).toThrow()
      expect(() => reasonRequestSchema.parse({ reason: '   ' })).toThrow()
    })
  })

  describe('Dispatch Schemas (CU-TRA-03)', () => {
    it('validates valid dispatchRequestSchema', () => {
      const valid = dispatchRequestSchema.parse({
        carrierName: 'Servientrega S.A.',
        trackingNumber: 'TRK-2026-99',
        estimatedArrivalAt: '2026-08-30T18:00:00Z',
        items: [{ itemExternalId: VALID_UUID_1, dispatchedQuantity: 20 }],
      })

      expect(valid.carrierName).toBe('Servientrega S.A.')
      expect(valid.items[0]?.dispatchedQuantity).toBe(20)
    })

    it('rejects missing or empty carrierName', () => {
      expect(() =>
        dispatchRequestSchema.parse({
          carrierName: '',
          items: [{ itemExternalId: VALID_UUID_1, dispatchedQuantity: 20 }],
        }),
      ).toThrow()
    })
  })

  describe('Receipt Schemas (CU-TRA-04 & CU-TRA-05)', () => {
    it('validates valid receiptRequestSchema', () => {
      const valid = receiptRequestSchema.parse({
        items: [
          {
            itemExternalId: VALID_UUID_1,
            receivedQuantity: 18,
            discrepancyReason: '2 units damaged in transit box',
          },
        ],
      })

      expect(valid.items[0]?.receivedQuantity).toBe(18)
      expect(valid.items[0]?.discrepancyReason).toBe(
        '2 units damaged in transit box',
      )
    })

    it('rejects negative receivedQuantity', () => {
      expect(() =>
        receiptRequestSchema.parse({
          items: [{ itemExternalId: VALID_UUID_1, receivedQuantity: -1 }],
        }),
      ).toThrow()
    })
  })

  describe('Response & Summary Schemas', () => {
    it('validates transferDetailResponseSchema with full response structure', () => {
      const parsed = transferDetailResponseSchema.parse({
        externalId: VALID_UUID_1,
        transferNumber: 'TRF-2026-0001',
        status: 'IN_TRANSIT',
        priority: 'URGENT',
        originBranch: { externalId: VALID_UUID_2, name: 'Sucursal Norte' },
        destinationBranch: { externalId: VALID_UUID_3, name: 'Sucursal Sur' },
        carrierName: 'DHL Express',
        trackingNumber: 'DHL-102938',
        dispatchedAt: '2026-08-29T10:00:00Z',
        estimatedArrivalAt: '2026-08-30T10:00:00Z',
        actualArrivalAt: null,
        deviationHours: null,
        observations: ['Approved with adjustment', 'Dispatched on time'],
        requestedBy: VALID_UUID_1,
        dispatchedBy: VALID_UUID_2,
        receivedBy: null,
        createdAt: '2026-08-29T08:00:00Z',
        updatedAt: '2026-08-29T10:00:00Z',
        items: [
          {
            externalId: VALID_UUID_1,
            productExternalId: VALID_UUID_2,
            sku: 'FERT-UREA',
            name: 'Urea 46%',
            requestedQuantity: 50,
            dispatchedQuantity: 45,
            receivedQuantity: null,
            discrepancyQuantity: null,
            discrepancyReason: null,
          },
        ],
      })

      expect(parsed.transferNumber).toBe('TRF-2026-0001')
      expect(parsed.status).toBe('IN_TRANSIT')
      expect(parsed.items[0]?.dispatchedQuantity).toBe(45)
    })

    it('validates transferPageResponseSchema and transferSummaryResponseSchema', () => {
      const summary = transferSummaryResponseSchema.parse({
        externalId: VALID_UUID_1,
        transferNumber: 'TRF-2026-0002',
        status: 'REQUESTED',
        priority: 'STANDARD',
        originBranch: { externalId: VALID_UUID_2, name: 'Norte' },
        destinationBranch: { externalId: VALID_UUID_3, name: 'Sur' },
        createdAt: '2026-08-29T00:00:00Z',
        estimatedArrivalAt: null,
      })

      const page = transferPageResponseSchema.parse({
        content: [summary],
        totalElements: 1,
        page: 0,
        size: 20,
      })

      expect(page.totalElements).toBe(1)
      expect(page.content[0]?.status).toBe('REQUESTED')
    })

    it('validates transferQuerySchema with optional filter criteria', () => {
      const query = transferQuerySchema.parse({
        status: 'IN_TRANSIT',
        direction: 'INBOUND',
        page: 0,
        size: 15,
      })

      expect(query.status).toBe('IN_TRANSIT')
      expect(query.direction).toBe('INBOUND')
    })
  })
})
