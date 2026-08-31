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
import { useRotation } from '../hooks/use-analytics.ts'
import type { AbcClass, RotationDirection } from '../types/index.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  PieChart,
  RefreshCw,
  TrendingDown,
  TrendingUp,
} from 'lucide-react'

interface RotationViewProps {
  branchExternalId?: string | undefined
}

export function RotationView({ branchExternalId }: RotationViewProps) {
  const { t } = useTranslation()
  const [direction, setDirection] = React.useState<RotationDirection>('TOP')
  const [fromDate, setFromDate] = React.useState<string>('')
  const [toDate, setToDate] = React.useState<string>('')
  const [page, setPage] = React.useState<number>(0)
  const pageSize = 20

  const queryParams = React.useMemo(() => {
    return {
      direction,
      page,
      size: pageSize,
      ...(fromDate ? { from: new Date(fromDate).toISOString() } : {}),
      ...(toDate
        ? { to: new Date(`${toDate}T23:59:59.999Z`).toISOString() }
        : {}),
      ...(branchExternalId ? { branchExternalId } : {}),
    }
  }, [direction, page, pageSize, fromDate, toDate, branchExternalId])

  const query = useRotation(queryParams)

  const pageData = query.data
  const items = pageData?.content ?? []
  const totalElements = pageData?.totalElements ?? 0
  const totalPages = Math.ceil(totalElements / pageSize) || 1

  const handleDirectionChange = (newDir: RotationDirection) => {
    setDirection(newDir)
    setPage(0)
  }

  const handleResetFilters = () => {
    setFromDate('')
    setToDate('')
    setDirection('TOP')
    setPage(0)
  }

  const getAbcBadge = (abcClass: AbcClass) => {
    switch (abcClass) {
      case 'A':
        return (
          <Badge className="bg-emerald-600 text-white font-black text-xs px-2.5 py-0.5 rounded-md">
            {t('analytics.rotation.classA')}
          </Badge>
        )
      case 'B':
        return (
          <Badge className="bg-amber-500 text-white font-black text-xs px-2.5 py-0.5 rounded-md">
            {t('analytics.rotation.classB')}
          </Badge>
        )
      case 'C':
        return (
          <Badge className="bg-slate-600 text-white font-black text-xs px-2.5 py-0.5 rounded-md">
            {t('analytics.rotation.classC')}
          </Badge>
        )
      default:
        return <Badge variant="outline">{abcClass}</Badge>
    }
  }

  return (
    <div className="space-y-6">
      {/* Controls & Filters Header */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-2xs space-y-3">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
              <PieChart className="h-5 w-5 text-indigo-600" />
              {t('analytics.rotation.title')}
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              {t('analytics.rotation.subtitle')}
            </p>
          </div>

          {/* Direction toggle */}
          <div className="flex items-center rounded-lg border border-slate-200 bg-slate-100/90 p-0.5 shrink-0">
            <button
              type="button"
              onClick={() => handleDirectionChange('TOP')}
              className={`flex items-center gap-1.5 rounded-md px-3 py-1 text-xs font-bold transition-all ${
                direction === 'TOP'
                  ? 'bg-white text-indigo-900 shadow-2xs border border-slate-200/80'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <TrendingUp className="h-3.5 w-3.5 text-emerald-600" />
              {t('analytics.rotation.topDemand')}
            </button>
            <button
              type="button"
              onClick={() => handleDirectionChange('BOTTOM')}
              className={`flex items-center gap-1.5 rounded-md px-3 py-1 text-xs font-bold transition-all ${
                direction === 'BOTTOM'
                  ? 'bg-white text-indigo-900 shadow-2xs border border-slate-200/80'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <TrendingDown className="h-3.5 w-3.5 text-amber-600" />
              {t('analytics.rotation.bottomDemand')}
            </button>
          </div>
        </div>

        {/* Date Filters Bar */}
        <div className="flex flex-wrap items-center gap-3 pt-2 border-t border-slate-100">
          <div className="flex items-center gap-2">
            <label
              htmlFor="rot-from-date"
              className="text-xs font-semibold text-slate-600"
            >
              {t('analytics.rotation.dateFrom')}
            </label>
            <input
              id="rot-from-date"
              type="date"
              value={fromDate}
              onChange={(e) => {
                setFromDate(e.target.value)
                setPage(0)
              }}
              className="h-8 rounded-lg border border-slate-200 bg-white px-2.5 text-xs text-slate-700 shadow-2xs focus:border-indigo-500 focus:outline-none"
            />
          </div>

          <div className="flex items-center gap-2">
            <label
              htmlFor="rot-to-date"
              className="text-xs font-semibold text-slate-600"
            >
              {t('analytics.rotation.dateTo')}
            </label>
            <input
              id="rot-to-date"
              type="date"
              value={toDate}
              onChange={(e) => {
                setToDate(e.target.value)
                setPage(0)
              }}
              className="h-8 rounded-lg border border-slate-200 bg-white px-2.5 text-xs text-slate-700 shadow-2xs focus:border-indigo-500 focus:outline-none"
            />
          </div>

          {(fromDate || toDate) && (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleResetFilters}
              className="h-8 text-xs text-slate-600 hover:text-slate-900"
            >
              <RefreshCw className="h-3 w-3 mr-1" />
              {t('common.filter')}
            </Button>
          )}

          <div className="ml-auto text-xs text-slate-500 italic hidden md:block">
            {t('analytics.rotation.abcLegend')}
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

      {/* Table Content */}
      {!query.isLoading && !query.isError && (
        <>
          {items.length === 0 ? (
            <Card className="p-8 text-center bg-white border-slate-200">
              <div className="mx-auto w-12 h-12 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 mb-3">
                <PieChart className="h-6 w-6" />
              </div>
              <h3 className="text-sm font-bold text-slate-800">
                {t('analytics.rotation.emptyState')}
              </h3>
              <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
                {t('analytics.rotation.subtitle')}
              </p>
            </Card>
          ) : (
            <Card className="bg-white border-slate-200 shadow-2xs overflow-hidden">
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
                        {t('analytics.rotation.unitsSold')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('analytics.rotation.salesAmount')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('analytics.rotation.sharePercent')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('analytics.rotation.cumulativeShare')}
                      </TableHead>
                      <TableHead className="text-center text-xs font-bold text-slate-700">
                        {t('analytics.rotation.abcClass')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('analytics.rotation.coverageDays')}
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((row) => {
                      return (
                        <TableRow
                          key={row.sku}
                          className="hover:bg-slate-50/80"
                        >
                          <TableCell className="font-mono text-xs font-bold text-indigo-700">
                            {row.sku}
                          </TableCell>
                          <TableCell className="font-medium text-xs text-slate-900">
                            {row.name}
                          </TableCell>
                          <TableCell className="text-right text-xs text-slate-700 font-mono">
                            {row.unitsSold.toLocaleString('en-US', {
                              minimumFractionDigits: 2,
                              maximumFractionDigits: 2,
                            })}
                          </TableCell>
                          <TableCell className="text-right text-xs text-slate-900 font-bold font-mono">
                            $
                            {row.salesAmount.toLocaleString('en-US', {
                              minimumFractionDigits: 2,
                              maximumFractionDigits: 2,
                            })}
                          </TableCell>
                          <TableCell className="text-right text-xs text-slate-700 font-mono">
                            {row.sharePercent.toFixed(2)}%
                          </TableCell>
                          <TableCell className="text-right text-xs font-bold text-slate-900 font-mono">
                            {row.cumulativeSharePercent.toFixed(2)}%
                          </TableCell>
                          <TableCell className="text-center">
                            {getAbcBadge(row.abcClass)}
                          </TableCell>
                          <TableCell className="text-right text-xs font-mono">
                            {row.coverageDays !== null &&
                            row.coverageDays !== undefined ? (
                              row.coverageDays === 0 ? (
                                <Badge
                                  variant="outline"
                                  className="bg-rose-50 text-rose-700 border-rose-200 font-bold text-[11px]"
                                >
                                  {t('analytics.rotation.zeroStock')}
                                </Badge>
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

              {/* Pagination Controls */}
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
