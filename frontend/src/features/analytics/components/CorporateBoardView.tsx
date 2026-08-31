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
import { useCorporateBoard } from '../hooks/use-analytics.ts'
import type { CorporateSortField, SortDirection } from '../types/index.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { Permissions } from '@/lib/permissions.ts'
import {
  AlertCircle,
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  ChevronLeft,
  ChevronRight,
  DollarSign,
  Globe2,
  Lock,
  Package,
  ShieldAlert,
  TrendingUp,
} from 'lucide-react'

export function CorporateBoardView() {
  const { t } = useTranslation()
  const sessionQuery = useSession()
  const role = sessionQuery.data?.role
  const isUserAdmin = Permissions.canAccessCorporateAnalytics(role)

  const currentDate = new Date()
  const [year, setYear] = React.useState<number>(currentDate.getFullYear())
  const [month, setMonth] = React.useState<number>(currentDate.getMonth() + 1)
  const [sortField, setSortField] =
    React.useState<CorporateSortField>('salesAmount')
  const [sortDirection, setSortDirection] =
    React.useState<SortDirection>('DESC')
  const [page, setPage] = React.useState<number>(0)
  const pageSize = 20

  const queryParams = React.useMemo(() => {
    return {
      year,
      month,
      sort: sortField,
      direction: sortDirection,
      page,
      size: pageSize,
    }
  }, [year, month, sortField, sortDirection, page, pageSize])

  const query = useCorporateBoard(queryParams, isUserAdmin)

  const pageData = query.data
  const items = pageData?.content ?? []
  const totalElements = pageData?.totalElements ?? 0
  const totalPages = Math.ceil(totalElements / pageSize) || 1

  // Aggregated totals across all returned branches on current page
  const totals = React.useMemo(() => {
    return items.reduce(
      (acc, b) => ({
        salesAmount: acc.salesAmount + b.salesAmount,
        salesCount: acc.salesCount + b.salesCount,
        unitsSold: acc.unitsSold + b.unitsSold,
        inventoryValue: acc.inventoryValue + b.inventoryValue,
        criticalProducts: acc.criticalProducts + b.criticalProductCount,
        activeTransfers: acc.activeTransfers + b.activeTransferCount,
      }),
      {
        salesAmount: 0,
        salesCount: 0,
        unitsSold: 0,
        inventoryValue: 0,
        criticalProducts: 0,
        activeTransfers: 0,
      },
    )
  }, [items])

  const handleSort = (field: CorporateSortField) => {
    if (sortField === field) {
      setSortDirection((prev) => (prev === 'ASC' ? 'DESC' : 'ASC'))
    } else {
      setSortField(field)
      setSortDirection('DESC')
    }
    setPage(0)
  }

  const renderSortIndicator = (field: CorporateSortField) => {
    if (sortField !== field) {
      return (
        <ArrowUpDown className="h-3 w-3 ml-1 text-slate-300 group-hover:text-slate-500 inline" />
      )
    }
    return sortDirection === 'ASC' ? (
      <ArrowUp className="h-3.5 w-3.5 ml-1 text-indigo-600 inline" />
    ) : (
      <ArrowDown className="h-3.5 w-3.5 ml-1 text-indigo-600 inline" />
    )
  }

  // If not admin, render friendly permission notice
  if (!isUserAdmin) {
    return (
      <Card className="p-8 text-center bg-slate-50 border-slate-200">
        <div className="mx-auto w-12 h-12 rounded-full bg-amber-100 flex items-center justify-center text-amber-700 mb-3">
          <Lock className="h-6 w-6" />
        </div>
        <h3 className="text-base font-bold text-slate-800">
          {t('analytics.corporateBoard.title')}
        </h3>
        <p className="text-xs text-slate-600 mt-1 max-w-md mx-auto">
          {t('analytics.corporateBoard.adminOnlyNotice')}
        </p>
      </Card>
    )
  }

  const monthNames = [
    'Enero',
    'Febrero',
    'Marzo',
    'Abril',
    'Mayo',
    'Junio',
    'Julio',
    'Agosto',
    'Septiembre',
    'Octubre',
    'Noviembre',
    'Diciembre',
  ]

  return (
    <div className="space-y-6">
      {/* Header & Date Selector */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-white p-4 rounded-xl border border-slate-200 shadow-2xs">
        <div>
          <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
            <Globe2 className="h-5 w-5 text-indigo-600" />
            {t('analytics.corporateBoard.title')}
          </h2>
          <p className="text-xs text-slate-500 mt-0.5">
            {t('analytics.corporateBoard.subtitle')}
          </p>
        </div>

        <div className="flex items-center gap-3 shrink-0">
          {/* Year select */}
          <div className="flex items-center gap-1.5">
            <label
              htmlFor="corp-year"
              className="text-xs font-semibold text-slate-600"
            >
              {t('analytics.corporateBoard.year')}
            </label>
            <select
              id="corp-year"
              value={year}
              onChange={(e) => {
                setYear(Number(e.target.value))
                setPage(0)
              }}
              className="h-8 rounded-lg border border-slate-200 bg-white px-2 text-xs font-semibold text-slate-700 shadow-2xs focus:border-indigo-500 focus:outline-none"
            >
              {[
                currentDate.getFullYear() - 1,
                currentDate.getFullYear(),
                currentDate.getFullYear() + 1,
              ].map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
          </div>

          {/* Month select */}
          <div className="flex items-center gap-1.5">
            <label
              htmlFor="corp-month"
              className="text-xs font-semibold text-slate-600"
            >
              {t('analytics.corporateBoard.month')}
            </label>
            <select
              id="corp-month"
              value={month}
              onChange={(e) => {
                setMonth(Number(e.target.value))
                setPage(0)
              }}
              className="h-8 rounded-lg border border-slate-200 bg-white px-2 text-xs font-semibold text-slate-700 shadow-2xs focus:border-indigo-500 focus:outline-none"
            >
              {monthNames.map((name, index) => (
                <option key={name} value={index + 1}>
                  {name}
                </option>
              ))}
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
        <div className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <Card key={`skeleton-corp-${i}`} className="p-4">
                <Skeleton className="h-4 w-24 mb-3" />
                <Skeleton className="h-8 w-32 mb-2" />
                <Skeleton className="h-3 w-20" />
              </Card>
            ))}
          </div>
          <Card className="p-6">
            <Skeleton className="h-48 w-full" />
          </Card>
        </div>
      )}

      {/* Main Content */}
      {!query.isLoading && !query.isError && (
        <>
          {/* Corporate Network Summary KPI Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* Total Corporate Sales */}
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardHeader className="pb-2">
                <CardTitle className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center justify-between">
                  <span>{t('analytics.corporateBoard.salesAmount')}</span>
                  <DollarSign className="h-4 w-4 text-emerald-600" />
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-black text-slate-900">
                  $
                  {totals.salesAmount.toLocaleString('en-US', {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2,
                  })}
                </div>
                <p className="text-xs text-slate-500 mt-1">
                  {totals.salesCount.toLocaleString()}{' '}
                  {t('analytics.corporateBoard.salesCount').toLowerCase()}
                </p>
              </CardContent>
            </Card>

            {/* Total Units Sold */}
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardHeader className="pb-2">
                <CardTitle className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center justify-between">
                  <span>{t('analytics.corporateBoard.unitsSold')}</span>
                  <Package className="h-4 w-4 text-indigo-600" />
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-black text-slate-900">
                  {totals.unitsSold.toLocaleString('en-US', {
                    maximumFractionDigits: 2,
                  })}
                </div>
                <p className="text-xs text-slate-500 mt-1">
                  {items.length} {t('iam.branches').toLowerCase()}
                </p>
              </CardContent>
            </Card>

            {/* Total Inventory Valuation */}
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardHeader className="pb-2">
                <CardTitle className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center justify-between">
                  <span>{t('analytics.corporateBoard.inventoryValue')}</span>
                  <TrendingUp className="h-4 w-4 text-amber-600" />
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-black text-slate-900">
                  $
                  {totals.inventoryValue.toLocaleString('en-US', {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2,
                  })}
                </div>
                <p className="text-xs text-slate-500 mt-1">
                  {t('analytics.corporateScopeNotice')}
                </p>
              </CardContent>
            </Card>

            {/* Critical Products across Network */}
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardHeader className="pb-2">
                <CardTitle className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center justify-between">
                  <span>{t('analytics.corporateBoard.criticalProducts')}</span>
                  <ShieldAlert className="h-4 w-4 text-rose-600" />
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-black text-rose-700">
                  {totals.criticalProducts.toLocaleString()}
                </div>
                <p className="text-xs text-slate-500 mt-1">
                  {totals.activeTransfers.toLocaleString()}{' '}
                  {t('analytics.corporateBoard.activeTransfers').toLowerCase()}
                </p>
              </CardContent>
            </Card>
          </div>

          {/* Comparative Table */}
          {items.length === 0 ? (
            <Card className="p-8 text-center bg-white border-slate-200">
              <div className="mx-auto w-12 h-12 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 mb-3">
                <Globe2 className="h-6 w-6" />
              </div>
              <h3 className="text-sm font-bold text-slate-800">
                {t('analytics.corporateBoard.emptyState')}
              </h3>
            </Card>
          ) : (
            <Card className="bg-white border-slate-200 shadow-2xs overflow-hidden">
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader className="bg-slate-50">
                    <TableRow>
                      <TableHead
                        onClick={() => handleSort('code')}
                        className="w-24 text-xs font-bold text-slate-700 cursor-pointer hover:bg-slate-100/80 select-none group"
                      >
                        {t('analytics.corporateBoard.branchCode')}
                        {renderSortIndicator('code')}
                      </TableHead>
                      <TableHead
                        onClick={() => handleSort('name')}
                        className="text-xs font-bold text-slate-700 cursor-pointer hover:bg-slate-100/80 select-none group"
                      >
                        {t('analytics.corporateBoard.branchName')}
                        {renderSortIndicator('name')}
                      </TableHead>
                      <TableHead
                        onClick={() => handleSort('salesAmount')}
                        className="text-right text-xs font-bold text-slate-700 cursor-pointer hover:bg-slate-100/80 select-none group"
                      >
                        {t('analytics.corporateBoard.salesAmount')}
                        {renderSortIndicator('salesAmount')}
                      </TableHead>
                      <TableHead
                        onClick={() => handleSort('salesCount')}
                        className="text-right text-xs font-bold text-slate-700 cursor-pointer hover:bg-slate-100/80 select-none group"
                      >
                        {t('analytics.corporateBoard.salesCount')}
                        {renderSortIndicator('salesCount')}
                      </TableHead>
                      <TableHead
                        onClick={() => handleSort('unitsSold')}
                        className="text-right text-xs font-bold text-slate-700 cursor-pointer hover:bg-slate-100/80 select-none group"
                      >
                        {t('analytics.corporateBoard.unitsSold')}
                        {renderSortIndicator('unitsSold')}
                      </TableHead>
                      <TableHead
                        onClick={() => handleSort('inventoryValue')}
                        className="text-right text-xs font-bold text-slate-700 cursor-pointer hover:bg-slate-100/80 select-none group"
                      >
                        {t('analytics.corporateBoard.inventoryValue')}
                        {renderSortIndicator('inventoryValue')}
                      </TableHead>
                      <TableHead
                        onClick={() => handleSort('criticalProductCount')}
                        className="text-right text-xs font-bold text-slate-700 cursor-pointer hover:bg-slate-100/80 select-none group"
                      >
                        {t('analytics.corporateBoard.criticalProducts')}
                        {renderSortIndicator('criticalProductCount')}
                      </TableHead>
                      <TableHead
                        onClick={() => handleSort('activeTransferCount')}
                        className="text-right text-xs font-bold text-slate-700 cursor-pointer hover:bg-slate-100/80 select-none group"
                      >
                        {t('analytics.corporateBoard.activeTransfers')}
                        {renderSortIndicator('activeTransferCount')}
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((row) => (
                      <TableRow key={row.code} className="hover:bg-slate-50/80">
                        <TableCell className="font-mono text-xs font-bold text-indigo-700">
                          {row.code}
                        </TableCell>
                        <TableCell className="font-semibold text-xs text-slate-900">
                          {row.name}
                        </TableCell>
                        <TableCell className="text-right text-xs font-mono font-bold text-slate-900">
                          $
                          {row.salesAmount.toLocaleString('en-US', {
                            minimumFractionDigits: 2,
                            maximumFractionDigits: 2,
                          })}
                        </TableCell>
                        <TableCell className="text-right text-xs font-mono text-slate-700">
                          {row.salesCount.toLocaleString()}
                        </TableCell>
                        <TableCell className="text-right text-xs font-mono text-slate-700">
                          {row.unitsSold.toLocaleString('en-US', {
                            minimumFractionDigits: 2,
                            maximumFractionDigits: 2,
                          })}
                        </TableCell>
                        <TableCell className="text-right text-xs font-mono font-bold text-emerald-800">
                          $
                          {row.inventoryValue.toLocaleString('en-US', {
                            minimumFractionDigits: 2,
                            maximumFractionDigits: 2,
                          })}
                        </TableCell>
                        <TableCell className="text-right text-xs font-mono">
                          {row.criticalProductCount > 0 ? (
                            <Badge
                              variant="outline"
                              className="bg-rose-50 text-rose-700 border-rose-200 font-bold text-[11px]"
                            >
                              {row.criticalProductCount}
                            </Badge>
                          ) : (
                            <span className="text-slate-400">0</span>
                          )}
                        </TableCell>
                        <TableCell className="text-right text-xs font-mono">
                          {row.activeTransferCount > 0 ? (
                            <Badge
                              variant="outline"
                              className="bg-cyan-50 text-cyan-700 border-cyan-200 font-bold text-[11px]"
                            >
                              {row.activeTransferCount}
                            </Badge>
                          ) : (
                            <span className="text-slate-400">0</span>
                          )}
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
                  <span className="font-bold text-slate-700">
                    {items.length}
                  </span>{' '}
                  {t('common.of')}{' '}
                  <span className="font-bold text-slate-700">
                    {totalElements}
                  </span>{' '}
                  {t('iam.branches').toLowerCase()}
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
