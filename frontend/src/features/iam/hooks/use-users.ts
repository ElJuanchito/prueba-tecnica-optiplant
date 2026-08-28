import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import { userService } from '../services/user.service.ts'
import type {
  CreateUserInput,
  EditUserInput,
  UserPageResponse,
  UserQueryParams,
  UserResponse,
} from '../types/user.types.ts'

export function useUsers(
  params?: UserQueryParams,
): UseQueryResult<UserPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.users.list((params ?? {}) as Record<string, unknown>),
    queryFn: () => userService.listUsers(params),
  })
}

export function useCreateUser(): UseMutationResult<
  UserResponse,
  Error,
  CreateUserInput
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: CreateUserInput) => userService.createUser(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.users.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useEditUser(): UseMutationResult<
  UserResponse,
  Error,
  { externalId: string; input: EditUserInput }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      userService.editUser(externalId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.users.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useDisableUser(): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (externalId: string) => userService.disableUser(externalId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.users.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}
