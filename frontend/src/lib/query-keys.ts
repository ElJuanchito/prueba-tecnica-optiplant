export const queryKeys = {
  auth: {
    session: ['auth', 'session'] as const,
  },
  users: {
    all: ['users'] as const,
    lists: () => [...queryKeys.users.all, 'list'] as const,
    list: (filters: Record<string, unknown>) =>
      [...queryKeys.users.lists(), filters] as const,
    detail: (externalId: string) =>
      [...queryKeys.users.all, 'detail', externalId] as const,
  },
  branches: {
    all: ['branches'] as const,
    lists: () => [...queryKeys.branches.all, 'list'] as const,
    list: (filters: Record<string, unknown>) =>
      [...queryKeys.branches.lists(), filters] as const,
    detail: (externalId: string) =>
      [...queryKeys.branches.all, 'detail', externalId] as const,
  },
  audit: {
    all: ['audit'] as const,
    lists: () => [...queryKeys.audit.all, 'list'] as const,
    list: (filters: Record<string, unknown>) =>
      [...queryKeys.audit.lists(), filters] as const,
  },
  categories: {
    all: ['categories'] as const,
    lists: () => [...queryKeys.categories.all, 'list'] as const,
    list: (filters: Record<string, unknown>) =>
      [...queryKeys.categories.lists(), filters] as const,
    detail: (externalId: string) =>
      [...queryKeys.categories.all, 'detail', externalId] as const,
  },
  products: {
    all: ['products'] as const,
    lists: () => [...queryKeys.products.all, 'list'] as const,
    list: (filters: Record<string, unknown>) =>
      [...queryKeys.products.lists(), filters] as const,
    detail: (externalId: string) =>
      [...queryKeys.products.all, 'detail', externalId] as const,
  },
  productUnits: {
    all: ['productUnits'] as const,
    byProduct: (productExternalId: string) =>
      [...queryKeys.productUnits.all, productExternalId] as const,
  },
  inventory: {
    all: ['inventory'] as const,
    stock: {
      all: ['inventory', 'stock'] as const,
      lists: () => [...queryKeys.inventory.stock.all, 'list'] as const,
      list: (filters: Record<string, unknown>) =>
        [...queryKeys.inventory.stock.lists(), filters] as const,
      network: (productExternalId: string) =>
        [
          ...queryKeys.inventory.stock.all,
          'network',
          productExternalId,
        ] as const,
    },
    kardex: {
      all: ['inventory', 'kardex'] as const,
      lists: () => [...queryKeys.inventory.kardex.all, 'list'] as const,
      list: (filters: Record<string, unknown>) =>
        [...queryKeys.inventory.kardex.lists(), filters] as const,
    },
  },
  notifications: {
    all: ['notifications'] as const,
    alerts: {
      all: ['notifications', 'alerts'] as const,
      lists: () => [...queryKeys.notifications.alerts.all, 'list'] as const,
      list: (filters: Record<string, unknown>) =>
        [...queryKeys.notifications.alerts.lists(), filters] as const,
      detail: (externalId: string) =>
        [...queryKeys.notifications.alerts.all, 'detail', externalId] as const,
    },
  },
  transfers: {
    all: ['transfers'] as const,
    lists: () => [...queryKeys.transfers.all, 'list'] as const,
    list: (filters: Record<string, unknown>) =>
      [...queryKeys.transfers.lists(), filters] as const,
    detail: (externalId: string) =>
      [...queryKeys.transfers.all, 'detail', externalId] as const,
  },
  logistics: {
    all: ['logistics'] as const,
    routes: {
      all: ['logistics', 'routes'] as const,
      lists: () => [...queryKeys.logistics.routes.all, 'list'] as const,
      list: (filters: Record<string, unknown>) =>
        [...queryKeys.logistics.routes.lists(), filters] as const,
    },
    monitor: {
      all: ['logistics', 'monitor'] as const,
      lists: () => [...queryKeys.logistics.monitor.all, 'list'] as const,
      list: (filters: Record<string, unknown>) =>
        [...queryKeys.logistics.monitor.lists(), filters] as const,
    },
    compliance: {
      all: ['logistics', 'compliance'] as const,
      lists: () => [...queryKeys.logistics.compliance.all, 'list'] as const,
      list: (filters: Record<string, unknown>) =>
        [...queryKeys.logistics.compliance.lists(), filters] as const,
    },
  },
} as const
