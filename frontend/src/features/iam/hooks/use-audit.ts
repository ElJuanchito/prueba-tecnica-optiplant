import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import { auditService } from '../services/audit.service.ts'
import type {
  AuditPageResponse,
  AuditQueryParams,
} from '../types/audit.types.ts'

export function useAuditLogs(
  params?: AuditQueryParams,
): UseQueryResult<AuditPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.audit.list((params ?? {}) as Record<string, unknown>),
    queryFn: () => auditService.listAuditLogs(params),
  })
}
