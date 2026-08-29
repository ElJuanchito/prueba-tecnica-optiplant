export type UserRole = 'ADMIN' | 'BRANCH_MANAGER' | 'OPERATOR'

export const Permissions = {
  canAccessIam: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER',

  canManageBranches: (role?: UserRole): boolean =>
    role === 'ADMIN',

  canManageUsers: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER',

  canViewAuditLogs: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER',

  canManageCatalog: (role?: UserRole): boolean =>
    role === 'ADMIN',

  canAccessInventory: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER' || role === 'OPERATOR',

  canAdjustStock: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER',

  canWriteOffStock: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER' || role === 'OPERATOR',

  canSetThresholds: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER',

  canViewKardex: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER',

  canAccessAlerts: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER',

  getDefaultRoute: (role?: UserRole): string => {
    switch (role) {
      case 'OPERATOR':
      case 'BRANCH_MANAGER':
        return '/inventory'
      case 'ADMIN':
      default:
        return '/'
    }
  },
} as const
