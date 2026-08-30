import { describe, expect, it } from 'vitest'
import { Permissions, type UserRole } from '../permissions.ts'

describe('RBAC Permissions Matrix', () => {
  describe('ADMIN Capabilities', () => {
    const role: UserRole = 'ADMIN'

    it('has full access across all modules and governance', () => {
      expect(Permissions.canAccessIam(role)).toBe(true)
      expect(Permissions.canManageBranches(role)).toBe(true)
      expect(Permissions.canManageUsers(role)).toBe(true)
      expect(Permissions.canViewAuditLogs(role)).toBe(true)
      expect(Permissions.canManageCatalog(role)).toBe(true)
      expect(Permissions.canAccessInventory(role)).toBe(true)
      expect(Permissions.canAdjustStock(role)).toBe(true)
      expect(Permissions.canWriteOffStock(role)).toBe(true)
      expect(Permissions.canSetThresholds(role)).toBe(true)
      expect(Permissions.canViewKardex(role)).toBe(true)
      expect(Permissions.canAccessAlerts(role)).toBe(true)
      expect(Permissions.canAccessCustomers(role)).toBe(true)
      expect(Permissions.canManageCustomers(role)).toBe(true)
      expect(Permissions.canDeactivateCustomers(role)).toBe(true)
      expect(Permissions.getDefaultRoute(role)).toBe('/')
    })
  })

  describe('BRANCH_MANAGER Capabilities', () => {
    const role: UserRole = 'BRANCH_MANAGER'

    it('has branch inventory, alert, and scoped user/audit management access', () => {
      expect(Permissions.canAccessIam(role)).toBe(true)
      expect(Permissions.canManageBranches(role)).toBe(false) // ADMIN only
      expect(Permissions.canManageUsers(role)).toBe(true) // Operators in own branch
      expect(Permissions.canViewAuditLogs(role)).toBe(true) // Own branch only
      expect(Permissions.canManageCatalog(role)).toBe(false) // Read-only
      expect(Permissions.canAccessInventory(role)).toBe(true)
      expect(Permissions.canAdjustStock(role)).toBe(true)
      expect(Permissions.canWriteOffStock(role)).toBe(true)
      expect(Permissions.canSetThresholds(role)).toBe(true)
      expect(Permissions.canViewKardex(role)).toBe(true)
      expect(Permissions.canAccessAlerts(role)).toBe(true)
      expect(Permissions.canAccessCustomers(role)).toBe(true)
      expect(Permissions.canManageCustomers(role)).toBe(true) // Create & Edit
      expect(Permissions.canDeactivateCustomers(role)).toBe(false) // ADMIN only
      expect(Permissions.getDefaultRoute(role)).toBe('/inventory')
    })
  })

  describe('OPERATOR Capabilities', () => {
    const role: UserRole = 'OPERATOR'

    it('has strictly scoped operational stock access and write-offs only', () => {
      expect(Permissions.canAccessIam(role)).toBe(false)
      expect(Permissions.canManageBranches(role)).toBe(false)
      expect(Permissions.canManageUsers(role)).toBe(false)
      expect(Permissions.canViewAuditLogs(role)).toBe(false)
      expect(Permissions.canManageCatalog(role)).toBe(false) // Read-only
      expect(Permissions.canAccessInventory(role)).toBe(true)
      expect(Permissions.canAdjustStock(role)).toBe(false) // Denied CU-INV-05
      expect(Permissions.canWriteOffStock(role)).toBe(true) // Allowed CU-INV-06
      expect(Permissions.canSetThresholds(role)).toBe(false) // Denied CU-INV-07
      expect(Permissions.canViewKardex(role)).toBe(false) // Denied CU-INV-08
      expect(Permissions.canAccessAlerts(role)).toBe(false) // Denied CU-ALE-02
      expect(Permissions.canAccessCustomers(role)).toBe(true)
      expect(Permissions.canManageCustomers(role)).toBe(true) // Create & Edit at counter
      expect(Permissions.canDeactivateCustomers(role)).toBe(false) // ADMIN only
      expect(Permissions.getDefaultRoute(role)).toBe('/inventory')
    })
  })
})
