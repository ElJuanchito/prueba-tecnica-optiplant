import * as React from 'react'
import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table'
import {
  Building2,
  Calendar,
  CheckCircle,
  ChevronLeft,
  ChevronRight,
  Clock,
  DollarSign,
  Globe,
  Tag,
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
import type { PriceResponse } from '../types/index.ts'
import { useProducts } from '@/features/catalog/hooks/use-products.ts'
import { useBranches } from '@/features/iam/hooks/use-branches.ts'

interface PriceTableProps {
  prices: PriceResponse[]
  isLoading?: boolean
  totalElements: number
  page: number
  size: number
  onPageChange: (newPage: number) => void
  onClosePrice: (price: PriceResponse) => void
  isAdmin?: boolean
}

export function PriceTable({
  prices,
  isLoading = false,
  totalElements,
  page,
  size,
  onPageChange,
  onClosePrice,
  isAdmin = false,
}: PriceTableProps) {
  const { t } = useTranslation()
  const totalPages = Math.ceil(totalElements / size) || 1

  // Load product catalog and branches for mapping UUIDs to human names
  const productsQuery = useProducts({ size: 100 })
  const branchesQuery = useBranches({ size: 50 })

  const productsMap = React.useMemo(() => {
    const map = new Map<string, { sku: string; name: string }>()
    for (const p of productsQuery.data?.content ?? []) {
      map.set(p.externalId, { sku: p.sku, name: p.name })
    }
    return map
  }, [productsQuery.data])

  const branchesMap = React.useMemo(() => {
    const map = new Map<string, string>()
    for (const b of branchesQuery.data?.content ?? []) {
      map.set(b.externalId, b.name)
    }
    return map
  }, [branchesQuery.data])

  const columns = React.useMemo<ColumnDef<PriceResponse>[]>(
    () => [
      {
        accessorKey: 'productExternalId',
        header: t('pricing.rates.product'),
        cell: ({ row }) => {
          const product = productsMap.get(row.original.productExternalId)
          return (
            <div className="flex items-center gap-2">
              <div className="h-7 w-7 rounded-lg bg-violet-50 text-violet-700 flex items-center justify-center border border-violet-200">
                <Tag className="h-3.5 w-3.5" />
              </div>
              <div>
                <div className="font-semibold text-xs text-slate-900">
                  {product?.name ?? '—'}
                </div>
                <div className="font-mono text-[10px] text-slate-500">
                  {product?.sku ?? '—'}
                </div>
              </div>
            </div>
          )
        },
      },
      {
        accessorKey: 'branchExternalId',
        header: t('pricing.rates.scope'),
        cell: ({ row }) => {
          const branchId = row.original.branchExternalId
          if (!branchId) {
            return (
              <Badge
                variant="outline"
                className="text-[10px] font-semibold bg-slate-100 text-slate-700 border-slate-300 flex items-center gap-1 w-fit"
              >
                <Globe className="h-3 w-3 text-slate-500" />
                {t('pricing.rates.corporate')}
              </Badge>
            )
          }
          const branchName = branchesMap.get(branchId) ?? '—'
          return (
            <Badge
              variant="outline"
              className="text-[10px] font-semibold bg-indigo-50 text-indigo-800 border-indigo-200 flex items-center gap-1 w-fit"
            >
              <Building2 className="h-3 w-3 text-indigo-500" />
              {t('pricing.rates.branchOverride', { branch: branchName })}
            </Badge>
          )
        },
      },
      {
        accessorKey: 'unitPrice',
        header: t('pricing.rates.unitPrice'),
        cell: ({ row }) => (
          <div className="flex items-center gap-0.5 font-mono font-bold text-xs text-emerald-700 bg-emerald-50/80 px-2 py-0.5 rounded border border-emerald-200 w-fit">
            <DollarSign className="h-3 w-3 text-emerald-600" />
            <span>{row.original.unitPrice.toFixed(2)}</span>
          </div>
        ),
      },
      {
        accessorKey: 'validFrom',
        header: t('pricing.rates.validFrom'),
        cell: ({ row }) => {
          const from = row.original.validFrom
          return (
            <span className="text-xs text-slate-600 flex items-center gap-1">
              <Calendar className="h-3 w-3 text-slate-400" />
              {from ?? '—'}
            </span>
          )
        },
      },
      {
        accessorKey: 'validTo',
        header: t('pricing.rates.validTo'),
        cell: ({ row }) => {
          const to = row.original.validTo
          const isCurrent = !to
          return isCurrent ? (
            <Badge
              variant="outline"
              className="text-[10px] font-bold bg-emerald-50 text-emerald-800 border-emerald-300 flex items-center gap-1 w-fit"
            >
              <CheckCircle className="h-3 w-3 text-emerald-600" />
              {t('pricing.rates.statusCurrent')}
            </Badge>
          ) : (
            <Badge
              variant="outline"
              className="text-[10px] font-semibold bg-slate-100 text-slate-600 border-slate-300 flex items-center gap-1 w-fit"
            >
              <Clock className="h-3 w-3 text-slate-400" />
              {to}
            </Badge>
          )
        },
      },
      {
        id: 'actions',
        header: t('common.actions'),
        cell: ({ row }) => {
          const price = row.original
          const isCurrent = !price.validTo
          if (!isAdmin || !isCurrent) {
            return <span className="text-slate-400 text-xs">—</span>
          }
          return (
            <Button
              variant="outline"
              size="sm"
              className="h-7 px-2 text-xs text-rose-700 hover:bg-rose-50 border-rose-200"
              onClick={() => onClosePrice(price)}
              title={t('pricing.rates.closePrice')}
            >
              <Clock className="h-3.5 w-3.5 mr-1" />
              {t('pricing.rates.closePrice')}
            </Button>
          )
        },
      },
    ],
    [t, productsMap, branchesMap, isAdmin, onClosePrice],
  )

  const table = useReactTable({
    data: prices,
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
                    <Tag className="h-6 w-6 text-slate-300" />
                    <span>{t('pricing.rates.noPrices')}</span>
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
              {prices.length === 0 ? 0 : page * size + 1}
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
