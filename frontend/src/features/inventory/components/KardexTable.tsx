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
import { useUsers } from '@/features/iam/hooks/use-users.ts'
import { useProducts } from '@/features/catalog/hooks/use-products.ts'
import { useKardex } from '../hooks/use-inventory.ts'
import { STOCK_MOVEMENT_TYPE } from '../schemas/kardex.schema.ts'
import type { KardexLineResponse, StockMovementType } from '../types/index.ts'
import {
  AlertCircle,
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  Calendar,
  ChevronLeft,
  ChevronRight,
  History,
  RotateCcw,
  Shield,
  User,
} from 'lucide-react'

interface KardexTableProps {
  initialProductExternalId?: string
}

export function KardexTable({ initialProductExternalId }: KardexTableProps) {
  const [page, setPage] = React.useState(0)
  const [size] = React.useState(20)
  const [productExternalId, setProductExternalId] = React.useState<string>(
    initialProductExternalId ?? '',
  )
  const [movementType, setMovementType] = React.useState<StockMovementType | 'ALL'>('ALL')
  const [fromDate, setFromDate] = React.useState<string>('')
  const [toDate, setToDate] = React.useState<string>('')

  React.useEffect(() => {
    if (initialProductExternalId) {
      setProductExternalId(initialProductExternalId)
    }
  }, [initialProductExternalId])

  const kardexQuery = useKardex({
    page,
    size,
    productExternalId: productExternalId ? productExternalId : undefined,
    movementType: movementType !== 'ALL' ? movementType : undefined,
    from: fromDate ? new Date(fromDate).toISOString() : undefined,
    to: toDate ? new Date(toDate).toISOString() : undefined,
  })

  const rawData = kardexQuery.data?.content ?? []

  const isInbound = (type: StockMovementType) => {
    return (
      type === STOCK_MOVEMENT_TYPE.PURCHASE_RECEIPT ||
      type === STOCK_MOVEMENT_TYPE.TRANSFER_IN ||
      type === STOCK_MOVEMENT_TYPE.ADJUSTMENT_POS ||
      type === STOCK_MOVEMENT_TYPE.INITIAL_LOAD
    )
  }

  const usersQuery = useUsers({ page: 0, size: 100 })
  const productsQuery = useProducts({ page: 0, size: 100 })

  const usersMap = React.useMemo(() => {
    const map = new Map<string, string>()
    usersQuery.data?.content.forEach((u) => {
      map.set(u.externalId, u.username)
    })
    return map
  }, [usersQuery.data])

  const productsMap = React.useMemo(() => {
    const map = new Map<string, { name: string; sku: string }>()
    productsQuery.data?.content.forEach((p) => {
      map.set(p.externalId, { name: p.name, sku: p.sku })
    })
    return map
  }, [productsQuery.data])

  const columns = React.useMemo<ColumnDef<KardexLineResponse>[]>(
    () => [
      {
        accessorKey: 'createdAt',
        header: 'Timestamp (UTC)',
        cell: ({ row }) => {
          const d = new Date(row.original.createdAt)
          return (
            <div className="text-xs">
              <p className="font-mono font-medium text-slate-800">
                {d.toLocaleDateString()}
              </p>
              <p className="text-[10px] text-slate-600 font-mono">
                {d.toLocaleTimeString()}
              </p>
            </div>
          )
        },
      },
      {
        accessorKey: 'productExternalId',
        header: 'Product',
        cell: ({ row }) => {
          const pId = row.original.productExternalId
          const prod = productsMap.get(pId)
          if (!prod) {
            return <span className="text-xs font-mono text-slate-600">{pId.substring(0, 8)}...</span>
          }
          return (
            <div className="text-xs max-w-[180px]">
              <p className="font-semibold text-slate-900 truncate">{prod.name}</p>
              <span className="font-mono text-[10px] bg-slate-100 text-slate-600 px-1 py-0.5 rounded border border-slate-200">
                {prod.sku}
              </span>
            </div>
          )
        },
      },
      {
        accessorKey: 'movementType',
        header: 'Movement Type',
        cell: ({ row }) => {
          const type = row.original.movementType
          const inbound = isInbound(type)
          return (
            <div className="flex items-center gap-1.5">
              {inbound ? (
                <div className="h-5 w-5 rounded bg-emerald-100 text-emerald-700 flex items-center justify-center">
                  <ArrowUpRight className="h-3 w-3" />
                </div>
              ) : (
                <div className="h-5 w-5 rounded bg-rose-100 text-rose-700 flex items-center justify-center">
                  <ArrowDownRight className="h-3 w-3" />
                </div>
              )}
              <Badge
                variant="outline"
                className={`text-[10px] py-0 px-1.5 font-mono ${
                  inbound
                    ? 'bg-emerald-50 text-emerald-800 border-emerald-300'
                    : 'bg-rose-50 text-rose-800 border-rose-300'
                }`}
              >
                {type}
              </Badge>
            </div>
          )
        },
      },
      {
        accessorKey: 'quantity',
        header: () => <div className="text-right">Quantity</div>,
        cell: ({ row }) => {
          const { quantity, movementType } = row.original
          const inbound = isInbound(movementType)
          return (
            <div
              className={`text-right font-mono font-bold text-xs ${
                inbound ? 'text-emerald-700' : 'text-rose-700'
              }`}
            >
              {inbound ? `+${quantity}` : `-${quantity}`}
            </div>
          )
        },
      },
      {
        accessorKey: 'costs',
        header: () => <div className="text-right">Unit / Total Cost</div>,
        cell: ({ row }) => {
          const { unitCost, totalCost } = row.original
          if (unitCost === null || unitCost === undefined) {
            return <div className="text-right text-[11px] text-slate-600">—</div>
          }
          return (
            <div className="text-right text-xs">
              <p className="font-mono text-slate-900 font-semibold">
                ${totalCost ? totalCost.toFixed(2) : (unitCost * row.original.quantity).toFixed(2)}
              </p>
              <p className="text-[10px] text-slate-600 font-mono">
                ${unitCost.toFixed(2)}/u
              </p>
            </div>
          )
        },
      },
      {
        accessorKey: 'balance',
        header: 'Balance Progression',
        cell: ({ row }) => {
          const { previousStock, resultingStock } = row.original
          return (
            <div className="flex items-center gap-1.5 text-xs font-mono">
              <span className="text-slate-600">{previousStock}</span>
              <ArrowRight className="h-3 w-3 text-slate-400" />
              <span className="font-bold text-slate-900">{resultingStock}</span>
            </div>
          )
        },
      },
      {
        accessorKey: 'reason',
        header: 'Reason / Reference',
        cell: ({ row }) => {
          const { notes, referenceType, referenceId } = row.original
          return (
            <div className="text-xs max-w-[200px] truncate">
              {notes && <p className="text-slate-800 truncate">{notes}</p>}
              {referenceType && (
                <p className="text-[10px] text-slate-600 font-mono">
                  Ref: {referenceType} {referenceId ? `(${referenceId})` : ''}
                </p>
              )}
              {!notes && !referenceType && (
                <span className="text-[11px] text-slate-600 italic">No notes</span>
              )}
            </div>
          )
        },
      },
      {
        accessorKey: 'userExternalId',
        header: 'Responsible User',
        cell: ({ row }) => {
          const userId = row.original.userExternalId
          if (!userId) {
            return (
              <div className="flex items-center gap-1 text-[11px] text-slate-500 font-mono italic">
                <Shield className="h-3 w-3 text-slate-400" />
                <span>System</span>
              </div>
            )
          }
          const username = usersMap.get(userId) ?? `${userId.substring(0, 8)}...`
          return (
            <div
              className="flex items-center gap-1.5 text-xs text-slate-800 font-medium"
              title={`User ID: ${userId}`}
            >
              <User className="h-3.5 w-3.5 text-slate-400 shrink-0" />
              <span className="font-semibold text-slate-900">{username}</span>
            </div>
          )
        },
      },
    ],
    [usersMap, productsMap],
  )

  const table = useReactTable({
    data: rawData,
    columns,
    getCoreRowModel: getCoreRowModel(),
  })

  const totalElements = kardexQuery.data?.totalElements ?? 0
  const totalPages = Math.ceil(totalElements / size) || 1

  const handleResetFilters = () => {
    setProductExternalId('')
    setMovementType('ALL')
    setFromDate('')
    setToDate('')
    setPage(0)
  }

  return (
    <div className="space-y-4">
      {/* Kardex Filters Bar */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-2xs space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <div className="h-6 w-6 rounded bg-slate-100 text-slate-700 flex items-center justify-center">
              <History className="h-3.5 w-3.5" />
            </div>
            <span className="text-xs font-bold text-slate-900 uppercase tracking-wider">
              Immutable Kardex Audit Ledger
            </span>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleResetFilters}
            className="text-xs h-7 text-slate-600 hover:text-slate-900"
          >
            <RotateCcw className="h-3 w-3 mr-1" />
            Reset Filters
          </Button>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3 pt-1">
          <div>
            <label className="text-[11px] font-semibold text-slate-600 block mb-1">
              Movement Type
            </label>
            <Select
              value={movementType}
              onValueChange={(val) => {
                setMovementType(val as StockMovementType | 'ALL')
                setPage(0)
              }}
            >
              <SelectTrigger className="text-xs h-8 bg-slate-50 border-slate-200">
                <SelectValue placeholder="All Movement Types" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Movement Types</SelectItem>
                <SelectItem value="PURCHASE_RECEIPT">PURCHASE_RECEIPT (Inbound)</SelectItem>
                <SelectItem value="SALE">SALE (Outbound)</SelectItem>
                <SelectItem value="TRANSFER_IN">TRANSFER_IN (Inbound)</SelectItem>
                <SelectItem value="TRANSFER_OUT">TRANSFER_OUT (Outbound)</SelectItem>
                <SelectItem value="ADJUSTMENT_POS">ADJUSTMENT_POS (Inbound)</SelectItem>
                <SelectItem value="ADJUSTMENT_NEG">ADJUSTMENT_NEG (Outbound)</SelectItem>
                <SelectItem value="DAMAGE_WASTE">DAMAGE_WASTE (Outbound)</SelectItem>
                <SelectItem value="INITIAL_LOAD">INITIAL_LOAD (Inbound)</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div>
            <label className="text-[11px] font-semibold text-slate-600 block mb-1">
              From Date
            </label>
            <div className="relative">
              <Calendar className="h-3.5 w-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
              <Input
                type="date"
                value={fromDate}
                onChange={(e) => {
                  setFromDate(e.target.value)
                  setPage(0)
                }}
                className="text-xs h-8 pl-8 bg-slate-50 border-slate-200"
              />
            </div>
          </div>

          <div>
            <label className="text-[11px] font-semibold text-slate-600 block mb-1">
              To Date
            </label>
            <div className="relative">
              <Calendar className="h-3.5 w-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
              <Input
                type="date"
                value={toDate}
                onChange={(e) => {
                  setToDate(e.target.value)
                  setPage(0)
                }}
                className="text-xs h-8 pl-8 bg-slate-50 border-slate-200"
              />
            </div>
          </div>

          <div>
            <label className="text-[11px] font-semibold text-slate-600 block mb-1">
              Product ID Filter (Optional)
            </label>
            <Input
              type="text"
              placeholder="UUID filter..."
              value={productExternalId}
              onChange={(e) => {
                setProductExternalId(e.target.value.trim())
                setPage(0)
              }}
              className="text-xs h-8 font-mono bg-slate-50 border-slate-200"
            />
          </div>
        </div>
      </div>

      {/* Main Kardex Table */}
      <div className="bg-white border border-slate-200 rounded-xl shadow-2xs overflow-hidden">
        {kardexQuery.isLoading ? (
          <div className="p-6 space-y-3">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        ) : kardexQuery.isError ? (
          <div className="p-8 text-center">
            <div className="h-10 w-10 rounded-full bg-rose-50 text-rose-600 flex items-center justify-center mx-auto mb-3">
              <AlertCircle className="h-5 w-5" />
            </div>
            <p className="text-sm font-bold text-slate-900">Failed to load Kardex ledger</p>
            <p className="text-xs text-slate-500 mt-1">{kardexQuery.error.message}</p>
          </div>
        ) : rawData.length === 0 ? (
          <div className="p-12 text-center">
            <div className="h-12 w-12 rounded-full bg-slate-100 text-slate-400 flex items-center justify-center mx-auto mb-3">
              <History className="h-6 w-6" />
            </div>
            <p className="text-sm font-bold text-slate-800">No Kardex records found</p>
            <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
              No stock transactions match the selected filter criteria.
            </p>
          </div>
        ) : (
          <Table>
            <TableHeader className="bg-slate-50 border-b border-slate-200">
              {table.getHeaderGroups().map((headerGroup) => (
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
              {table.getRowModel().rows.map((row) => (
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
        {!kardexQuery.isLoading && !kardexQuery.isError && rawData.length > 0 && (
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
              movements
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
    </div>
  )
}
