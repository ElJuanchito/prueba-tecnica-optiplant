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
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog.tsx'
import { Input } from '@/components/ui/input.tsx'
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
import { useUsers } from '../hooks/use-users.ts'
import { useAuditLogs } from '../hooks/use-audit.ts'
import type {
  AuditEntryResponse,
  AuditQueryParams,
} from '../types/audit.types.ts'
import {
  Activity,
  AlertCircle,
  Building2,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Clock,
  Code2,
  Globe,
  RotateCcw,
  Search,
  Shield,
  User,
} from 'lucide-react'

export function AuditTable() {
  const [filters, setFilters] = React.useState<AuditQueryParams>({
    page: 0,
    size: 15,
  })

  const [entityFilter, setEntityFilter] = React.useState('')
  const [actionFilter, setActionFilter] = React.useState('')
  const [selectedPayload, setSelectedPayload] = React.useState<{
    title: string
    before: string | null
    after: string | null
  } | null>(null)

  const auditQuery = useAuditLogs(filters)
  const usersQuery = useUsers({ page: 0, size: 100 })
  const branchesQuery = useBranches({ page: 0, size: 100 })

  const usersMap = React.useMemo(() => {
    const map = new Map<string, string>()
    usersQuery.data?.content?.forEach((u) => {
      map.set(u.externalId, u.fullName || u.username)
    })
    return map
  }, [usersQuery.data?.content])

  const branchesMap = React.useMemo(() => {
    const map = new Map<string, string>()
    branchesQuery.data?.content?.forEach((b) => {
      map.set(b.externalId, `${b.name} (${b.code})`)
    })
    return map
  }, [branchesQuery.data?.content])

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    setFilters((prev) => ({
      ...prev,
      page: 0,
      entityName: entityFilter.trim() ? entityFilter.trim() : undefined,
      action: actionFilter.trim() ? actionFilter.trim() : undefined,
    }))
  }

  const handleReset = () => {
    setEntityFilter('')
    setActionFilter('')
    setFilters({
      page: 0,
      size: 15,
    })
  }

  const columns = React.useMemo<ColumnDef<AuditEntryResponse>[]>(
    () => [
      {
        accessorKey: 'createdAt',
        header: 'Timestamp (UTC)',
        cell: ({ row }) => {
          const raw = row.original.createdAt
          try {
            const date = new Date(raw)
            return (
              <div className="flex items-center gap-1.5 text-xs text-slate-700 font-mono">
                <Clock className="h-3 w-3 text-slate-400 shrink-0" />
                <span>{date.toISOString().replace('T', ' ').slice(0, 19)}</span>
              </div>
            )
          } catch {
            return (
              <span className="text-xs font-mono text-slate-600">{raw}</span>
            )
          }
        },
      },
      {
        accessorKey: 'action',
        header: 'Action',
        cell: ({ row }) => {
          const action = row.original.action
          const isDanger =
            action.includes('DISABLE') || action.includes('DELETE')
          const isSuccess =
            action.includes('CREATE') || action.includes('REGISTER')
          const isAuth =
            action.includes('LOGIN') ||
            action.includes('AUTH') ||
            action.includes('REFRESH')

          if (isDanger) {
            return (
              <Badge
                variant="destructive"
                className="font-mono text-[11px] font-medium"
              >
                {action}
              </Badge>
            )
          }
          if (isSuccess) {
            return (
              <Badge className="bg-emerald-50 text-emerald-800 border-emerald-200 font-mono text-[11px] font-medium">
                {action}
              </Badge>
            )
          }
          if (isAuth) {
            return (
              <Badge className="bg-orange-50 text-orange-800 border-orange-200 font-mono text-[11px] font-medium">
                {action}
              </Badge>
            )
          }
          return (
            <Badge
              variant="secondary"
              className="font-mono text-[11px] font-medium bg-slate-100 text-slate-700"
            >
              {action}
            </Badge>
          )
        },
      },
      {
        accessorKey: 'entityName',
        header: 'Entity',
        cell: ({ row }) => (
          <span className="font-semibold text-slate-900 text-xs px-2 py-0.5 rounded bg-slate-100 border border-slate-200">
            {row.original.entityName}
          </span>
        ),
      },
      {
        accessorKey: 'actorUserId',
        header: 'Actor',
        cell: ({ row }) => {
          const actorId = row.original.actorUserId
          if (!actorId) {
            return (
              <span className="inline-flex items-center text-slate-400 text-xs italic gap-1">
                <Shield className="h-3 w-3 text-slate-300" />
                System
              </span>
            )
          }
          const actorName = usersMap.get(actorId) ?? 'Authorized Operator'
          return (
            <span
              className="inline-flex items-center text-xs text-slate-700 font-medium gap-1"
              title={actorId}
            >
              <User className="h-3 w-3 text-slate-400" />
              {actorName}
            </span>
          )
        },
      },
      {
        accessorKey: 'branchId',
        header: 'Branch',
        cell: ({ row }) => {
          const bId = row.original.branchId
          if (!bId) {
            return <span className="text-slate-400 text-xs">Global</span>
          }
          const branchName = branchesMap.get(bId) ?? 'Assigned Branch'
          return (
            <span
              className="inline-flex items-center text-xs text-slate-700 font-medium gap-1"
              title={bId}
            >
              <Building2 className="h-3 w-3 text-slate-400" />
              {branchName}
            </span>
          )
        },
      },
      {
        accessorKey: 'ipAddress',
        header: 'IP Address',
        cell: ({ row }) => (
          <div className="flex items-center gap-1 text-xs font-mono text-slate-600">
            <Globe className="h-3 w-3 text-slate-400" />
            <span>{row.original.ipAddress ?? '—'}</span>
          </div>
        ),
      },
      {
        id: 'payloads',
        header: 'Payload Details',
        cell: ({ row }) => {
          const before = row.original.payloadBefore
          const after = row.original.payloadAfter

          if (!before && !after) {
            return <span className="text-slate-400 text-xs">—</span>
          }

          return (
            <div
              className="text-xs space-y-0.5 max-w-xs cursor-pointer hover:opacity-85 transition-opacity"
              onClick={() =>
                setSelectedPayload({
                  title: `${row.original.action} on ${row.original.entityName}`,
                  before,
                  after,
                })
              }
              title="Click to view full payload details"
            >
              {before && (
                <div
                  className="text-red-700 truncate bg-red-50/70 px-1.5 py-0.5 rounded border border-red-200/60 font-mono text-[11px]"
                  title={`Before: ${before}`}
                >
                  <span className="font-bold">B:</span> {before}
                </div>
              )}
              {after && (
                <div
                  className="text-emerald-800 truncate bg-emerald-50/70 px-1.5 py-0.5 rounded border border-emerald-200/60 font-mono text-[11px]"
                  title={`After: ${after}`}
                >
                  <span className="font-bold">A:</span> {after}
                </div>
              )}
            </div>
          )
        },
      },
    ],
    [usersMap, branchesMap],
  )

  const auditData = auditQuery.data?.content ?? []
  const totalElements = auditQuery.data?.totalElements ?? 0
  const currentPage = filters.page ?? 0
  const pageSize = filters.size ?? 15
  const totalPages = Math.ceil(totalElements / pageSize)

  const table = useReactTable<AuditEntryResponse>({
    data: auditData,
    columns,
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
    pageCount: totalPages,
  })

  return (
    <div className="space-y-4">
      {/* Header */}
      <div>
        <h2 className="text-xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
          <Activity className="h-5 w-5 text-orange-600" />
          Audit Trail
        </h2>
        <p className="text-sm text-slate-500">
          Immutable, transactional log of all mutations across the system.
        </p>
      </div>

      {/* Filter / Search Form */}
      <form
        onSubmit={handleSearch}
        className="flex flex-wrap items-center gap-3 p-3.5 bg-white rounded-xl border border-slate-200/90 shadow-xs text-sm"
      >
        <span className="text-slate-600 font-semibold text-xs uppercase tracking-wider">
          Filter:
        </span>

        <div className="w-48">
          <Input
            placeholder="Entity name (e.g. USER, BRANCH)"
            value={entityFilter}
            onChange={(e) => setEntityFilter(e.target.value)}
            className="h-8 text-xs bg-slate-50/50 border-slate-200 focus-visible:bg-white"
          />
        </div>

        <div className="w-48">
          <Input
            placeholder="Action (e.g. CREATE, DISABLE)"
            value={actionFilter}
            onChange={(e) => setActionFilter(e.target.value)}
            className="h-8 text-xs bg-slate-50/50 border-slate-200 focus-visible:bg-white"
          />
        </div>

        <Button
          type="submit"
          size="sm"
          variant="secondary"
          className="h-8 text-xs cursor-pointer font-medium"
        >
          <Search className="h-3.5 w-3.5 mr-1 text-slate-600" />
          Apply
        </Button>

        {(entityFilter || actionFilter) && (
          <Button
            type="button"
            size="sm"
            variant="ghost"
            onClick={handleReset}
            className="h-8 text-xs text-slate-500 hover:text-slate-900 cursor-pointer"
          >
            <RotateCcw className="h-3.5 w-3.5 mr-1" />
            Reset
          </Button>
        )}

        <div className="ml-auto text-xs text-slate-500 font-medium">
          Showing {auditData.length} of {totalElements} log entries
        </div>
      </form>

      {/* Query Error */}
      {auditQuery.isError && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Error Loading Audit Logs</AlertTitle>
          <AlertDescription>
            {auditQuery.error?.message ?? 'Failed to fetch audit log entries.'}
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
            {auditQuery.isLoading ? (
              Array.from({ length: 5 }).map((_, idx) => (
                <TableRow key={idx}>
                  <TableCell>
                    <Skeleton className="h-4 w-28" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-5 w-20" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-20" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-20" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-20" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-20" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-20" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-32" />
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
                      <ClipboardList className="h-6 w-6" />
                    </div>
                    <p className="font-semibold text-slate-700">
                      No audit entries found
                    </p>
                    <p className="text-xs text-slate-500 max-w-sm">
                      Audit events will record automatically when user and
                      branch mutations occur.
                    </p>
                  </div>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
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
            disabled={currentPage === 0 || auditQuery.isLoading}
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
            disabled={currentPage >= totalPages - 1 || auditQuery.isLoading}
            className="cursor-pointer text-xs"
          >
            Next
            <ChevronRight className="h-4 w-4 ml-1" />
          </Button>
        </div>
      </div>

      {/* Expandable Payload Inspector Dialog */}
      {selectedPayload && (
        <Dialog
          open={Boolean(selectedPayload)}
          onOpenChange={(open) => !open && setSelectedPayload(null)}
        >
          <DialogContent className="sm:max-w-[600px] p-6">
            <DialogHeader className="space-y-1">
              <div className="flex items-center gap-2">
                <div className="h-8 w-8 rounded bg-slate-100 text-slate-800 flex items-center justify-center font-bold text-sm border border-slate-200">
                  <Code2 className="h-4 w-4 text-orange-600" />
                </div>
                <DialogTitle className="text-lg font-bold text-slate-900">
                  {selectedPayload.title}
                </DialogTitle>
              </div>
            </DialogHeader>

            <div className="space-y-4 py-2">
              {selectedPayload.before && (
                <div className="space-y-1.5">
                  <span className="text-xs font-bold text-red-700 uppercase tracking-wider flex items-center gap-1">
                    State Before Mutation
                  </span>
                  <pre className="p-3 bg-red-50/60 rounded-lg border border-red-200 text-xs font-mono text-slate-800 overflow-x-auto whitespace-pre-wrap">
                    {(() => {
                      try {
                        return JSON.stringify(
                          JSON.parse(selectedPayload.before),
                          null,
                          2,
                        )
                      } catch {
                        return selectedPayload.before
                      }
                    })()}
                  </pre>
                </div>
              )}

              {selectedPayload.after && (
                <div className="space-y-1.5">
                  <span className="text-xs font-bold text-emerald-700 uppercase tracking-wider flex items-center gap-1">
                    State After Mutation
                  </span>
                  <pre className="p-3 bg-emerald-50/60 rounded-lg border border-emerald-200 text-xs font-mono text-slate-800 overflow-x-auto whitespace-pre-wrap">
                    {(() => {
                      try {
                        return JSON.stringify(
                          JSON.parse(selectedPayload.after),
                          null,
                          2,
                        )
                      } catch {
                        return selectedPayload.after
                      }
                    })()}
                  </pre>
                </div>
              )}
            </div>
          </DialogContent>
        </Dialog>
      )}
    </div>
  )
}
