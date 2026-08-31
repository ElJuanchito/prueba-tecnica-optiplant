import * as React from 'react'
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card.tsx'
import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Skeleton } from '@/components/ui/skeleton.tsx'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert.tsx'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table.tsx'
import {
  useTransferActivitySummary,
  useTransferStockImpact,
} from '../hooks/use-analytics.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  AlertCircle,
  AlertTriangle,
  ArrowDownLeft,
  ArrowLeftRight,
  ArrowUpRight,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Package,
  Truck,
} from 'lucide-react'

interface TransfersImpactViewProps {
  branchExternalId?: string | undefined
}

export function TransfersImpactView({
  branchExternalId,
}: TransfersImpactViewProps) {
  const { t } = useTranslation()
  const [page, setPage] = React.useState<number>(0)
  const pageSize = 20

  const summaryQuery = useTransferActivitySummary(
    branchExternalId ? { branchExternalId } : undefined,
  )

  const impactQuery = useTransferStockImpact({
    page,
    size: pageSize,
    ...(branchExternalId ? { branchExternalId } : {}),
  })

  const summary = summaryQuery.data
  const pageData = impactQuery.data
  const items = pageData?.content ?? []
  const totalElements = pageData?.totalElements ?? 0
  const totalPages = Math.ceil(totalElements / pageSize) || 1

  return (
    <div className="space-y-6">
      {/* View Header */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-2xs">
        <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
          <ArrowLeftRight className="h-5 w-5 text-cyan-600" />
          {t('analytics.transfers.title')}
        </h2>
        <p className="text-xs text-slate-500 mt-0.5">
          {t('analytics.transfers.subtitle')}
        </p>
      </div>

      {/* Error state */}
      {(summaryQuery.isError || impactQuery.isError) && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>{t('common.error')}</AlertTitle>
          <AlertDescription>
            {summaryQuery.error?.message || impactQuery.error?.message}
          </AlertDescription>
        </Alert>
      )}

      {/* Active Transfer Counters Summary */}
      {summaryQuery.isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Skeleton className="h-36 rounded-xl" />
          <Skeleton className="h-36 rounded-xl" />
        </div>
      ) : summary ? (
        <div className="space-y-4">
          {/* Delayed Alert Banner */}
          {summary.delayedCount > 0 ? (
            <div className="flex items-center gap-3 p-3.5 bg-rose-50 border border-rose-200 rounded-xl text-rose-900 shadow-2xs">
              <AlertTriangle className="h-5 w-5 text-rose-600 shrink-0" />
              <div>
                <h4 className="text-xs font-bold text-rose-900">
                  {t('analytics.transfers.delayedAlert')}
                </h4>
                <p className="text-xs text-rose-700 mt-0.5">
                  {t('analytics.transfers.delayedCount', {
                    count: summary.delayedCount,
                  })}
                </p>
              </div>
            </div>
          ) : (
            <div className="flex items-center gap-2.5 p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-emerald-900 shadow-2xs text-xs font-semibold">
              <CheckCircle2 className="h-4 w-4 text-emerald-600 shrink-0" />
              <span>{t('analytics.transfers.noDelayed')}</span>
            </div>
          )}

          {/* Inbound vs Outbound KPI Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Inbound Summary */}
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardHeader className="pb-2 border-b border-slate-100">
                <CardTitle className="text-xs font-bold text-cyan-800 uppercase tracking-wider flex items-center justify-between">
                  <span className="flex items-center gap-1.5">
                    <ArrowDownLeft className="h-4 w-4 text-cyan-600" />
                    {t('analytics.transfers.inboundTitle')}
                  </span>
                  <Badge
                    variant="outline"
                    className="bg-cyan-50 text-cyan-700 border-cyan-200"
                  >
                    {summary.inbound.requested +
                      summary.inbound.inPreparation +
                      summary.inbound.inTransit}{' '}
                    {t('common.active').toLowerCase()}
                  </Badge>
                </CardTitle>
              </CardHeader>
              <CardContent className="pt-4 grid grid-cols-3 gap-2 text-center">
                <div className="p-2 bg-slate-50 rounded-lg">
                  <div className="text-xs font-medium text-slate-500">
                    {t('analytics.transfers.requested')}
                  </div>
                  <div className="text-lg font-black text-slate-800 mt-1">
                    {summary.inbound.requested}
                  </div>
                </div>
                <div className="p-2 bg-slate-50 rounded-lg">
                  <div className="text-xs font-medium text-slate-500">
                    {t('analytics.transfers.inPreparation')}
                  </div>
                  <div className="text-lg font-black text-amber-700 mt-1">
                    {summary.inbound.inPreparation}
                  </div>
                </div>
                <div className="p-2 bg-slate-50 rounded-lg">
                  <div className="text-xs font-medium text-slate-500">
                    {t('analytics.transfers.inTransit')}
                  </div>
                  <div className="text-lg font-black text-cyan-700 mt-1">
                    {summary.inbound.inTransit}
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* Outbound Summary */}
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardHeader className="pb-2 border-b border-slate-100">
                <CardTitle className="text-xs font-bold text-indigo-800 uppercase tracking-wider flex items-center justify-between">
                  <span className="flex items-center gap-1.5">
                    <ArrowUpRight className="h-4 w-4 text-indigo-600" />
                    {t('analytics.transfers.outboundTitle')}
                  </span>
                  <Badge
                    variant="outline"
                    className="bg-indigo-50 text-indigo-700 border-indigo-200"
                  >
                    {summary.outbound.requested +
                      summary.outbound.inPreparation +
                      summary.outbound.inTransit}{' '}
                    {t('common.active').toLowerCase()}
                  </Badge>
                </CardTitle>
              </CardHeader>
              <CardContent className="pt-4 grid grid-cols-3 gap-2 text-center">
                <div className="p-2 bg-slate-50 rounded-lg">
                  <div className="text-xs font-medium text-slate-500">
                    {t('analytics.transfers.requested')}
                  </div>
                  <div className="text-lg font-black text-slate-800 mt-1">
                    {summary.outbound.requested}
                  </div>
                </div>
                <div className="p-2 bg-slate-50 rounded-lg">
                  <div className="text-xs font-medium text-slate-500">
                    {t('analytics.transfers.inPreparation')}
                  </div>
                  <div className="text-lg font-black text-amber-700 mt-1">
                    {summary.outbound.inPreparation}
                  </div>
                </div>
                <div className="p-2 bg-slate-50 rounded-lg">
                  <div className="text-xs font-medium text-slate-500">
                    {t('analytics.transfers.inTransit')}
                  </div>
                  <div className="text-lg font-black text-indigo-700 mt-1">
                    {summary.outbound.inTransit}
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      ) : null}

      {/* Stock Impact Table */}
      <Card className="bg-white border-slate-200 shadow-2xs overflow-hidden">
        <CardHeader className="border-b border-slate-100 pb-3">
          <CardTitle className="text-sm font-bold text-slate-900 flex items-center justify-between">
            <span className="flex items-center gap-2">
              <Package className="h-4 w-4 text-cyan-600" />
              {t('analytics.transfers.stockImpactTitle')}
            </span>
            <Badge
              variant="outline"
              className="font-semibold text-xs text-slate-600"
            >
              {totalElements} {t('analytics.rotation.product').toLowerCase()}s
            </Badge>
          </CardTitle>
        </CardHeader>

        {impactQuery.isLoading ? (
          <div className="p-6">
            <Skeleton className="h-48 w-full" />
          </div>
        ) : items.length === 0 ? (
          <div className="p-8 text-center">
            <div className="mx-auto w-10 h-10 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 mb-2">
              <Truck className="h-5 w-5" />
            </div>
            <h4 className="text-xs font-bold text-slate-700">
              {t('analytics.transfers.emptyImpact')}
            </h4>
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <Table>
                <TableHeader className="bg-slate-50">
                  <TableRow>
                    <TableHead className="w-24 text-xs font-bold text-slate-700">
                      {t('analytics.rotation.sku')}
                    </TableHead>
                    <TableHead className="text-xs font-bold text-slate-700">
                      {t('analytics.rotation.product')}
                    </TableHead>
                    <TableHead className="text-right text-xs font-bold text-slate-700">
                      {t('analytics.transfers.currentStock')}
                    </TableHead>
                    <TableHead className="text-right text-xs font-bold text-slate-700">
                      {t('analytics.transfers.inTransitStock')}
                    </TableHead>
                    <TableHead className="text-right text-xs font-bold text-emerald-700">
                      {t('analytics.transfers.inboundInTransit')}
                    </TableHead>
                    <TableHead className="text-right text-xs font-bold text-rose-700">
                      {t('analytics.transfers.outboundCommitted')}
                    </TableHead>
                    <TableHead className="text-right text-xs font-bold text-cyan-900 bg-cyan-50/50">
                      {t('analytics.transfers.projectedStock')}
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((row) => (
                    <TableRow key={row.sku} className="hover:bg-slate-50/80">
                      <TableCell className="font-mono text-xs font-bold text-cyan-800">
                        {row.sku}
                      </TableCell>
                      <TableCell className="font-medium text-xs text-slate-900">
                        {row.name}
                      </TableCell>
                      <TableCell className="text-right text-xs font-mono font-semibold text-slate-800">
                        {row.currentStock.toLocaleString('en-US', {
                          minimumFractionDigits: 2,
                          maximumFractionDigits: 2,
                        })}
                      </TableCell>
                      <TableCell className="text-right text-xs font-mono text-slate-600">
                        {row.inTransitStock.toLocaleString('en-US', {
                          minimumFractionDigits: 2,
                          maximumFractionDigits: 2,
                        })}
                      </TableCell>
                      <TableCell className="text-right text-xs font-mono font-bold text-emerald-700">
                        +
                        {row.inboundInTransit.toLocaleString('en-US', {
                          minimumFractionDigits: 2,
                          maximumFractionDigits: 2,
                        })}
                      </TableCell>
                      <TableCell className="text-right text-xs font-mono font-bold text-rose-700">
                        -
                        {row.outboundCommitted.toLocaleString('en-US', {
                          minimumFractionDigits: 2,
                          maximumFractionDigits: 2,
                        })}
                      </TableCell>
                      <TableCell className="text-right text-xs font-mono font-black text-cyan-950 bg-cyan-50/40">
                        <Badge
                          variant="outline"
                          className={`font-mono text-xs ${
                            row.projectedStock < 0
                              ? 'bg-rose-50 text-rose-800 border-rose-200'
                              : 'bg-cyan-50 text-cyan-900 border-cyan-200'
                          }`}
                        >
                          {row.projectedStock.toLocaleString('en-US', {
                            minimumFractionDigits: 2,
                            maximumFractionDigits: 2,
                          })}
                        </Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            {/* Pagination */}
            <div className="flex items-center justify-between px-4 py-3 border-t border-slate-100 bg-slate-50/50">
              <div className="text-xs text-slate-500">
                {t('common.showing')}{' '}
                <span className="font-bold text-slate-700">{items.length}</span>{' '}
                {t('common.of')}{' '}
                <span className="font-bold text-slate-700">
                  {totalElements}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page <= 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="h-8 px-2 text-xs"
                >
                  <ChevronLeft className="h-4 w-4 mr-1" />
                  {t('common.previous')}
                </Button>
                <span className="text-xs font-semibold text-slate-600">
                  {t('common.pageOf', { page: page + 1, totalPages })}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  className="h-8 px-2 text-xs"
                >
                  {t('common.next')}
                  <ChevronRight className="h-4 w-4 ml-1" />
                </Button>
              </div>
            </div>
          </>
        )}
      </Card>
    </div>
  )
}
