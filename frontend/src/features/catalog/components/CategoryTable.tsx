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
import {
  useCategories,
  useDisableCategory,
  useEnableCategory,
} from '../hooks/use-categories.ts'
import type {
  CategoryQueryParams,
  CategoryResponse,
} from '../types/category.types.ts'
import { CategoryFormDialog } from './CategoryFormDialog.tsx'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  AlertCircle,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Edit2,
  FolderTree,
  Package,
  Plus,
  Power,
  PowerOff,
  Search,
} from 'lucide-react'

interface CategoryTableProps {
  currentActorRole?: 'ADMIN' | 'BRANCH_MANAGER' | 'OPERATOR'
}

export function CategoryTable({
  currentActorRole = 'OPERATOR',
}: CategoryTableProps) {
  const { t } = useTranslation()
  const isAdmin = currentActorRole === 'ADMIN'

  const [filters, setFilters] = React.useState<CategoryQueryParams>({
    page: 0,
    size: 10,
    active: 'true',
  })

  const [searchTerm, setSearchTerm] = React.useState('')
  const [dialogOpen, setDialogOpen] = React.useState(false)
  const [editingCategory, setEditingCategory] = React.useState<
    CategoryResponse | null | undefined
  >(null)
  const [blockedCategoryWarning, setBlockedCategoryWarning] =
    React.useState<CategoryResponse | null>(null)
  const [categoryToDisable, setCategoryToDisable] =
    React.useState<CategoryResponse | null>(null)
  const [actionError, setActionError] = React.useState<string | null>(null)

  const categoriesQuery = useCategories(filters)
  const disableMutation = useDisableCategory()
  const enableMutation = useEnableCategory()

  const handleEdit = (category: CategoryResponse) => {
    setActionError(null)
    setEditingCategory(category)
    setDialogOpen(true)
  }

  const handleCreate = () => {
    setActionError(null)
    setEditingCategory(null)
    setDialogOpen(true)
  }

  const handleDisable = React.useCallback((category: CategoryResponse) => {
    setActionError(null)
    if (category.activeProductCount > 0) {
      setBlockedCategoryWarning(category)
      return
    }
    setCategoryToDisable(category)
  }, [])

  const handleEnable = React.useCallback(
    (category: CategoryResponse) => {
      setActionError(null)
      enableMutation.mutate(category.externalId, {
        onError: (error: any) => {
          setActionError(error?.message || 'Failed to re-enable category.')
        },
      })
    },
    [enableMutation],
  )

  const columns = React.useMemo<ColumnDef<CategoryResponse>[]>(
    () => [
      {
        accessorKey: 'name',
        header: t('catalog.categoryName'),
        cell: ({ row }) => (
          <div className="flex items-center gap-2">
            <div className="h-7 w-7 rounded bg-emerald-50 text-emerald-700 flex items-center justify-center border border-emerald-200">
              <FolderTree className="h-3.5 w-3.5" />
            </div>
            <div>
              <div className="font-semibold text-slate-900 text-xs">
                {row.original.name}
              </div>
              {row.original.description && (
                <div className="text-[11px] text-slate-500 max-w-xs truncate">
                  {row.original.description}
                </div>
              )}
            </div>
          </div>
        ),
      },
      {
        accessorKey: 'active',
        header: t('common.status'),
        cell: ({ row }) => {
          const isActive = row.original.active
          return (
            <Badge
              variant={isActive ? 'default' : 'secondary'}
              className={`text-[10px] font-semibold py-0.5 px-2 ${
                isActive
                  ? 'bg-emerald-100 text-emerald-800 hover:bg-emerald-200 border-emerald-300'
                  : 'bg-slate-100 text-slate-600 border-slate-300'
              }`}
            >
              {isActive ? t('common.active') : t('common.disabled')}
            </Badge>
          )
        },
      },
      {
        accessorKey: 'activeProductCount',
        header: t('catalog.totalProducts'),
        cell: ({ row }) => (
          <div className="flex items-center gap-1.5 text-xs text-slate-700 font-medium">
            <Package className="h-3.5 w-3.5 text-slate-400" />
            <span>{row.original.activeProductCount}</span>
          </div>
        ),
      },
      {
        id: 'actions',
        header: t('common.actions'),
        cell: ({ row }) => {
          const category = row.original
          if (!isAdmin) {
            return (
              <span className="text-[11px] text-slate-400 italic">
                {t('common.readOnly')}
              </span>
            )
          }

          return (
            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                size="sm"
                className="h-7 px-2 text-xs text-slate-700 hover:text-slate-900 border-slate-300 cursor-pointer"
                onClick={() => handleEdit(category)}
              >
                <Edit2 className="h-3 w-3 mr-1" />
                {t('common.edit')}
              </Button>

              {category.active ? (
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 px-2 text-xs text-amber-700 hover:text-amber-800 hover:bg-amber-50 border-amber-200 cursor-pointer"
                  onClick={() => handleDisable(category)}
                  disabled={disableMutation.isPending}
                >
                  <PowerOff className="h-3 w-3 mr-1" />
                  {t('common.disable')}
                </Button>
              ) : (
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 px-2 text-xs text-emerald-700 hover:text-emerald-800 hover:bg-emerald-50 border-emerald-200 cursor-pointer"
                  onClick={() => handleEnable(category)}
                  disabled={enableMutation.isPending}
                >
                  <Power className="h-3 w-3 mr-1" />
                  {t('common.enable')}
                </Button>
              )}
            </div>
          )
        },
      },
    ],
    [
      isAdmin,
      handleDisable,
      handleEnable,
      disableMutation.isPending,
      enableMutation.isPending,
    ],
  )

  const tableData = React.useMemo(
    () => categoriesQuery.data?.content ?? [],
    [categoriesQuery.data?.content],
  )

  const table = useReactTable({
    data: tableData,
    columns,
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
    pageCount: categoriesQuery.data
      ? Math.ceil(categoriesQuery.data.totalElements / (filters.size ?? 10))
      : -1,
  })

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setFilters((prev) => ({
      ...prev,
      name: searchTerm.trim() || undefined,
      page: 0,
    }))
  }

  const handleActiveFilterChange = (value: string) => {
    setFilters((prev) => ({
      ...prev,
      active: value as 'true' | 'false' | 'all',
      page: 0,
    }))
  }

  const totalPages = categoriesQuery.data
    ? Math.ceil(categoriesQuery.data.totalElements / (filters.size ?? 10))
    : 1
  const currentPage = (filters.page ?? 0) + 1

  return (
    <div className="space-y-4">
      {/* Action error banner */}
      {actionError && (
        <Alert variant="destructive" className="py-2.5">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription className="text-xs flex items-center justify-between">
            <span>{actionError}</span>
            <Button
              variant="ghost"
              size="sm"
              className="h-6 px-2 text-xs"
              onClick={() => setActionError(null)}
            >
              Dismiss
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {/* Header and Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white p-3.5 rounded-lg border border-slate-200 shadow-2xs">
        <div className="flex flex-1 flex-wrap items-center gap-2.5">
          <form
            onSubmit={handleSearchSubmit}
            className="relative min-w-[220px] max-w-sm flex-1"
          >
            <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-400" />
            <Input
              placeholder="Search category by name..."
              className="pl-8 text-xs h-9 bg-slate-50 border-slate-200"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </form>

          <Select
            value={filters.active ?? 'true'}
            onValueChange={handleActiveFilterChange}
          >
            <SelectTrigger className="w-[140px] text-xs h-9 bg-slate-50 border-slate-200">
              <SelectValue placeholder={t('common.status')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="true" className="text-xs">
                {t('common.activeOnly')}
              </SelectItem>
              <SelectItem value="false" className="text-xs">
                {t('common.disabledOnly')}
              </SelectItem>
              <SelectItem value="all" className="text-xs">
                {t('common.allStatus')}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        {isAdmin && (
          <Button
            size="sm"
            className="text-xs bg-emerald-700 hover:bg-emerald-800 text-white cursor-pointer h-9 px-3.5"
            onClick={handleCreate}
          >
            <Plus className="h-3.5 w-3.5 mr-1.5" />
            {t('catalog.newCategory')}
          </Button>
        )}
      </div>

      {/* Table Container */}
      <div className="bg-white rounded-lg border border-slate-200 overflow-hidden shadow-2xs">
        {categoriesQuery.isLoading ? (
          <div className="p-4 space-y-3">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        ) : categoriesQuery.isError ? (
          <div className="p-6">
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle className="text-xs font-bold">
                {t('common.error')}
              </AlertTitle>
              <AlertDescription className="text-xs">
                {categoriesQuery.error.message || t('common.error')}
              </AlertDescription>
            </Alert>
          </div>
        ) : tableData.length === 0 ? (
          <div className="p-12 text-center space-y-3">
            <div className="h-10 w-10 rounded-full bg-slate-100 text-slate-400 flex items-center justify-center mx-auto">
              <FolderTree className="h-5 w-5" />
            </div>
            <div className="space-y-1">
              <p className="text-xs font-semibold text-slate-800">
                {t('common.noData')}
              </p>
            </div>
            {isAdmin && !filters.name && (
              <Button
                variant="outline"
                size="sm"
                className="text-xs mt-2"
                onClick={handleCreate}
              >
                <Plus className="h-3.5 w-3.5 mr-1" />
                {t('catalog.createCategory')}
              </Button>
            )}
          </div>
        ) : (
          <Table>
            <TableHeader className="bg-slate-50/75">
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id} className="hover:bg-transparent">
                  {headerGroup.headers.map((header) => (
                    <TableHead
                      key={header.id}
                      className="text-[11px] font-bold text-slate-600 uppercase tracking-wider py-3"
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
              {table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  className="hover:bg-slate-50/50 transition-colors border-slate-100"
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id} className="py-2.5">
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext(),
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}

        {/* Pagination Bar */}
        {categoriesQuery.data && categoriesQuery.data.totalElements > 0 && (
          <div className="flex items-center justify-between px-4 py-3 border-t border-slate-100 bg-slate-50/50 text-xs text-slate-600">
            <div>
              {t('common.showing')}{' '}
              <span className="font-semibold text-slate-900">
                {(filters.page ?? 0) * (filters.size ?? 10) + 1}
              </span>{' '}
              {t('common.to')}{' '}
              <span className="font-semibold text-slate-900">
                {Math.min(
                  ((filters.page ?? 0) + 1) * (filters.size ?? 10),
                  categoriesQuery.data.totalElements,
                )}
              </span>{' '}
              {t('common.of')}{' '}
              <span className="font-semibold text-slate-900">
                {categoriesQuery.data.totalElements}
              </span>{' '}
              {t('common.results')}
            </div>

            <div className="flex items-center space-x-2">
              <Button
                variant="outline"
                size="sm"
                className="h-7 w-7 p-0 cursor-pointer"
                disabled={(filters.page ?? 0) === 0}
                onClick={() =>
                  setFilters((prev) => ({
                    ...prev,
                    page: Math.max(0, (prev.page ?? 0) - 1),
                  }))
                }
              >
                <ChevronLeft className="h-3.5 w-3.5" />
              </Button>
              <span className="text-[11px] font-medium text-slate-700">
                {t('common.pageOf', {
                  page: String(currentPage),
                  totalPages: String(totalPages),
                })}
              </span>
              <Button
                variant="outline"
                size="sm"
                className="h-7 w-7 p-0 cursor-pointer"
                disabled={currentPage >= totalPages}
                onClick={() =>
                  setFilters((prev) => ({
                    ...prev,
                    page: (prev.page ?? 0) + 1,
                  }))
                }
              >
                <ChevronRight className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>
        )}
      </div>

      <CategoryFormDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        categoryToEdit={editingCategory}
      />

      {/* R-04 Rule: Cannot Disable Category with Active Products Modal */}
      <Dialog
        open={!!blockedCategoryWarning}
        onOpenChange={(open) => {
          if (!open) setBlockedCategoryWarning(null)
        }}
      >
        <DialogContent className="max-w-md">
          <DialogHeader>
            <div className="flex items-center gap-3">
              <div className="h-10 w-10 rounded-full bg-amber-100 border border-amber-200 text-amber-700 flex items-center justify-center shrink-0">
                <AlertTriangle className="h-5 w-5 text-amber-600" />
              </div>
              <div>
                <DialogTitle className="text-base font-bold text-slate-900">
                  Cannot Disable Category
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-500 mt-0.5">
                  Active product dependencies detected (Rule R-04)
                </DialogDescription>
              </div>
            </div>
          </DialogHeader>

          <div className="py-2 space-y-3 text-xs text-slate-600">
            <div className="rounded-lg bg-amber-50/70 border border-amber-200/80 p-3.5 space-y-2">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-900">
                  {blockedCategoryWarning?.name}
                </span>
                <Badge
                  variant="outline"
                  className="bg-amber-100/80 text-amber-800 border-amber-300 text-[11px] font-semibold flex items-center gap-1"
                >
                  <Package className="h-3 w-3" />
                  {blockedCategoryWarning?.activeProductCount} active product
                  {blockedCategoryWarning?.activeProductCount === 1 ? '' : 's'}
                </Badge>
              </div>
              <p className="text-[11.5px] leading-relaxed text-amber-900/90">
                According to business rule <strong>R-04</strong>, categories
                with active items cannot be disabled to prevent orphaned catalog
                records.
              </p>
            </div>

            <p className="text-slate-600 text-[11.5px] leading-relaxed">
              To disable this category, please reassign or disable its
              associated products in the <strong>Products</strong> tab first.
            </p>
          </div>

          <DialogFooter className="pt-2 border-t border-slate-100">
            <Button
              type="button"
              size="sm"
              className="w-full sm:w-auto bg-slate-900 hover:bg-slate-800 text-white cursor-pointer"
              onClick={() => setBlockedCategoryWarning(null)}
            >
              Understood
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Confirm Disable Category Modal */}
      <Dialog
        open={!!categoryToDisable}
        onOpenChange={(open) => {
          if (!open) setCategoryToDisable(null)
        }}
      >
        <DialogContent className="max-w-md">
          <DialogHeader>
            <div className="flex items-center gap-3">
              <div className="h-10 w-10 rounded-full bg-slate-100 border border-slate-200 text-slate-700 flex items-center justify-center shrink-0">
                <PowerOff className="h-5 w-5 text-slate-600" />
              </div>
              <div>
                <DialogTitle className="text-base font-bold text-slate-900">
                  Disable Category
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-500 mt-0.5">
                  Confirm deactivation in catalog master data
                </DialogDescription>
              </div>
            </div>
          </DialogHeader>

          <div className="py-2 text-xs text-slate-600 space-y-2">
            <p>
              Are you sure you want to disable category{' '}
              <span className="font-semibold text-slate-900">
                "{categoryToDisable?.name}"
              </span>
              ?
            </p>
            <p className="text-[11.5px] text-slate-500">
              Disabled categories cannot be selected when creating or updating
              products until they are re-enabled.
            </p>
          </div>

          <DialogFooter className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setCategoryToDisable(null)}
              disabled={disableMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              type="button"
              variant="destructive"
              size="sm"
              disabled={disableMutation.isPending}
              onClick={() => {
                if (!categoryToDisable) return
                disableMutation.mutate(categoryToDisable.externalId, {
                  onSuccess: () => {
                    setCategoryToDisable(null)
                  },
                  onError: (error: any) => {
                    setActionError(
                      error?.message ||
                        'Failed to disable category. Active products may be associated with it.',
                    )
                    setCategoryToDisable(null)
                  },
                })
              }}
            >
              {disableMutation.isPending ? 'Disabling...' : 'Disable Category'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
