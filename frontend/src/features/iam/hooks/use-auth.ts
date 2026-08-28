import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import type { SessionData } from '@/lib/api-client.ts'
import { authService } from '../services/auth.service.ts'
import type { LoginRequest, LoginResponse } from '../types/auth.types.ts'

export function useSession(): UseQueryResult<SessionData | null, Error> {
  return useQuery({
    queryKey: queryKeys.auth.session,
    queryFn: () => authService.getSession(),
    staleTime: Infinity,
  })
}

export function useLogin(): UseMutationResult<
  LoginResponse,
  Error,
  LoginRequest
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: LoginRequest) => authService.login(input),
    onSuccess: (data, variables) => {
      const session: SessionData = {
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        expiresInSeconds: data.expiresInSeconds,
        role: data.role,
        branchId: data.branchId,
        username: variables.username,
      }
      queryClient.setQueryData(queryKeys.auth.session, session)
      queryClient.invalidateQueries({ queryKey: queryKeys.users.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.branches.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useLogout(): UseMutationResult<void, Error, void> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async () => {
      const session = authService.getSession()
      if (session?.refreshToken) {
        await authService.logout({ refreshToken: session.refreshToken })
      }
    },
    onSuccess: () => {
      queryClient.setQueryData(queryKeys.auth.session, null)
      queryClient.clear()
    },
  })
}
