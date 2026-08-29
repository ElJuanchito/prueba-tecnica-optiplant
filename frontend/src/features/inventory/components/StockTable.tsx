import * as React from 'react'
import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table'
import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
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
import { useProducts } from '@/features/catalog/hooks/use-products.ts'
import type { ProductListItemResponse } from '@/features/catalog/schemas/product.schema.ts'
import { useStock } from '../hooks/use-inventory.ts'
import type { StockLineResponse } from '../types/index.ts'
import { AdjustStockDialog } from './AdjustStockDialog.tsx'
import {
  NetworkAvailabilityDialog,
  type NetworkAvailabilityProduct,
} from './NetworkAvailabilityDialog.tsx'
import { ThresholdDialog } from './ThresholdDialog.tsx'
import { WriteOffDialog } from './WriteOffDialog.tsx'
import {
  AlertCircle,
  AlertTriangle,
  ArrowUpDown,
  Bell,
  ChevronLeft,
  ChevronRight,
  Clock,
  Filter,
  Globe,
  History,
  Info,
  Package,
  Search,
  Sliders,
  Trash2,
} from 'lucide-react'

interface StockTableProps {
  currentActorRole: 'ADMIN' | 'BRANCH_MANAGER' | 'OPERATOR'
  onViewKardex?: ((productExternalId: string) => void) | undefined
}

