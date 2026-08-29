import { describe, expect, it } from 'vitest'
import {
  alertPageResponseSchema,
  alertQuerySchema,
  alertResponseSchema,
  alertSeveritySchema,
  alertTypeSchema,
} from '../schemas/index.ts'

const VALID_UUID_1 = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'

describe('Notifications Zod Schemas', () => {
  it('validates AlertType and AlertSeverity schemas', () => {
    expect(alertTypeSchema.parse('STOCK_MINIMUM')).toBe('STOCK_MINIMUM')
    expect(alertTypeSchema.parse('LOGISTIC_DELAY')).toBe('LOGISTIC_DELAY')
    expect(alertTypeSchema.parse('TRANSFER_DISCREPANCY')).toBe('TRANSFER_DISCREPANCY')
    expect(alertTypeSchema.parse('PRICE_CHANGE')).toBe('PRICE_CHANGE')
    expect(() => alertTypeSchema.parse('INVALID_ALERT')).toThrow()

    expect(alertSeveritySchema.parse('CRITICAL')).toBe('CRITICAL')
    expect(alertSeveritySchema.parse('WARNING')).toBe('WARNING')
    expect(alertSeveritySchema.parse('INFO')).toBe('INFO')
    expect(() => alertSeveritySchema.parse('FATAL')).toThrow()
  })

  it('validates alertResponseSchema for active and resolved alerts', () => {
    const activeAlert = alertResponseSchema.parse({
      externalId: VALID_UUID_1,
      alertType: 'STOCK_MINIMUM',
      severity: 'CRITICAL',
      title: 'STOCK_MINIMUM:a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
      message: 'Stock fell below minimum threshold (0 remaining)',
      isResolved: false,
      resolvedAt: null,
      createdAt: '2026-08-29T00:00:00Z',
    })

    expect(activeAlert.isResolved).toBe(false)
    expect(activeAlert.severity).toBe('CRITICAL')

    const resolvedAlert = alertResponseSchema.parse({
      externalId: VALID_UUID_1,
      alertType: 'LOGISTIC_DELAY',
      severity: 'WARNING',
      title: 'LOGISTIC_DELAY:TR-100',
      message: 'Transfer delivery delayed',
      isResolved: true,
      resolvedAt: '2026-08-29T02:00:00Z',
      createdAt: '2026-08-29T00:00:00Z',
    })

    expect(resolvedAlert.isResolved).toBe(true)
    expect(resolvedAlert.resolvedAt).toBe('2026-08-29T02:00:00Z')
  })

  it('validates alertPageResponseSchema', () => {
    const page = alertPageResponseSchema.parse({
      content: [
        {
          externalId: VALID_UUID_1,
          alertType: 'STOCK_MINIMUM',
          severity: 'WARNING',
          title: 'STOCK_MINIMUM:PROD-01',
          message: 'Product stock is low',
          isResolved: false,
          resolvedAt: null,
          createdAt: '2026-08-29T00:00:00Z',
        },
      ],
      totalElements: 1,
      page: 0,
      size: 20,
    })

    expect(page.totalElements).toBe(1)
    expect(page.content[0]?.severity).toBe('WARNING')
  })

  it('validates alertQuerySchema', () => {
    const query = alertQuerySchema.parse({
      resolved: false,
      severity: 'CRITICAL',
      alertType: 'STOCK_MINIMUM',
      page: 0,
      size: 15,
    })

    expect(query.resolved).toBe(false)
    expect(query.severity).toBe('CRITICAL')
  })
})
