import * as React from 'react'
import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert.tsx'
import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Input } from '@/components/ui/input.tsx'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select.tsx'
import { Skeleton } from '@/components/ui/skeleton.tsx'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table.tsx'
import { useBranches } from '../hooks/use-branches.ts'
import { useDisableUser, useUsers } from '../hooks/use-users.ts'
import type { Role, UserQueryParams, UserResponse } from '../types/index.ts'
import { UserFormDialog } from './UserFormDialog.tsx'
import {
  AlertCircle,
  Building2,
  ChevronLeft,
  ChevronRight,
  Edit2,
  Plus,
  Search,
  Shield,
  User,
  Users,
  UserX,
} from 'lucide-react'

interface UserTableProps {
  currentActorRole?: Role | undefined
  currentActorBranchId?: string | null | undefined
}

export function UserTable({
  currentActorRole = 'ADMIN',
  currentActorBranchId = null,
}: UserTableProps) {
  const isBranchManager = currentActorRole === 'BRANCH_MANAGER'
  const [filters, setFilters] = React.useState<UserQueryParams>({
    page: 0,
    size: 10,
    role: isBranchManager ? 'OPERATOR' : undefined,
    branchId:
      isBranchManager && currentActorBranchId
        ? currentActorBranchId
        : undefined,
  })

  const [searchTerm, setSearchTerm] = React.useState('')
  const [dialogOpen, setDialogOpen] = React.useState(false)
  const [editingUser, setEditingUser] = React.useState<
    UserResponse | null | undefined
  >(null)

  const usersQuery = useUsers(filters)
  const branchesQuery = useBranches()
  const disableMutation = useDisableUser()

  const branchesMap = React.useMemo(() => {
    const map = new Map<string, string>()
    branchesQuery.data?.content.forEach((b) => {
      map.set(b.externalId, `${b.name} (${b.code})`)
    })
    return map
  }, [branchesQuery.data])

  const handleEdit = (user: UserResponse) => {
    setEditingUser(user)
    setDialogOpen(true)
  }

  const handleCreate = () => {
    setEditingUser(null)
    setDialogOpen(true)
  }

  const handleDisable = React.useCallback(
    (user: UserResponse) => {
      if (
        window.confirm(
          `Are you sure you want to disable user "${user.username}"? Active sessions will be revoked.`,
        )
      ) {
        disableMutation.mutate(user.externalId)
      }
    },
    [disableMutation],
  )

  const columns = React.useMemo<ColumnDef<UserResponse>[]>(
    () => [
      {
        accessorKey: 'username',
        header: 'User',
        cell: ({ row }) => {
          const user = row.original
          const initials = user.fullName
            ? user.fullName
                .split(' ')
                .map((n) => n[0])
                .slice(0, 2)
                .join('')
                .toUpperCase()
            : user.username.slice(0, 2).toUpperCase()

          return (
            <div className="flex items-center gap-2.5">
              <div className="h-7 w-7 rounded bg-slate-800 text-white flex items-center justify-center font-bold text-[11px] shrink-0">
                {initials}
              </div>
              <span className="font-semibold text-slate-900 text-sm">
                {user.username}
              </span>
            </div>
          )
        },
      },
      {
        accessorKey: 'fullName',
        header: 'Full Name',
        cell: ({ row }) => (
          <span className="text-slate-700 text-sm">
            {row.original.fullName}
          </span>
        ),
      },
      {
        accessorKey: 'email',
        header: 'Email',
        cell: ({ row }) => (
          <span className="text-slate-600 text-xs font-mono">
            {row.original.email}
          </span>
        ),
      },
      {
        accessorKey: 'role',
        header: 'Role',
        cell: ({ row }) => {
          const role = row.original.role
          if (role === 'ADMIN') {
            return (
              <Badge className="bg-orange-50 text-orange-800 border-orange-200 hover:bg-orange-100 flex items-center gap-1 w-fit text-[11px]">
                <Shield className="h-3 w-3" />
                <span>ADMIN</span>
              </Badge>
            )
          }
          if (role === 'BRANCH_MANAGER') {
            return (
              <Badge
                variant="secondary"
                className="bg-slate-100 text-slate-800 border-slate-200 flex items-center gap-1 w-fit text-[11px]"
              >
                <Building2 className="h-3 w-3" />
                <span>BRANCH_MANAGER</span>
              </Badge>
            )
          }
          return (
            <Badge
              variant="outline"
              className="bg-white text-slate-600 border-slate-200 flex items-center gap-1 w-fit text-[11px]"
            >
              <User className="h-3 w-3" />
              <span>OPERATOR</span>
            </Badge>
          )
        },
      },
      {
        accessorKey: 'branchId',
        header: 'Branch Assignment',
        cell: ({ row }) => {
          const user = row.original
          const branchId = user.branchId
          if (!branchId) {
            return (
              <span className="inline-flex items-center text-xs font-medium text-slate-400 bg-slate-100 px-2 py-0.5 rounded">
                Corporate
              </span>
            )
          }
          const branchName = user.branchName
            ? user.branchCode
              ? `${user.branchName} (${user.branchCode})`
              : user.branchName
            : (branchesMap.get(branchId) ?? 'Assigned Branch')

          return (
            <span className="inline-flex items-center text-xs text-slate-700">
              <Building2 className="h-3.5 w-3.5 mr-1 text-slate-400" />
              {branchName}
            </span>
          )
        },
      },
      {
        accessorKey: 'active',
        header: 'Status',
        cell: ({ row }) => {
          const active = row.original.active
          return (
            <div className="flex items-center gap-1.5">
              <span
                className={`h-1.5 w-1.5 rounded-full ${
                  active ? 'bg-emerald-600' : 'bg-slate-300'
                }`}
              />
              <span
                className={`text-xs font-medium ${active ? 'text-emerald-700' : 'text-slate-500'}`}
              >
                {active ? 'Active' : 'Disabled'}
              </span>
            </div>
          )
        },
      },
      {
        id: 'actions',
        header: () => <span className="sr-only">Actions</span>,
        cell: ({ row }) => {
          const user = row.original
          return (
            <div className="flex items-center justify-end gap-1">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => handleEdit(user)}
                title="Edit user"
                className="h-7 w-7 p-0 text-slate-500 hover:text-slate-900 hover:bg-slate-100 cursor-pointer"
              >
                <Edit2 className="h-3.5 w-3.5" />
                <span className="sr-only">Edit {user.username}</span>
              </Button>
              {user.active && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => handleDisable(user)}
                  className="h-7 w-7 p-0 text-slate-400 hover:text-red-600 hover:bg-red-50 cursor-pointer"
                  title="Disable user"
                >
                  <UserX className="h-3.5 w-3.5" />
                  <span className="sr-only">Disable {user.username}</span>
                </Button>
              )}
            </div>
          )
        },
      },
    ],
    [branchesMap, handleDisable],
  )

  const filteredUsersData = React.useMemo(() => {
    const rawUsersData = usersQuery.data?.content ?? []
    if (!searchTerm.trim()) return rawUsersData
    const term = searchTerm.toLowerCase()
    return rawUsersData.filter(
      (u) =>
        u.username.toLowerCase().includes(term) ||
        u.fullName.toLowerCase().includes(term) ||
        u.email.toLowerCase().includes(term),
    )
  }, [usersQuery.data?.content, searchTerm])

  const totalElements = usersQuery.data?.totalElements ?? 0
  const currentPage = filters.page ?? 0
  const pageSize = filters.size ?? 10
  const totalPages = Math.ceil(totalElements / pageSize)

  const table = useReactTable<UserResponse>({
    data: filteredUsersData,
    columns,
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
    pageCount: totalPages,
  })

  if (currentActorRole === 'OPERATOR') {
    return (
      <div className="p-8 text-center bg-white rounded-lg border border-slate-200 shadow-2xs space-y-3">
        <div className="h-10 w-10 rounded-full bg-orange-50 text-orange-700 flex items-center justify-center mx-auto border border-orange-200">
          <Shield className="h-5 w-5" />
        </div>
        <h3 className="text-sm font-bold text-slate-900">Access Restricted</h3>
        <p className="text-xs text-slate-500 max-w-sm mx-auto">
          User account management is restricted to Branch Managers and
          Administrators. Operators do not have permission to view or manage
          user accounts.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {/* Header and Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
            <Users className="h-5 w-5 text-orange-600" />
            User Accounts
          </h2>
          <p className="text-sm text-slate-500">
            Manage user credentials, enterprise roles, and branch assignments.
          </p>
        </div>

        <Button
          onClick={handleCreate}
          className="self-start sm:self-auto bg-orange-600 hover:bg-orange-700 text-white shadow-2xs cursor-pointer"
        >
          <Plus className="h-4 w-4 mr-1.5" />
          Create User
        </Button>
      </div>

      {/* Filters Bar */}
      <div className="flex flex-wrap items-center gap-3 p-3.5 bg-white rounded-xl border border-slate-200/90 shadow-xs text-sm">
        {/* Search input */}
        <div className="relative min-w-[200px] flex-1 sm:flex-initial sm:w-64">
          <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-400" />
          <Input
            placeholder="Search users..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="pl-8 h-8 text-xs bg-slate-50/50 border-slate-200 focus-visible:bg-white"
          />
        </div>

        {!isBranchManager && (
          <div className="w-40">
            <Select
              value={filters.role ?? 'ALL'}
              onValueChange={(val) =>
                setFilters((prev) => ({
                  ...prev,
                  page: 0,
                  role: val === 'ALL' ? undefined : (val as Role),
                }))
              }
            >
              <SelectTrigger className="h-8 text-xs bg-slate-50/50 border-slate-200">
                <SelectValue placeholder="All Roles" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Roles</SelectItem>
                <SelectItem value="ADMIN">ADMIN</SelectItem>
                <SelectItem value="BRANCH_MANAGER">BRANCH_MANAGER</SelectItem>
                <SelectItem value="OPERATOR">OPERATOR</SelectItem>
              </SelectContent>
            </Select>
          </div>
        )}

        <div className="w-36">
          <Select
            value={
              filters.active === undefined
                ? 'ALL'
                : filters.active
                  ? 'ACTIVE'
                  : 'DISABLED'
            }
            onValueChange={(val) =>
              setFilters((prev) => ({
                ...prev,
                page: 0,
                active: val === 'ALL' ? undefined : val === 'ACTIVE',
              }))
            }
          >
            <SelectTrigger className="h-8 text-xs bg-slate-50/50 border-slate-200">
              <SelectValue placeholder="All Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Status</SelectItem>
              <SelectItem value="ACTIVE">Active Only</SelectItem>
              <SelectItem value="DISABLED">Disabled Only</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="ml-auto text-xs text-slate-500 font-medium">
          Showing {filteredUsersData.length} of {totalElements} users
        </div>
      </div>

      {/* Query Error Alert */}
      {usersQuery.isError && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Error Loading Users</AlertTitle>
          <AlertDescription>
            {usersQuery.error?.message ?? 'Failed to fetch user list.'}
          </AlertDescription>
        </Alert>
      )}

      {/* Data Table */}
      <div className="rounded-xl border border-slate-200 bg-white overflow-hidden shadow-xs">
        <Table>
          <TableHeader className="bg-slate-50/80">
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id} className="hover:bg-transparent">
                {headerGroup.headers.map((header) => (
                  <TableHead
                    key={header.id}
                    className="text-xs font-semibold text-slate-600 uppercase tracking-wider py-3"
                  >
                    {header.isPlaceholder
                      ? null
                      : flexRender(
                          header.column.columnDef.header,
                          header.getContext(),
                        )}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {usersQuery.isLoading ? (
              Array.from({ length: 5 }).map((_, idx) => (
                <TableRow key={idx}>
                  <TableCell>
                    <Skeleton className="h-4 w-24" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-32" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-40" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-5 w-16" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-28" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-5 w-14" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-8 w-16 ml-auto" />
                  </TableCell>
                </TableRow>
              ))
            ) : table.getRowModel().rows.length > 0 ? (
              table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  className="hover:bg-slate-50/60 transition-colors"
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id} className="py-3">
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext(),
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell
                  colSpan={columns.length}
                  className="h-40 text-center text-slate-500"
                >
                  <div className="flex flex-col items-center justify-center space-y-2 py-4">
                    <div className="h-12 w-12 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 mb-1">
                      <Users className="h-6 w-6" />
                    </div>
                    <p className="font-semibold text-slate-700">
                      No users found
                    </p>
                    <p className="text-xs text-slate-500 max-w-sm">
                      {searchTerm
                        ? `No users match "${searchTerm}". Try resetting your search or filter criteria.`
                        : 'No user accounts available in this view.'}
                    </p>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={handleCreate}
                      className="mt-2 text-xs"
                    >
                      <Plus className="h-3.5 w-3.5 mr-1" />
                      Create New User
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination Controls */}
      <div className="flex items-center justify-between px-2 text-sm text-slate-600">
        <div className="text-xs font-medium">
          Page {currentPage + 1} of {Math.max(1, totalPages)}
        </div>
        <div className="flex items-center space-x-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() =>
              setFilters((prev) => ({
                ...prev,
                page: Math.max(0, (prev.page ?? 0) - 1),
              }))
            }
            disabled={currentPage === 0 || usersQuery.isLoading}
            className="cursor-pointer text-xs"
          >
            <ChevronLeft className="h-4 w-4 mr-1" />
            Previous
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() =>
              setFilters((prev) => ({
                ...prev,
                page: (prev.page ?? 0) + 1,
              }))
            }
            disabled={currentPage >= totalPages - 1 || usersQuery.isLoading}
            className="cursor-pointer text-xs"
          >
            Next
            <ChevronRight className="h-4 w-4 ml-1" />
          </Button>
        </div>
      </div>

      {/* Modal Dialog */}
      <UserFormDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        userToEdit={editingUser}
        currentActorRole={currentActorRole}
        currentActorBranchId={currentActorBranchId}
      />
    </div>
  )
}