export function StockTable({
  currentActorRole,
  onViewKardex,
}: StockTableProps) {
  const isAdmin = currentActorRole === 'ADMIN'

  const [page, setPage] = React.useState(0)
  const [size] = React.useState(15)
  const [searchFilter, setSearchFilter] = React.useState('')
  const [belowThreshold, setBelowThreshold] = React.useState(false)
  const [sortBy, setSortBy] = React.useState<'product' | 'currentStock'>('product')

  // Modals state
  const [selectedStockProduct, setSelectedStockProduct] = React.useState<StockLineResponse | null>(null)
  const [selectedAdminProduct, setSelectedAdminProduct] = React.useState<NetworkAvailabilityProduct | null>(null)
  const [isNetworkOpen, setIsNetworkOpen] = React.useState(false)
  const [isAdjustOpen, setIsAdjustOpen] = React.useState(false)
  const [isWriteOffOpen, setIsWriteOffOpen] = React.useState(false)
  const [isThresholdOpen, setIsThresholdOpen] = React.useState(false)

  // Branch Manager & Operator use own-branch stock query
  const stockQuery = useStock(
    {
      page,
      size,
      belowThreshold,
      sort: sortBy,
    },
    !isAdmin,
  )

  // Corporate ADMIN queries catalog master for network availability
  const productsQuery = useProducts(
    {
      page,
      size,
      q: searchFilter.trim() || undefined,
      sort: 'name',
      direction: 'asc',
    },
    isAdmin,
  )

  const rawData = stockQuery.data?.content ?? []

  // Filter client-side by SKU / Name for branch queries
  const filteredData = React.useMemo(() => {
    if (!searchFilter.trim()) return rawData
    const q = searchFilter.toLowerCase()
    return rawData.filter(
      (item) =>
        item.name.toLowerCase().includes(q) ||
        item.sku.toLowerCase().includes(q),
    )
  }, [rawData, searchFilter])

  const canAdjust = currentActorRole === 'BRANCH_MANAGER'
  const canSetThreshold = currentActorRole === 'BRANCH_MANAGER'
  const canWriteOff = !isAdmin // Operational write-offs occur in branch stations

  const adminColumns = React.useMemo<ColumnDef<ProductListItemResponse>[]>(
    () => [
      {
        accessorKey: 'name',
        header: 'Product / SKU',
        cell: ({ row }) => {
          const item = row.original
          return (
            <div>
              <p className="font-semibold text-slate-900 text-xs">{item.name}</p>
              <div className="flex items-center gap-1.5 mt-0.5">
                <span className="font-mono text-[10px] bg-slate-100 text-slate-700 px-1.5 py-0.2 rounded border border-slate-200">
                  {item.sku}
                </span>
                {item.category && (
                  <span className="text-[10px] bg-indigo-50 text-indigo-700 px-1.5 py-0.2 rounded border border-indigo-200">
                    {item.category.name}
                  </span>
                )}
              </div>
            </div>
          )
        },
      },
      {
        accessorKey: 'baseUnit',
        header: 'Base Unit',
        cell: ({ row }) => (
          <span className="font-mono text-xs text-slate-700 bg-slate-100 px-2 py-0.5 rounded">
            {row.original.baseUnit}
          </span>
        ),
      },
      {
        accessorKey: 'active',
        header: 'Catalog Status',
        cell: ({ row }) => (
          <Badge
            variant="outline"
            className={`text-[10px] py-0 px-1.5 ${
              row.original.active
                ? 'bg-emerald-50 text-emerald-800 border-emerald-300'
                : 'bg-slate-100 text-slate-500 border-slate-300'
            }`}
          >
            {row.original.active ? 'Active Master' : 'Disabled'}
          </Badge>
        ),
      },
      {
        id: 'actions',
        header: () => <div className="text-right">Network Actions</div>,
        cell: ({ row }) => {
          const item = row.original
          return (
            <div className="flex items-center justify-end gap-1.5">
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setSelectedAdminProduct({
                    externalId: item.externalId,
                    name: item.name,
                    sku: item.sku,
                  })
                  setIsNetworkOpen(true)
                }}
                className="h-7 px-2.5 text-xs font-semibold text-indigo-700 border-indigo-200 hover:bg-indigo-50"
              >
                <Globe className="h-3.5 w-3.5 mr-1" />
                Network Availability
              </Button>
              {onViewKardex && (
                <Button
                  variant="ghost"
                  size="sm"
                  title="View Multi-Branch Kardex Movements"
                  onClick={() => onViewKardex(item.externalId)}
                  className="h-7 w-7 p-0 text-slate-600 hover:text-slate-900 hover:bg-slate-100"
                >
                  <History className="h-3.5 w-3.5" />
                </Button>
              )}
            </div>
          )
        },
      },
    ],
    [onViewKardex],
  )

  const columns = React.useMemo<ColumnDef<StockLineResponse>[]>(
    () => [
      {
        accessorKey: 'product',
        header: 'Product / SKU',
        cell: ({ row }) => {
          const item = row.original
          return (
            <div>
              <p className="font-semibold text-slate-900 text-xs">{item.name}</p>
              <div className="flex items-center gap-1.5 mt-0.5">
                <span className="font-mono text-[10px] bg-slate-100 text-slate-700 px-1.5 py-0.2 rounded border border-slate-200">
                  {item.sku}
                </span>
                {item.lastUpdatedAt && (
                  <span className="text-[10px] text-slate-600 flex items-center gap-0.5">
                    <Clock className="h-2.5 w-2.5" />
                    {new Date(item.lastUpdatedAt).toLocaleDateString()}
                  </span>
                )}
              </div>
            </div>
          )
        },
      },
      {
        accessorKey: 'currentStock',
        header: () => <div className="text-right">Current Stock</div>,
        cell: ({ row }) => (
          <div className="text-right font-mono font-bold text-xs text-slate-900">
            {row.original.currentStock}
          </div>
        ),
      },
      {
        accessorKey: 'reservedStock',
        header: () => <div className="text-right">Reserved</div>,
        cell: ({ row }) => {
          const val = row.original.reservedStock
          return (
            <div
              className={`text-right font-mono text-xs ${
                val > 0 ? 'text-amber-700 font-semibold' : 'text-slate-600'
              }`}
            >
              {val}
            </div>
          )
        },
      },
      {
        accessorKey: 'inTransitStock',
        header: () => <div className="text-right">In-Transit</div>,
        cell: ({ row }) => {
          const val = row.original.inTransitStock
          return (
            <div
              className={`text-right font-mono text-xs ${
                val > 0 ? 'text-sky-700 font-semibold' : 'text-slate-600'
              }`}
            >
              {val}
            </div>
          )
        },
      },
      {
        accessorKey: 'availableStock',
        header: () => <div className="text-right">Available</div>,
        cell: ({ row }) => {
          const val = row.original.availableStock
          return (
            <div
              className={`text-right font-mono text-xs font-bold ${
                val <= 0 ? 'text-rose-700' : 'text-emerald-700'
              }`}
            >
              {val}
            </div>
          )
        },
      },
      {
        accessorKey: 'threshold',
        header: () => <div className="text-right">Min Threshold</div>,
        cell: ({ row }) => (
          <div className="text-right font-mono text-xs text-slate-700">
            {row.original.minStockThreshold}
          </div>
        ),
      },
      {
        accessorKey: 'status',
        header: 'Stock Status',
        cell: ({ row }) => {
          const { currentStock, minStockThreshold } = row.original
          if (currentStock === 0) {
            return (
              <Badge
                variant="destructive"
                className="text-[10px] py-0 px-1.5 bg-rose-100 text-rose-800 border-rose-200"
              >
                Out of Stock
              </Badge>
            )
          }
          if (currentStock <= minStockThreshold) {
            return (
              <Badge
                variant="outline"
                className="text-[10px] py-0 px-1.5 bg-amber-50 text-amber-800 border-amber-300 flex items-center gap-1 w-fit"
              >
                <AlertTriangle className="h-2.5 w-2.5 text-amber-600" />
                Low Stock
              </Badge>
            )
          }
          return (
            <Badge
              variant="outline"
              className="text-[10px] py-0 px-1.5 bg-emerald-50 text-emerald-800 border-emerald-300"
            >
              In Stock
            </Badge>
          )
        },
      },
      {
        accessorKey: 'valuation',
        header: () => <div className="text-right">Avg Cost / Value</div>,
        cell: ({ row }) => {
          const { currentStock, averageCost } = row.original
          const totalVal = currentStock * averageCost
          return (
            <div className="text-right text-xs font-mono">
              <p className="font-semibold text-slate-900">
                ${totalVal.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </p>
              <p className="text-[10px] text-slate-600">
                ${averageCost.toFixed(2)}/u
              </p>
            </div>
          )
        },
      },
      {
        id: 'actions',
        header: () => <div className="text-right">Actions</div>,
        cell: ({ row }) => {
          const item = row.original
          return (
            <div className="flex items-center justify-end space-x-1">
              <Button
                variant="ghost"
                size="sm"
                title="View stock across branches (CU-INV-04)"
                onClick={() => {
                  setSelectedStockProduct(item)
                  setIsNetworkOpen(true)
                }}
                className="h-7 w-7 p-0 text-slate-600 hover:text-slate-900 hover:bg-slate-100"
              >
                <Globe className="h-3.5 w-3.5" />
              </Button>

              {canAdjust && (
                <Button
                  variant="ghost"
                  size="sm"
                  title="Adjust stock discrepancy (CU-INV-05)"
                  onClick={() => {
                    setSelectedStockProduct(item)
                    setIsAdjustOpen(true)
                  }}
                  className="h-7 w-7 p-0 text-slate-600 hover:text-slate-900 hover:bg-slate-100"
                >
                  <Sliders className="h-3.5 w-3.5" />
                </Button>
              )}

              {canWriteOff && (
                <Button
                  variant="ghost"
                  size="sm"
                  title="Write-off damage or waste (CU-INV-06)"
                  onClick={() => {
                    setSelectedStockProduct(item)
                    setIsWriteOffOpen(true)
                  }}
                  className="h-7 w-7 p-0 text-rose-600 hover:text-rose-900 hover:bg-rose-50"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </Button>
              )}

              {canSetThreshold && (
                <Button
                  variant="ghost"
                  size="sm"
                  title="Set safety stock threshold (CU-INV-07)"
                  onClick={() => {
                    setSelectedStockProduct(item)
                    setIsThresholdOpen(true)
                  }}
                  className="h-7 w-7 p-0 text-slate-600 hover:text-slate-900 hover:bg-slate-100"
                >
                  <Bell className="h-3.5 w-3.5" />
                </Button>
              )}

              {onViewKardex && (
                <Button
                  variant="ghost"
                  size="sm"
                  title="View Kardex movements history"
                  onClick={() => onViewKardex(item.productExternalId)}
                  className="h-7 w-7 p-0 text-slate-600 hover:text-slate-900 hover:bg-slate-100"
                >
                  <History className="h-3.5 w-3.5" />
                </Button>
              )}
            </div>
          )
        },
      },
    ],
    [canAdjust, canSetThreshold, canWriteOff, onViewKardex],
  )

  const branchTable = useReactTable({
    data: filteredData,
    columns,
    getCoreRowModel: getCoreRowModel(),
  })

  const adminTable = useReactTable({
    data: productsQuery.data?.content ?? [],
    columns: adminColumns,
    getCoreRowModel: getCoreRowModel(),
  })

  const isLoading = isAdmin ? productsQuery.isLoading : stockQuery.isLoading
  const isError = isAdmin ? productsQuery.isError : stockQuery.isError
  const errorObj = isAdmin ? productsQuery.error : stockQuery.error
  const totalElements = isAdmin
    ? (productsQuery.data?.totalElements ?? 0)
    : (stockQuery.data?.totalElements ?? 0)
  const totalPages = Math.ceil(totalElements / size) || 1
  const hasRows = isAdmin
    ? (productsQuery.data?.content.length ?? 0) > 0
    : filteredData.length > 0

  return (
    <div className="space-y-4">
      {/* Search & Filters Bar */}
      <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between bg-white p-3.5 rounded-xl border border-slate-200 shadow-2xs">
        <div className="flex flex-1 items-center gap-2 max-w-md">
          <div className="relative w-full">
            <Search className="h-3.5 w-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <Input
              value={searchFilter}
              onChange={(e) => {
                setSearchFilter(e.target.value)
                setPage(0)
              }}
              placeholder="Filter by SKU or Product Name..."
              className="pl-8 text-xs h-8 bg-slate-50 border-slate-200"
            />
          </div>
        </div>

        {isAdmin ? (
          <div className="flex items-center gap-2">
            <Badge
              variant="outline"
              className="text-xs py-1 px-2.5 bg-indigo-50 text-indigo-700 border-indigo-200 flex items-center gap-1.5 font-medium"
            >
              <Globe className="h-3.5 w-3.5" />
              Corporate Network Explorer
            </Badge>
          </div>
        ) : (
          <div className="flex items-center gap-2 flex-wrap">
            <Button
              variant={belowThreshold ? 'default' : 'outline'}
              size="sm"
              onClick={() => {
                setBelowThreshold(!belowThreshold)
                setPage(0)
              }}
              className={`text-xs h-8 ${
                belowThreshold
                  ? 'bg-amber-600 hover:bg-amber-700 text-white'
                  : 'text-slate-700 border-slate-300 hover:bg-slate-50'
              }`}
            >
              <Filter className="h-3 w-3 mr-1.5" />
              {belowThreshold ? 'Critical Stock Filter Active' : 'Filter Below Threshold'}
            </Button>

            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setSortBy(sortBy === 'product' ? 'currentStock' : 'product')
                setPage(0)
              }}
              className="text-xs h-8 text-slate-700 border-slate-300 hover:bg-slate-50"
            >
              <ArrowUpDown className="h-3 w-3 mr-1.5" />
              Sort: {sortBy === 'product' ? 'Product Name' : 'Current Stock'}
            </Button>
          </div>
        )}
      </div>

      {/* Corporate Admin Information Banner */}
      {isAdmin && (
        <div className="bg-indigo-50/60 border border-indigo-100 rounded-xl p-3.5 flex items-start gap-3 text-xs text-indigo-900">
          <Info className="h-4 w-4 text-indigo-600 shrink-0 mt-0.5" />
          <div>
            <span className="font-bold text-indigo-950">
              Corporate Administrator Multi-Branch Scope:
            </span>{' '}
            As corporate administrator, stock balances are distributed across branch stations. Click{' '}
            <span className="font-semibold text-indigo-800">"Network Availability"</span> on any product
            to inspect real-time local balances, reservations, and in-transit units across all active branches.
          </div>
        </div>
      )}

      {/* Main Stock Table */}
      <div className="bg-white border border-slate-200 rounded-xl shadow-2xs overflow-hidden">
        {isLoading ? (
          <div className="p-6 space-y-3">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        ) : isError ? (
          <div className="p-8 text-center">
            <div className="h-10 w-10 rounded-full bg-rose-50 text-rose-600 flex items-center justify-center mx-auto mb-3">
              <AlertCircle className="h-5 w-5" />
            </div>
            <p className="text-sm font-bold text-slate-900">
              {isAdmin ? 'Failed to load catalog products' : 'Failed to load stock data'}
            </p>
            <p className="text-xs text-slate-500 mt-1">
              {errorObj instanceof Error ? errorObj.message : 'Unknown error'}
            </p>
          </div>
        ) : !hasRows ? (
          <div className="p-12 text-center">
            <div className="h-12 w-12 rounded-full bg-slate-100 text-slate-400 flex items-center justify-center mx-auto mb-3">
              <Package className="h-6 w-6" />
            </div>
            <p className="text-sm font-bold text-slate-800">
              {isAdmin ? 'No catalog products found' : 'No stock records found'}
            </p>
            <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
              {isAdmin
                ? 'There are no catalog products matching the search query.'
                : belowThreshold
                  ? 'No products are currently at or below their minimum stock threshold.'
                  : 'There are no registered stock balances in this branch yet.'}
            </p>
          </div>
        ) : isAdmin ? (
          <Table>
            <TableHeader className="bg-slate-50 border-b border-slate-200">
              {adminTable.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id}>
                  {headerGroup.headers.map((header) => (
                    <TableHead
                      key={header.id}
                      className="text-xs font-semibold text-slate-700 py-3"
                    >
                      {flexRender(
                        header.column.columnDef.header,
                        header.getContext(),
                      )}
                    </TableHead>
                  ))}
                </TableRow>
              ))}
            </TableHeader>
            <TableBody>
              {adminTable.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  className="hover:bg-slate-50/80 transition-colors"
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
              ))}
            </TableBody>
          </Table>
        ) : (
          <Table>
            <TableHeader className="bg-slate-50 border-b border-slate-200">
              {branchTable.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id}>
                  {headerGroup.headers.map((header) => (
                    <TableHead
                      key={header.id}
                      className="text-xs font-semibold text-slate-700 py-3"
                    >
                      {flexRender(
                        header.column.columnDef.header,
                        header.getContext(),
                      )}
                    </TableHead>
                  ))}
                </TableRow>
              ))}
            </TableHeader>
            <TableBody>
              {branchTable.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  className="hover:bg-slate-50/80 transition-colors"
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
              ))}
            </TableBody>
          </Table>
        )}

        {/* Pagination Bar */}
        {!isLoading && !isError && hasRows && (
          <div className="p-3.5 border-t border-slate-200 bg-slate-50 flex items-center justify-between text-xs text-slate-600">
            <div>
              Showing{' '}
              <span className="font-semibold text-slate-900">
                {page * size + 1}
              </span>{' '}
              to{' '}
              <span className="font-semibold text-slate-900">
                {Math.min((page + 1) * size, totalElements)}
              </span>{' '}
              of{' '}
              <span className="font-semibold text-slate-900">
                {totalElements}
              </span>{' '}
              {isAdmin ? 'catalog products' : 'products in stock'}
            </div>
            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="h-7 px-2.5 text-xs"
              >
                <ChevronLeft className="h-3 w-3 mr-1" />
                Previous
              </Button>
              <span className="px-2 text-xs font-medium">
                Page {page + 1} of {totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="h-7 px-2.5 text-xs"
              >
                Next
                <ChevronRight className="h-3 w-3 ml-1" />
              </Button>
            </div>
          </div>
        )}
      </div>

      {/* Action Dialogs */}
      <NetworkAvailabilityDialog
        product={isAdmin ? selectedAdminProduct : selectedStockProduct}
        open={isNetworkOpen}
        onOpenChange={setIsNetworkOpen}
      />

      <AdjustStockDialog
        product={selectedStockProduct}
        open={isAdjustOpen}
        onOpenChange={setIsAdjustOpen}
      />

      <WriteOffDialog
        product={selectedStockProduct}
        open={isWriteOffOpen}
        onOpenChange={setIsWriteOffOpen}
      />

      <ThresholdDialog
        product={selectedStockProduct}
        open={isThresholdOpen}
        onOpenChange={setIsThresholdOpen}
      />
    </div>
  )
}
