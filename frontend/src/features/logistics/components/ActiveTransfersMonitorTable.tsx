import * as React from 'react'
import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
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
import { useActiveTransfers } from '../hooks/use-logistics.ts'
import type { ActiveTransferQueryParams } from '../types/monitor.types.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  Package,
  RefreshCw,
  Truck,
} from 'lucide-react'

export function ActiveTransfersMonitorTable() {
  const { t } = useTranslation()
  const [statusFilter, setStatusFilter] = React.useState<string>('ALL')
  const [delayedFilter, setDelayedFilter] = React.useState<string>('ALL')
  const [page, setPage] = React.useState<number>(0)
  const pageSize = 10

  const queryParams: ActiveTransferQueryParams = {
    page,
    size: pageSize,
    status: statusFilter !== 'ALL' ? statusFilter : undefined,
    delayed:
      delayedFilter === 'DELAYED'
        ? true
        : delayedFilter === 'ON_TIME'
          ? false
          : undefined,
  }

  const activeQuery = useActiveTransfers(queryParams)

  const totalPages = activeQuery.data
    ? Math.ceil(activeQuery.data.totalElements / pageSize)
    : 1

  return (
    <div className="space-y-4">
      {/* Top Filter and Status Bar */}
      <div className="flex flex-col sm:flex-row gap-3 items-start sm:items-center justify-between bg-white p-3.5 rounded-xl border border-slate-200 shadow-2xs">
        <div className="flex flex-wrap items-center gap-2.5">
          {/* Status Filter */}
          <div className="w-44">
            <Select
              value={statusFilter}
              onValueChange={(val) => {
                setStatusFilter(val)
                setPage(0)
              }}
            >
              <SelectTrigger className="text-xs h-9 bg-slate-50 border-slate-200">
                <SelectValue placeholder="Estado" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL" className="text-xs">
                  {t('logistics.monitor.allActive')}
                </SelectItem>
                <SelectItem value="REQUESTED" className="text-xs">
                  {t('transfers.statuses.REQUESTED')}
                </SelectItem>
                <SelectItem value="IN_PREPARATION" className="text-xs">
                  {t('transfers.statuses.IN_PREPARATION')}
                </SelectItem>
                <SelectItem value="IN_TRANSIT" className="text-xs">
                  {t('transfers.statuses.IN_TRANSIT')}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Delay Filter */}
          <div className="w-40">
            <Select
              value={delayedFilter}
              onValueChange={(val) => {
                setDelayedFilter(val)
                setPage(0)
              }}
            >
              <SelectTrigger className="text-xs h-9 bg-slate-50 border-slate-200">
                <SelectValue placeholder="SLA / Retraso" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL" className="text-xs">
                  {t('common.all')}
                </SelectItem>
                <SelectItem
                  value="DELAYED"
                  className="text-xs text-rose-600 font-semibold"
                >
                  {t('logistics.monitor.delayedOnly')}
                </SelectItem>
                <SelectItem
                  value="ON_TIME"
                  className="text-xs text-emerald-600"
                >
                  {t('logistics.monitor.onTimeBadge')}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>

          <Button
            variant="outline"
            size="sm"
            onClick={() => activeQuery.refetch()}
            disabled={activeQuery.isFetching}
            className="text-xs h-9 gap-1 text-slate-600"
            title={t('common.refresh')}
          >
            <RefreshCw
              className={`h-3.5 w-3.5 ${
                activeQuery.isFetching ? 'animate-spin' : ''
              }`}
            />
            <span className="hidden md:inline">{t('common.refresh')}</span>
          </Button>
        </div>

        {activeQuery.data && (
          <p className="text-xs text-slate-500 font-medium">
            {activeQuery.data.totalElements} {t('common.results')}
          </p>
        )}
      </div>

      {/* Monitor Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-2xs overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50/80 border-b border-slate-200">
            <TableRow>
              <TableHead className="text-xs font-bold text-slate-700 py-3">
                {t('transfers.transferNumber')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('transfers.status')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('transfers.originBranch')} →{' '}
                {t('transfers.destinationBranch')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('logistics.monitor.itemCount')} /{' '}
                {t('logistics.monitor.totalUnits')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('transfers.estimatedArrival')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700 text-right pr-4">
                SLA / Retraso
              </TableHead>
            </TableRow>
          </TableHeader>

          <TableBody>
            {activeQuery.isLoading && (
              <>
                {[...Array(4)].map((_, i) => (
                  <TableRow key={i}>
                    <TableCell>
                      <Skeleton className="h-4 w-24" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-5 w-20" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-4 w-44" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-4 w-28" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-4 w-32" />
                    </TableCell>
                    <TableCell className="text-right">
                      <Skeleton className="h-5 w-20 ml-auto" />
                    </TableCell>
                  </TableRow>
                ))}
              </>
            )}

            {!activeQuery.isLoading &&
              activeQuery.data?.content.length === 0 && (
                <TableRow>
                  <TableCell
                    colSpan={6}
                    className="text-center py-12 text-slate-400"
                  >
                    <Truck className="h-8 w-8 mx-auto mb-2 text-slate-300" />
                    <p className="text-sm font-semibold">
                      No active transfers currently in transit or preparation.
                    </p>
                  </TableCell>
                </TableRow>
              )}

            {!activeQuery.isLoading &&
              activeQuery.data?.content.map((transfer) => (
                <TableRow
                  key={transfer.transferExternalId}
                  className="hover:bg-slate-50/60"
                >
                  {/* Transfer Number */}
                  <TableCell className="font-mono text-xs font-bold text-slate-900">
                    {transfer.transferNumber}
                  </TableCell>

                  {/* Status Badge */}
                  <TableCell>
                    <Badge variant="outline" className="text-[10px]">
                      {transfer.status}
                    </Badge>
                  </TableCell>

                  {/* Origin -> Destination */}
                  <TableCell className="text-xs">
                    <div className="flex items-center gap-1.5 text-slate-800 font-semibold">
                      <span>{transfer.originBranch?.name ?? '—'}</span>
                      <ArrowRight className="h-3 w-3 text-slate-400 shrink-0" />
                      <span>{transfer.destinationBranch?.name ?? '—'}</span>
                    </div>
                  </TableCell>

                  {/* Item Count & Units */}
                  <TableCell className="text-xs text-slate-700">
                    <div className="flex items-center gap-1 font-medium">
                      <Package className="h-3.5 w-3.5 text-slate-400" />
                      <span>
                        {transfer.itemCount} items ({transfer.totalQuantity}{' '}
                        unid.)
                      </span>
                    </div>
                  </TableCell>

                  {/* ETA */}
                  <TableCell className="text-xs text-slate-600 font-mono">
                    {transfer.estimatedArrivalAt ? (
                      <div className="flex items-center gap-1">
                        <Clock className="h-3 w-3 text-slate-400" />
                        {new Date(transfer.estimatedArrivalAt).toLocaleString()}
                      </div>
                    ) : (
                      <span className="text-slate-400">No ETA</span>
                    )}
                  </TableCell>

                  {/* Delayed Badge */}
                  <TableCell className="text-right pr-4">
                    {transfer.isDelayed ? (
                      <Badge
                        variant="outline"
                        className="bg-rose-50 text-rose-700 border-rose-200 font-semibold text-[10px] gap-1"
                      >
                        <AlertTriangle className="h-3 w-3" />
                        {t('logistics.monitor.delayedBadge')}
                      </Badge>
                    ) : (
                      <Badge
                        variant="outline"
                        className="bg-emerald-50 text-emerald-700 border-emerald-200 text-[10px] gap-1"
                      >
                        <CheckCircle2 className="h-3 w-3" />
                        {t('logistics.monitor.onTimeBadge')}
                      </Badge>
                    )}
                  </TableCell>
                </TableRow>
              ))}
          </TableBody>
        </Table>

        {/* Pagination Footer */}
        {activeQuery.data && activeQuery.data.totalElements > 0 && (
          <div className="flex items-center justify-between px-4 py-3 border-t border-slate-200 bg-slate-50/50">
            <span className="text-xs text-slate-500">
              {t('common.pageOf', {
                page: String(page + 1),
                totalPages: String(Math.max(totalPages, 1)),
              })}
            </span>

            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.max(p - 1, 0))}
                disabled={page === 0 || activeQuery.isFetching}
                className="h-7 px-2 text-xs"
              >
                <ChevronLeft className="h-3.5 w-3.5 mr-1" />
                {t('common.previous', { defaultValue: 'Anterior' })}
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.min(p + 1, totalPages - 1))}
                disabled={page >= totalPages - 1 || activeQuery.isFetching}
                className="h-7 px-2 text-xs"
              >
                {t('common.next', { defaultValue: 'Siguiente' })}
                <ChevronRight className="h-3.5 w-3.5 ml-1" />
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
