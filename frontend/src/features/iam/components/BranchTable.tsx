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
import { useBranches, useDisableBranch } from '../hooks/use-branches.ts'
import type {
  BranchQueryParams,
  BranchResponse,
} from '../types/branch.types.ts'
import { BranchFormDialog } from './BranchFormDialog.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  AlertCircle,
  Building2,
  ChevronLeft,
  ChevronRight,
  Edit2,
  MapPin,
  Phone,
  Plus,
  PowerOff,
  Search,
} from 'lucide-react'

export function BranchTable() {
  const { t } = useTranslation()
  const [filters, setFilters] = React.useState<BranchQueryParams>({
    page: 0,
    size: 10,
  })

  const [searchTerm, setSearchTerm] = React.useState('')
  const [dialogOpen, setDialogOpen] = React.useState(false)
  const [editingBranch, setEditingBranch] = React.useState<
    BranchResponse | null | undefined
  >(null)

  const branchesQuery = useBranches(filters)
  const disableMutation = useDisableBranch()

  const handleEdit = (branch: BranchResponse) => {
    setEditingBranch(branch)
    setDialogOpen(true)
  }

  const handleCreate = () => {
    setEditingBranch(null)
    setDialogOpen(true)
  }

  const handleDisable = React.useCallback(
    (branch: BranchResponse) => {
      if (
        window.confirm(
          t('iam.confirmDisableBranch'),
        )
      ) {
        disableMutation.mutate(branch.externalId)
      }
    },
    [disableMutation, t],
  )

  const columns = React.useMemo<ColumnDef<BranchResponse>[]>(
    () => [
      {
        accessorKey: 'code',
        header: t('iam.branchCode'),
        cell: ({ row }) => (
          <span className="font-mono font-bold text-xs bg-slate-100 text-slate-800 px-2 py-1 rounded border border-slate-200 shadow-2xs">
            {row.original.code}
          </span>
        ),
      },
      {
        accessorKey: 'name',
        header: t('iam.branchName'),
        cell: ({ row }) => (
          <div className="flex items-center gap-2">
            <div className="h-7 w-7 rounded bg-slate-100 text-slate-700 flex items-center justify-center shrink-0 border border-slate-200">
              <Building2 className="h-3.5 w-3.5" />
            </div>
            <span className="font-semibold text-slate-900 text-sm">
              {row.original.name}
            </span>
          </div>
        ),
      },
      {
        accessorKey: 'address',
        header: t('iam.address'),
        cell: ({ row }) => (
          <div className="flex items-center gap-1.5 text-slate-600 text-sm">
            <MapPin className="h-3.5 w-3.5 text-slate-400 shrink-0" />
            <span>{row.original.address}</span>
          </div>
        ),
      },
      {
        accessorKey: 'city',
        header: t('iam.city'),
        cell: ({ row }) => (
          <Badge
            variant="outline"
            className="bg-white text-slate-700 font-medium text-xs"
          >
            {row.original.city}
          </Badge>
        ),
      },
      {
        accessorKey: 'phone',
        header: t('iam.phone'),
        cell: ({ row }) => {
          const phone = row.original.phone
          if (!phone) return <span className="text-slate-400">—</span>
          return (
            <div className="flex items-center gap-1 text-slate-600 text-xs font-mono">
              <Phone className="h-3 w-3 text-slate-400" />
              <span>{phone}</span>
            </div>
          )
        },
      },
      {
        accessorKey: 'active',
        header: t('common.status'),
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
                {active ? t('common.active') : t('common.disabled')}
              </span>
            </div>
          )
        },
      },
      {
        id: 'actions',
        header: () => <span className="sr-only">{t('common.actions')}</span>,
        cell: ({ row }) => {
          const branch = row.original
          return (
            <div className="flex items-center justify-end gap-1">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => handleEdit(branch)}
                title={t('common.edit')}
                className="h-7 w-7 p-0 text-slate-500 hover:text-slate-900 hover:bg-slate-100 cursor-pointer"
              >
                <Edit2 className="h-3.5 w-3.5" />
                <span className="sr-only">Edit {branch.name}</span>
              </Button>
              {branch.active && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => handleDisable(branch)}
                  className="h-7 w-7 p-0 text-slate-400 hover:text-red-600 hover:bg-red-50 cursor-pointer"
                  title={t('common.disabled')}
                >
                  <PowerOff className="h-3.5 w-3.5" />
                  <span className="sr-only">Disable {branch.name}</span>
                </Button>
              )}
            </div>
          )
        },
      },
    ],
    [handleDisable, t],
  )

  const filteredBranchesData = React.useMemo(() => {
    const rawBranchesData = branchesQuery.data?.content ?? []
    if (!searchTerm.trim()) return rawBranchesData
    const term = searchTerm.toLowerCase()
    return rawBranchesData.filter(
      (b) =>
        b.name.toLowerCase().includes(term) ||
        b.code.toLowerCase().includes(term) ||
        b.city.toLowerCase().includes(term) ||
        b.address.toLowerCase().includes(term),
    )
  }, [branchesQuery.data?.content, searchTerm])

  const totalElements = branchesQuery.data?.totalElements ?? 0
  const currentPage = filters.page ?? 0
  const pageSize = filters.size ?? 10
  const totalPages = Math.ceil(totalElements / pageSize)

  const table = useReactTable<BranchResponse>({
    data: filteredBranchesData,
    columns,
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
    pageCount: totalPages,
  })

  return (
    <div className="space-y-4">
      {/* Header and Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
            <Building2 className="h-5 w-5 text-orange-600" />
            {t('iam.branchesTab')}
          </h2>
          <p className="text-sm text-slate-500">
            {t('iam.subtitle')}
          </p>
        </div>

        <Button
          onClick={handleCreate}
          className="self-start sm:self-auto bg-orange-600 hover:bg-orange-700 text-white shadow-2xs cursor-pointer"
        >
          <Plus className="h-4 w-4 mr-1.5" />
          {t('iam.createBranch')}
        </Button>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-wrap items-center gap-3 p-3.5 bg-white rounded-xl border border-slate-200/90 shadow-xs text-sm">
        {/* Search input */}
        <div className="relative min-w-[200px] flex-1 sm:flex-initial sm:w-64">
          <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-400" />
          <Input
            placeholder={t('common.search')}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="pl-8 h-8 text-xs bg-slate-50/50 border-slate-200 focus-visible:bg-white"
          />
        </div>

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
              <SelectValue placeholder={t('common.all')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">{t('common.all')}</SelectItem>
              <SelectItem value="ACTIVE">{t('common.active')}</SelectItem>
              <SelectItem value="DISABLED">{t('common.disabled')}</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="ml-auto text-xs text-slate-500 font-medium">
          {t('common.showing')} {filteredBranchesData.length} {t('common.of')} {totalElements} {t('common.results')}
        </div>
      </div>

      {/* Query Error Alert */}
      {branchesQuery.isError && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>{t('common.error')}</AlertTitle>
          <AlertDescription>
            {branchesQuery.error?.message ?? t('common.error')}
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
            {branchesQuery.isLoading ? (
              Array.from({ length: 4 }).map((_, idx) => (
                <TableRow key={idx}>
                  <TableCell>
                    <Skeleton className="h-4 w-16" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-36" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-40" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-20" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-24" />
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
                      <Building2 className="h-6 w-6" />
                    </div>
                    <p className="font-semibold text-slate-700">
                      {t('common.noData')}
                    </p>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={handleCreate}
                      className="mt-2 text-xs"
                    >
                      <Plus className="h-3.5 w-3.5 mr-1" />
                      {t('iam.createBranch')}
                    </Button>
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
          {t('common.pageOf', {
            page: String(currentPage + 1),
            totalPages: String(Math.max(1, totalPages)),
          })}
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
            disabled={currentPage === 0 || branchesQuery.isLoading}
            className="cursor-pointer text-xs"
          >
            <ChevronLeft className="h-4 w-4 mr-1" />
            {t('common.previous')}
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
            disabled={currentPage >= totalPages - 1 || branchesQuery.isLoading}
            className="cursor-pointer text-xs"
          >
            {t('common.next')}
            <ChevronRight className="h-4 w-4 ml-1" />
          </Button>
        </div>
      </div>

      {/* Modal Dialog */}
      <BranchFormDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        branchToEdit={editingBranch}
      />
    </div>
  )
}
