import * as React from 'react'
import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table'
import {
  BadgePercent,
  ChevronLeft,
  ChevronRight,
  Edit2,
  List,
  PowerOff,
  Star,
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
import type { PriceListResponse } from '../types/index.ts'

interface PriceListTableProps {
  priceLists: PriceListResponse[]
  isLoading?: boolean
  totalElements: number
  page: number
  size: number
  onPageChange: (newPage: number) => void
  onEdit: (priceList: PriceListResponse) => void
  onDeactivate: (priceList: PriceListResponse) => void
  onManagePrices: (priceList: PriceListResponse) => void
  isAdmin?: boolean
}

export function PriceListTable({
  priceLists,
  isLoading = false,
  totalElements,
  page,
  size,
  onPageChange,
  onEdit,
  onDeactivate,
  onManagePrices,
  isAdmin = false,
}: PriceListTableProps) {
  const { t } = useTranslation()
  const totalPages = Math.ceil(totalElements / size) || 1

  const columns = React.useMemo<ColumnDef<PriceListResponse>[]>(
    () => [
      {
        accessorKey: 'code',
        header: t('pricing.priceLists.code'),
        cell: ({ row }) => (
          <div className="flex items-center gap-2">
            <span className="font-mono font-bold text-xs bg-slate-100 text-slate-800 px-2 py-0.5 rounded border border-slate-200">
              {row.original.code}
            </span>
            {row.original.isDefault && (
              <Badge
                variant="outline"
                className="text-[9px] font-bold bg-amber-50 text-amber-800 border-amber-300 flex items-center gap-0.5"
              >
                <Star className="h-2.5 w-2.5 fill-amber-500 text-amber-500" />
                {t('pricing.priceLists.isDefault')}
              </Badge>
            )}
          </div>
        ),
      },
      {
        accessorKey: 'name',
        header: t('pricing.priceLists.name'),
        cell: ({ row }) => (
          <div>
            <div className="font-semibold text-xs text-slate-900">
              {row.original.name}
            </div>
            {row.original.description && (
              <div className="text-[11px] text-slate-500 truncate max-w-xs">
                {row.original.description}
              </div>
            )}
          </div>
        ),
      },
      {
        accessorKey: 'maxDiscountPercent',
        header: t('pricing.priceLists.maxDiscount'),
        cell: ({ row }) => (
          <div className="flex items-center gap-1 font-mono text-xs font-semibold text-amber-800 bg-amber-50/80 px-2 py-0.5 rounded border border-amber-200 w-fit">
            <BadgePercent className="h-3.5 w-3.5 text-amber-600" />
            <span>{row.original.maxDiscountPercent}%</span>
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
              variant="outline"
              className={`text-[10px] font-bold py-0.5 px-2 ${
                isActive
                  ? 'bg-emerald-50 text-emerald-800 border-emerald-300'
                  : 'bg-slate-100 text-slate-600 border-slate-300'
              }`}
            >
              {isActive ? t('common.active') : t('common.disabled')}
            </Badge>
          )
        },
      },
      {
        id: 'actions',
        header: t('common.actions'),
        cell: ({ row }) => {
          const list = row.original
          return (
            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                size="sm"
                className="h-7 px-2 text-xs text-violet-700 hover:bg-violet-50 border-violet-200"
                onClick={() => onManagePrices(list)}
                title={t('pricing.tabs.rates')}
              >
                <List className="h-3.5 w-3.5 mr-1" />
                {t('pricing.tabs.rates')}
              </Button>

              {isAdmin && (
                <>
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-7 px-2 text-xs text-slate-700 hover:text-slate-900 border-slate-300"
                    onClick={() => onEdit(list)}
                    title={t('common.edit')}
                  >
                    <Edit2 className="h-3.5 w-3.5 mr-1" />
                    {t('common.edit')}
                  </Button>

                  {list.active && (
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-7 px-2 text-xs text-rose-700 hover:bg-rose-50 border-rose-200"
                      onClick={() => onDeactivate(list)}
                      title={t('pricing.priceLists.deactivate')}
                    >
                      <PowerOff className="h-3.5 w-3.5 mr-1" />
                      {t('pricing.priceLists.deactivate')}
                    </Button>
                  )}
                </>
              )}
            </div>
          )
        },
      },
    ],
    [t, isAdmin, onEdit, onDeactivate, onManagePrices],
  )

  const table = useReactTable({
    data: priceLists,
    columns,
    getCoreRowModel: getCoreRowModel(),
  })

  if (isLoading) {
    return (
      <div className="space-y-3 p-4 bg-white rounded-xl border border-slate-200 shadow-2xs">
        <Skeleton className="h-10 w-full rounded-lg" />
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
                    <BadgePercent className="h-6 w-6 text-slate-300" />
                    <span>{t('pricing.priceLists.noLists')}</span>
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
              {priceLists.length === 0 ? 0 : page * size + 1}
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
