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
} as const
