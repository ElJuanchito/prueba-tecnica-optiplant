export type UserRole = 'ADMIN' | 'BRANCH_MANAGER' | 'OPERATOR'

export const Permissions = {
  canAccessIam: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER',

  canManageBranches: (role?: UserRole): boolean => role === 'ADMIN',

  canManageUsers: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER',

  canViewAuditLogs: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER',

  canManageCatalog: (role?: UserRole): boolean => role === 'ADMIN',

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

  canAccessTransfers: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER' || role === 'OPERATOR',

  canRequestTransfer: (role?: UserRole, hasBranch?: boolean): boolean =>
    role === 'ADMIN'
      ? Boolean(hasBranch)
      : role === 'BRANCH_MANAGER' || role === 'OPERATOR',

  canReviewTransfer: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER',

  canDispatchTransfer: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER' || role === 'OPERATOR',

  canReceiveTransfer: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER' || role === 'OPERATOR',

  canCancelTransfer: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER',

  canAccessLogistics: (role?: UserRole): boolean =>
    role === 'ADMIN' || role === 'BRANCH_MANAGER',

  canManageRoutes: (role?: UserRole): boolean => role === 'ADMIN',

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
