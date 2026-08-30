import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query'
import { queryKeys } from '@/lib/query-keys.ts'
import { branchService } from '../services/branch.service.ts'
import type {
  BranchPageResponse,
  BranchQueryParams,
  BranchResponse,
  CreateBranchInput,
  EditBranchInput,
} from '../types/branch.types.ts'

export function useBranches(
  params?: BranchQueryParams,
  enabled: boolean = true,
): UseQueryResult<BranchPageResponse, Error> {
  return useQuery({
    queryKey: queryKeys.branches.list(
      (params ?? {}) as Record<string, unknown>,
    ),
    queryFn: () => branchService.listBranches(params),
    enabled,
  })
}

export function useCreateBranch(): UseMutationResult<
  BranchResponse,
  Error,
  CreateBranchInput
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: CreateBranchInput) => branchService.createBranch(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.branches.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useEditBranch(): UseMutationResult<
  BranchResponse,
  Error,
  { externalId: string; input: EditBranchInput }
> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ externalId, input }) =>
      branchService.editBranch(externalId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.branches.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}

export function useDisableBranch(): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (externalId: string) => branchService.disableBranch(externalId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.branches.all })
      queryClient.invalidateQueries({ queryKey: queryKeys.audit.all })
    },
  })
}
