import * as React from 'react'
import { Card } from '@/components/ui/card.tsx'
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
import { useReplenishment } from '../hooks/use-analytics.ts'
import type {
  ReplenishmentSeverity,
  ReplenishmentSort,
} from '../types/index.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  AlertCircle,
  AlertOctagon,
  AlertTriangle,
  ArrowUpDown,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Filter,
  ShieldAlert,
} from 'lucide-react'

interface ReplenishmentPanelViewProps {
  branchExternalId?: string | undefined
}

export function ReplenishmentPanelView({
  branchExternalId,
}: ReplenishmentPanelViewProps) {
  const { t } = useTranslation()
  const [severityFilter, setSeverityFilter] = React.useState<
    ReplenishmentSeverity | ''
  >('')
  const [sortField, setSortField] =
    React.useState<ReplenishmentSort>('severity')
  const [page, setPage] = React.useState<number>(0)
  const pageSize = 20

  const queryParams = React.useMemo(() => {
    return {
      ...(severityFilter ? { severity: severityFilter } : {}),
      sort: sortField,
      page,
      size: pageSize,
      ...(branchExternalId ? { branchExternalId } : {}),
    }
  }, [severityFilter, sortField, page, pageSize, branchExternalId])

  const query = useReplenishment(queryParams)
  const pageData = query.data
  const items = pageData?.content ?? []
  const totalElements = pageData?.totalElements ?? 0
  const totalPages = Math.ceil(totalElements / pageSize) || 1

  const getSeverityBadge = (severity: ReplenishmentSeverity) => {
    if (severity === 'OUT_OF_STOCK') {
      return (
        <Badge className="bg-rose-600 hover:bg-rose-700 text-white font-black text-[11px] px-2 py-0.5 rounded-md flex items-center gap-1 w-fit">
          <AlertOctagon className="h-3 w-3" />
          {t('analytics.replenishment.outOfStockBadge')}
        </Badge>
      )
    }
    return (
      <Badge className="bg-amber-500 hover:bg-amber-600 text-white font-black text-[11px] px-2 py-0.5 rounded-md flex items-center gap-1 w-fit">
        <AlertTriangle className="h-3 w-3" />
        {t('analytics.replenishment.criticalBadge')}
      </Badge>
    )
  }

  return (
    <div className="space-y-6">
      {/* View Header & Filters */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-2xs space-y-3">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
              <ShieldAlert className="h-5 w-5 text-rose-600" />
              {t('analytics.replenishment.title')}
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              {t('analytics.replenishment.subtitle')}
            </p>
          </div>
        </div>

        {/* Filter Controls Bar */}
        <div className="flex flex-wrap items-center gap-3 pt-2 border-t border-slate-100">
          {/* Severity Filter */}
          <div className="flex items-center gap-2">
            <label
              htmlFor="replenish-sev"
              className="text-xs font-semibold text-slate-600 flex items-center gap-1"
            >
              <Filter className="h-3.5 w-3.5 text-slate-400" />
              {t('analytics.replenishment.filterSeverity')}
            </label>
            <select
              id="replenish-sev"
              value={severityFilter}
              onChange={(e) => {
                setSeverityFilter(e.target.value as ReplenishmentSeverity | '')
                setPage(0)
              }}
              className="h-8 rounded-lg border border-slate-200 bg-white px-2.5 text-xs text-slate-700 shadow-2xs focus:border-indigo-500 focus:outline-none"
            >
              <option value="">
                {t('analytics.replenishment.allSeverities')}
              </option>
              <option value="OUT_OF_STOCK">
                {t('analytics.replenishment.outOfStock')}
              </option>
              <option value="CRITICAL">
                {t('analytics.replenishment.critical')}
              </option>
            </select>
          </div>

          {/* Sort By Filter */}
          <div className="flex items-center gap-2">
            <label
              htmlFor="replenish-sort"
              className="text-xs font-semibold text-slate-600 flex items-center gap-1"
            >
              <ArrowUpDown className="h-3.5 w-3.5 text-slate-400" />
              {t('analytics.replenishment.sortBy')}
            </label>
            <select
              id="replenish-sort"
              value={sortField}
              onChange={(e) => {
                setSortField(e.target.value as ReplenishmentSort)
                setPage(0)
              }}
              className="h-8 rounded-lg border border-slate-200 bg-white px-2.5 text-xs text-slate-700 shadow-2xs focus:border-indigo-500 focus:outline-none"
            >
              <option value="severity">
                {t('analytics.replenishment.sortSeverity')}
              </option>
              <option value="product">
                {t('analytics.replenishment.sortProduct')}
              </option>
              <option value="coverage">
                {t('analytics.replenishment.sortCoverage')}
              </option>
            </select>
          </div>
        </div>
      </div>

      {/* Error state */}
      {query.isError && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>{t('common.error')}</AlertTitle>
          <AlertDescription>
            {t(`analytics.errors.${query.error.message}`) !==
            `analytics.errors.${query.error.message}`
              ? t(`analytics.errors.${query.error.message}`)
              : query.error.message}
          </AlertDescription>
        </Alert>
      )}

      {/* Loading state */}
      {query.isLoading && (
        <Card className="p-6">
          <Skeleton className="h-6 w-48 mb-4" />
          <Skeleton className="h-64 w-full" />
        </Card>
      )}

      {/* Main Table */}
      {!query.isLoading && !query.isError && (
        <>
          {items.length === 0 ? (
            <Card className="p-8 text-center bg-white border-slate-200">
              <div className="mx-auto w-12 h-12 rounded-full bg-emerald-50 flex items-center justify-center text-emerald-600 mb-3">
                <CheckCircle2 className="h-6 w-6" />
              </div>
              <h3 className="text-sm font-bold text-slate-800">
                {t('analytics.replenishment.emptyState')}
              </h3>
              <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
                {t('analytics.replenishment.subtitle')}
              </p>
            </Card>
          ) : (
            <Card className="bg-white border-slate-200 shadow-2xs overflow-hidden">
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader className="bg-slate-50">
                    <TableRow>
                      <TableHead className="w-36 text-xs font-bold text-slate-700">
                        {t('analytics.replenishment.severity')}
                      </TableHead>
                      <TableHead className="w-28 text-xs font-bold text-slate-700">
                        {t('analytics.rotation.sku')}
                      </TableHead>
                      <TableHead className="text-xs font-bold text-slate-700">
                        {t('analytics.rotation.product')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('analytics.transfers.currentStock')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('analytics.replenishment.minThreshold')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('analytics.rotation.coverageDays')}
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((row) => {
                      const isOutOfStock = row.severity === 'OUT_OF_STOCK'
                      return (
                        <TableRow
                          key={row.sku}
                          className={
                            isOutOfStock
                              ? 'bg-rose-50/30 hover:bg-rose-50/50'
                              : 'hover:bg-slate-50/80'
                          }
                        >
                          <TableCell>
                            {getSeverityBadge(row.severity)}
                          </TableCell>
                          <TableCell className="font-mono text-xs font-bold text-rose-800">
                            {row.sku}
                          </TableCell>
                          <TableCell className="font-medium text-xs text-slate-900">
                            {row.name}
                          </TableCell>
                          <TableCell className="text-right text-xs font-mono font-black">
                            <span
                              className={
                                isOutOfStock
                                  ? 'text-rose-700 bg-rose-100 px-2 py-0.5 rounded'
                                  : 'text-amber-800'
                              }
                            >
                              {row.currentStock.toLocaleString('en-US', {
                                minimumFractionDigits: 2,
                                maximumFractionDigits: 2,
                              })}
                            </span>
                          </TableCell>
                          <TableCell className="text-right text-xs font-mono text-slate-700">
                            {row.minStockThreshold.toLocaleString('en-US', {
                              minimumFractionDigits: 2,
                              maximumFractionDigits: 2,
                            })}
                          </TableCell>
                          <TableCell className="text-right text-xs font-mono">
                            {row.coverageDays !== null &&
                            row.coverageDays !== undefined ? (
                              row.coverageDays === 0 ? (
                                <span className="font-bold text-rose-700">
                                  0 d
                                </span>
                              ) : (
                                <span className="font-semibold text-slate-800">
                                  {row.coverageDays.toFixed(1)} d
                                </span>
                              )
                            ) : (
                              <span className="text-slate-400 italic">
                                {t('analytics.rotation.infiniteCoverage')}
                              </span>
                            )}
                          </TableCell>
                        </TableRow>
                      )
                    })}
                  </TableBody>
                </Table>
              </div>

              {/* Pagination */}
              <div className="flex items-center justify-between px-4 py-3 border-t border-slate-100 bg-slate-50/50">
                <div className="text-xs text-slate-500">
                  {t('common.showing')}{' '}
                  <span className="font-bold text-slate-700">
                    {items.length}
                  </span>{' '}
                  {t('common.of')}{' '}
                  <span className="font-bold text-slate-700">
                    {totalElements}
                  </span>{' '}
                  {t('analytics.rotation.product').toLowerCase()}s
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
            </Card>
          )}
        </>
      )}
    </div>
  )
}
