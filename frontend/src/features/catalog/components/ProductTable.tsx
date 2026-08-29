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
import { useCategories } from '../hooks/use-categories.ts'
import {
  useDisableProduct,
  useEnableProduct,
  useProducts,
} from '../hooks/use-products.ts'
import type {
  ProductListItemResponse,
  ProductQueryParams,
} from '../types/product.types.ts'
import { ProductFormDialog } from './ProductFormDialog.tsx'
import { ProductUnitsDialog } from './ProductUnitsDialog.tsx'
import {
  AlertCircle,
  ArrowUpDown,
  Boxes,
  ChevronLeft,
  ChevronRight,
  Edit2,
  FolderTree,
  Plus,
  Power,
  PowerOff,
  Scale,
  Search,
} from 'lucide-react'

interface ProductTableProps {
  currentActorRole?: 'ADMIN' | 'BRANCH_MANAGER' | 'OPERATOR'
}

export function ProductTable({
  currentActorRole = 'OPERATOR',
}: ProductTableProps) {
  const isAdmin = currentActorRole === 'ADMIN'

  const [filters, setFilters] = React.useState<ProductQueryParams>({
    page: 0,
    size: 10,
    active: 'true',
    sort: 'sku',
    direction: 'asc',
  })

  const [searchTerm, setSearchTerm] = React.useState('')
  const [formDialogOpen, setFormDialogOpen] = React.useState(false)
  const [unitsDialogOpen, setUnitsDialogOpen] = React.useState(false)
  const [editingProduct, setEditingProduct] = React.useState<
    ProductListItemResponse | null | undefined
  >(null)
  const [selectedProductForUnits, setSelectedProductForUnits] =
    React.useState<ProductListItemResponse | null>(null)
  const [actionError, setActionError] = React.useState<string | null>(null)

  const productsQuery = useProducts(filters)
  const categoriesQuery = useCategories({ active: 'all', size: 100 })
  const disableMutation = useDisableProduct()
  const enableMutation = useEnableProduct()

  const handleEdit = (product: ProductListItemResponse) => {
    setActionError(null)
    setEditingProduct(product)
    setFormDialogOpen(true)
  }

  const handleCreate = () => {
    setActionError(null)
    setEditingProduct(null)
    setFormDialogOpen(true)
  }

  const handleManageUnits = (product: ProductListItemResponse) => {
    setActionError(null)
    setSelectedProductForUnits(product)
    setUnitsDialogOpen(true)
  }

  const handleDisable = React.useCallback(
    (product: ProductListItemResponse) => {
      setActionError(null)
      if (
        window.confirm(
          `Are you sure you want to disable product "${product.name}" (${product.sku})?`,
        )
      ) {
        disableMutation.mutate(product.externalId, {
          onError: (error: any) => {
            setActionError(error?.message || 'Failed to disable product.')
          },
        })
      }
    },
    [disableMutation],
  )

  const handleEnable = React.useCallback(
    (product: ProductListItemResponse) => {
      setActionError(null)
      enableMutation.mutate(product.externalId, {
        onError: (error: any) => {
          if (error?.code === 'category_inactive') {
            setActionError(
              'Cannot enable product: its category is inactive. Enable the category first.',
            )
          } else {
            setActionError(error?.message || 'Failed to re-enable product.')
          }
        },
      })
    },
    [enableMutation],
  )

  const columns = React.useMemo<ColumnDef<ProductListItemResponse>[]>(
    () => [
      {
        accessorKey: 'sku',
        header: 'SKU',
        cell: ({ row }) => (
          <span className="font-mono font-bold text-xs bg-slate-100 text-slate-800 px-2 py-0.5 rounded border border-slate-200 shadow-2xs">
            {row.original.sku}
          </span>
        ),
      },
      {
        accessorKey: 'name',
        header: 'Product Name',
        cell: ({ row }) => (
          <div>
            <div className="font-semibold text-slate-900 text-xs">
              {row.original.name}
            </div>
          </div>
        ),
      },
      {
        accessorKey: 'category',
        header: 'Category',
        cell: ({ row }) => {
          const category = row.original.category
          return category ? (
            <Badge
              variant="outline"
              className="text-[11px] font-medium bg-slate-50 text-slate-700 border-slate-200 flex items-center w-fit gap-1"
            >
              <FolderTree className="h-3 w-3 text-slate-400" />
              {category.name}
            </Badge>
          ) : (
            <span className="text-slate-400 text-xs">—</span>
          )
        },
      },
      {
        accessorKey: 'baseUnit',
        header: 'Base Unit',
        cell: ({ row }) => (
          <Badge
            variant="outline"
            className="font-mono text-[10px] font-bold bg-amber-50 text-amber-900 border-amber-200"
          >
            {row.original.baseUnit}
          </Badge>
        ),
      },
      {
        accessorKey: 'active',
        header: 'Status',
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
              {isActive ? 'Active' : 'Disabled'}
            </Badge>
          )
        },
      },
      {
        accessorKey: 'createdAt',
        header: 'Created',
        cell: ({ row }) => (
          <div className="text-[11px] text-slate-500">
            {row.original.createdAt
              ? new Date(row.original.createdAt).toLocaleDateString()
              : '—'}
          </div>
        ),
      },
      {
        id: 'actions',
        header: 'Actions',
        cell: ({ row }) => {
          const product = row.original

          return (
            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                size="sm"
                className="h-7 px-2 text-xs text-slate-700 hover:text-slate-900 border-slate-300 cursor-pointer"
                onClick={() => handleManageUnits(product)}
                title="View & manage units of measure"
              >
                <Scale className="h-3 w-3 mr-1 text-amber-600" />
                Units
              </Button>

              {isAdmin && (
                <>
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-7 px-2 text-xs text-slate-700 hover:text-slate-900 border-slate-300 cursor-pointer"
                    onClick={() => handleEdit(product)}
                  >
                    <Edit2 className="h-3 w-3 mr-1" />
                    Edit
                  </Button>

                  {product.active ? (
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-7 px-2 text-xs text-amber-700 hover:text-amber-800 hover:bg-amber-50 border-amber-200 cursor-pointer"
                      onClick={() => handleDisable(product)}
                      disabled={disableMutation.isPending}
                    >
                      <PowerOff className="h-3 w-3 mr-1" />
                      Disable
                    </Button>
                  ) : (
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-7 px-2 text-xs text-emerald-700 hover:text-emerald-800 hover:bg-emerald-50 border-emerald-200 cursor-pointer"
                      onClick={() => handleEnable(product)}
                      disabled={enableMutation.isPending}
                    >
                      <Power className="h-3 w-3 mr-1" />
                      Enable
                    </Button>
                  )}
                </>
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
    () => productsQuery.data?.content ?? [],
    [productsQuery.data?.content],
  )

  const table = useReactTable({
    data: tableData,
    columns,
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
    pageCount: productsQuery.data
      ? Math.ceil(productsQuery.data.totalElements / (filters.size ?? 10))
      : -1,
  })

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setFilters((prev) => ({
      ...prev,
      q: searchTerm.trim() || undefined,
      page: 0,
    }))
  }

  const handleCategoryFilterChange = (categoryId: string) => {
    setFilters((prev) => ({
      ...prev,
      categoryId: categoryId === 'ALL' ? undefined : categoryId,
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

  const handleSortChange = (value: string) => {
    setFilters((prev) => ({
      ...prev,
      sort: value as 'sku' | 'name' | 'createdAt',
      page: 0,
    }))
  }

  const toggleDirection = () => {
    setFilters((prev) => ({
      ...prev,
      direction: prev.direction === 'asc' ? 'desc' : 'asc',
      page: 0,
    }))
  }

  const totalPages = productsQuery.data
    ? Math.ceil(productsQuery.data.totalElements / (filters.size ?? 10))
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
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3 bg-white p-3.5 rounded-lg border border-slate-200 shadow-2xs">
        <div className="flex flex-1 flex-wrap items-center gap-2.5">
          <form
            onSubmit={handleSearchSubmit}
            className="relative min-w-[200px] max-w-xs flex-1"
          >
            <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-400" />
            <Input
              placeholder="Search by SKU or name..."
              className="pl-8 text-xs h-9 bg-slate-50 border-slate-200"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </form>

          {/* Category Filter */}
          <Select
            value={filters.categoryId ?? 'ALL'}
            onValueChange={handleCategoryFilterChange}
          >
            <SelectTrigger className="w-[160px] text-xs h-9 bg-slate-50 border-slate-200">
              <SelectValue placeholder="All Categories" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL" className="text-xs">
                All Categories
              </SelectItem>
              {categoriesQuery.data?.content?.map((cat) => (
                <SelectItem
                  key={cat.externalId}
                  value={cat.externalId}
                  className="text-xs"
                >
                  {cat.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {/* Status Filter */}
          <Select
            value={filters.active ?? 'true'}
            onValueChange={handleActiveFilterChange}
          >
            <SelectTrigger className="w-[130px] text-xs h-9 bg-slate-50 border-slate-200">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="true" className="text-xs">
                Active only
              </SelectItem>
              <SelectItem value="false" className="text-xs">
                Disabled only
              </SelectItem>
              <SelectItem value="all" className="text-xs">
                All status
              </SelectItem>
            </SelectContent>
          </Select>

          {/* Sort selection */}
          <Select
            value={filters.sort ?? 'sku'}
            onValueChange={handleSortChange}
          >
            <SelectTrigger className="w-[130px] text-xs h-9 bg-slate-50 border-slate-200">
              <SelectValue placeholder="Sort by" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="sku" className="text-xs">
                Sort by SKU
              </SelectItem>
              <SelectItem value="name" className="text-xs">
                Sort by Name
              </SelectItem>
              <SelectItem value="createdAt" className="text-xs">
                Sort by Date
              </SelectItem>
            </SelectContent>
          </Select>

          <Button
            variant="outline"
            size="sm"
            className="h-9 px-2.5 text-xs text-slate-700 border-slate-200 bg-slate-50 cursor-pointer"
            onClick={toggleDirection}
            title={`Direction: ${filters.direction === 'asc' ? 'Ascending' : 'Descending'}`}
          >
            <ArrowUpDown className="h-3.5 w-3.5 mr-1 text-slate-500" />
            {filters.direction === 'asc' ? 'ASC' : 'DESC'}
          </Button>
        </div>

        {isAdmin && (
          <Button
            size="sm"
            className="text-xs bg-indigo-700 hover:bg-indigo-800 text-white cursor-pointer h-9 px-3.5"
            onClick={handleCreate}
          >
            <Plus className="h-3.5 w-3.5 mr-1.5" />
            New Product
          </Button>
        )}
      </div>

      {/* Table Container */}
      <div className="bg-white rounded-lg border border-slate-200 overflow-hidden shadow-2xs">
        {productsQuery.isLoading ? (
          <div className="p-4 space-y-3">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        ) : productsQuery.isError ? (
          <div className="p-6">
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle className="text-xs font-bold">
                Error loading product catalog
              </AlertTitle>
              <AlertDescription className="text-xs">
                {productsQuery.error.message ||
                  'Failed to fetch product list from backend.'}
              </AlertDescription>
            </Alert>
          </div>
        ) : tableData.length === 0 ? (
          <div className="p-12 text-center space-y-3">
            <div className="h-10 w-10 rounded-full bg-slate-100 text-slate-400 flex items-center justify-center mx-auto">
              <Boxes className="h-5 w-5" />
            </div>
            <div className="space-y-1">
              <p className="text-xs font-semibold text-slate-800">
                No products found in catalog
              </p>
              <p className="text-[11px] text-slate-500 max-w-sm mx-auto">
                {filters.q || filters.categoryId || filters.active !== 'true'
                  ? 'No products match the selected filters.'
                  : 'Start building your inventory catalog by registering products.'}
              </p>
            </div>
            {isAdmin && !filters.q && (
              <Button
                variant="outline"
                size="sm"
                className="text-xs mt-2"
                onClick={handleCreate}
              >
                <Plus className="h-3.5 w-3.5 mr-1" />
                Register First Product
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
        {productsQuery.data && productsQuery.data.totalElements > 0 && (
          <div className="flex items-center justify-between px-4 py-3 border-t border-slate-100 bg-slate-50/50 text-xs text-slate-600">
            <div>
              Showing{' '}
              <span className="font-semibold text-slate-900">
                {(filters.page ?? 0) * (filters.size ?? 10) + 1}
              </span>{' '}
              to{' '}
              <span className="font-semibold text-slate-900">
                {Math.min(
                  ((filters.page ?? 0) + 1) * (filters.size ?? 10),
                  productsQuery.data.totalElements,
                )}
              </span>{' '}
              of{' '}
              <span className="font-semibold text-slate-900">
                {productsQuery.data.totalElements}
              </span>{' '}
              products
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
                Page {currentPage} of {totalPages}
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

      <ProductFormDialog
        open={formDialogOpen}
        onOpenChange={setFormDialogOpen}
        productToEdit={editingProduct}
      />

      <ProductUnitsDialog
        open={unitsDialogOpen}
        onOpenChange={setUnitsDialogOpen}
        product={selectedProductForUnits}
        currentActorRole={currentActorRole}
      />
    </div>
  )
}
