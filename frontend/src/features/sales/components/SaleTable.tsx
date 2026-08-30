import * as React from 'react'
import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table'
import {
  Ban,
  ChevronLeft,
  ChevronRight,
  Eye,
  Receipt,
  User,
} from 'lucide-react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table.tsx'
import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Skeleton } from '@/components/ui/skeleton.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import type { SaleSummaryResponse } from '../types/index.ts'

interface SaleTableProps {
  sales: SaleSummaryResponse[]
  isLoading?: boolean
  totalElements: number
  page: number
  size: number
  onPageChange: (newPage: number) => void
  onViewDetail: (sale: SaleSummaryResponse) => void
  onCancelSale: (sale: SaleSummaryResponse) => void
  canCancel?: boolean
}

export function SaleTable({
  sales,
  isLoading = false,
  totalElements,
  page,
  size,
  onPageChange,
  onViewDetail,
  onCancelSale,
  canCancel = false,
}: SaleTableProps) {
  const { t } = useTranslation()
  const totalPages = Math.ceil(totalElements / size) || 1

  const columns = React.useMemo<ColumnDef<SaleSummaryResponse>[]>(
    () => [
      {
        accessorKey: 'invoiceNumber',
        header: t('sales.table.invoiceNumber'),
        cell: ({ row }) => (
          <div className="flex items-center gap-2">
            <div className="h-7 w-7 rounded-lg bg-teal-50 text-teal-700 flex items-center justify-center border border-teal-200">
              <Receipt className="h-4 w-4" />
            </div>
            <span className="font-mono font-bold text-xs text-slate-900">
              {row.original.invoiceNumber}
            </span>
          </div>
        ),
      },
      {
        accessorKey: 'createdAt',
        header: t('sales.table.date'),
        cell: ({ row }) => {
          const dateStr = new Date(row.original.createdAt).toLocaleString(
            undefined,
            {
              dateStyle: 'short',
              timeStyle: 'short',
            },
          )
          return <span className="text-xs text-slate-600">{dateStr}</span>
        },
      },
      {
        accessorKey: 'customerName',
        header: t('sales.table.customer'),
        cell: ({ row }) => (
          <div className="font-semibold text-xs text-slate-900">
            {row.original.customerName}
          </div>
        ),
      },
      {
        accessorKey: 'soldBy',
        header: t('sales.table.soldBy'),
        cell: ({ row }) => {
          const username = row.original.soldBy?.username
          return (
            <div className="flex items-center gap-1.5 text-xs text-slate-600">
              <User className="h-3.5 w-3.5 text-slate-400" />
              <span>{username ?? '—'}</span>
            </div>
          )
        },
      },
      {
        accessorKey: 'branch',
        header: t('sales.table.branch'),
        cell: ({ row }) => {
          const branchName = row.original.branch?.name
          return (
            <span className="text-xs font-medium text-slate-700">
              {branchName ?? '—'}
            </span>
          )
        },
      },
      {
        accessorKey: 'totalAmount',
        header: t('sales.table.total'),
        cell: ({ row }) => (
          <span className="font-mono font-bold text-xs text-slate-900">
            ${row.original.totalAmount.toFixed(2)}
          </span>
        ),
      },
      {
        accessorKey: 'status',
        header: t('sales.table.status'),
        cell: ({ row }) => {
          const status = row.original.status
          const isCompleted = status === 'COMPLETED'
          return (
            <Badge
              variant="outline"
              className={`text-[10px] font-bold py-0.5 px-2 ${
                isCompleted
                  ? 'bg-emerald-50 text-emerald-800 border-emerald-300'
                  : 'bg-rose-50 text-rose-800 border-rose-300'
              }`}
            >
              {status === 'COMPLETED'
                ? t('sales.status.COMPLETED')
                : t('sales.status.CANCELLED')}
            </Badge>
          )
        },
      },
      {
        id: 'actions',
        header: t('common.actions'),
        cell: ({ row }) => {
          const sale = row.original
          const isCompleted = sale.status === 'COMPLETED'
          return (
            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                size="sm"
                className="h-7 px-2 text-xs text-slate-700 hover:text-teal-700 border-slate-300"
                onClick={() => onViewDetail(sale)}
                title={t('common.details')}
              >
                <Eye className="h-3.5 w-3.5 mr-1 text-teal-600" />
                {t('common.details')}
              </Button>

              {isCompleted && canCancel && (
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 px-2 text-xs text-rose-700 hover:text-rose-800 hover:bg-rose-50 border-rose-200"
                  onClick={() => onCancelSale(sale)}
                  title={t('sales.detail.voidSale')}
                >
                  <Ban className="h-3.5 w-3.5 mr-1 text-rose-600" />
                  {t('sales.detail.voidSale')}
                </Button>
              )}
            </div>
          )
        },
      },
    ],
    [t, onViewDetail, onCancelSale, canCancel],
  )

  const table = useReactTable({
    data: sales,
    columns,
    getCoreRowModel: getCoreRowModel(),
  })

  if (isLoading) {
    return (
      <div className="space-y-3 p-4 bg-white rounded-xl border border-slate-200 shadow-2xs">
        <Skeleton className="h-10 w-full rounded-lg" />
        <Skeleton className="h-12 w-full rounded-lg" />
        <Skeleton className="h-12 w-full rounded-lg" />
        <Skeleton className="h-12 w-full rounded-lg" />
        <Skeleton className="h-12 w-full rounded-lg" />
      </div>
    )
  }

  return (
    <div className="space-y-3">
      <div className="rounded-xl border border-slate-200 bg-white overflow-hidden shadow-2xs">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-200">
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead
                    key={header.id}
                    className="text-xs font-bold text-slate-700 uppercase tracking-wider h-10 px-3"
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
            {table.getRowModel().rows.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={columns.length}
                  className="h-32 text-center text-slate-400 text-xs"
                >
                  <div className="flex flex-col items-center justify-center gap-1.5">
                    <Receipt className="h-6 w-6 text-slate-300" />
                    <span>{t('sales.table.noSales')}</span>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  className="hover:bg-slate-50/80 transition-colors border-b border-slate-100 last:border-0"
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id} className="py-2.5 px-3">
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext(),
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination Footer */}
      {totalElements > 0 && (
        <div className="flex items-center justify-between px-2 py-1 text-xs text-slate-600">
          <div>
            {t('common.showing')}{' '}
            <span className="font-semibold text-slate-900">
              {sales.length === 0 ? 0 : page * size + 1}
            </span>{' '}
            {t('common.to')}{' '}
            <span className="font-semibold text-slate-900">
              {Math.min((page + 1) * size, totalElements)}
            </span>{' '}
            {t('common.of')}{' '}
            <span className="font-semibold text-slate-900">
              {totalElements}
            </span>{' '}
            {t('common.results')}
          </div>

          <div className="flex items-center gap-2">
            <span className="text-slate-500 font-medium">
              {t('common.pageOf', {
                page: String(page + 1),
                totalPages: String(totalPages),
              })}
            </span>
            <Button
              variant="outline"
              size="sm"
              className="h-7 w-7 p-0 border-slate-300"
              disabled={page <= 0}
              onClick={() => onPageChange(page - 1)}
              title={t('common.previous')}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="sm"
              className="h-7 w-7 p-0 border-slate-300"
              disabled={page + 1 >= totalPages}
              onClick={() => onPageChange(page + 1)}
              title={t('common.next')}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
