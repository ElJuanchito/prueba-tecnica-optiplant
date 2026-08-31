import * as React from 'react'
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card.tsx'
import { Badge } from '@/components/ui/badge.tsx'
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
import { useSalesTrend } from '../hooks/use-analytics.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  AlertCircle,
  ArrowDownRight,
  ArrowUpRight,
  BarChart2,
  Calendar,
  DollarSign,
  Minus,
  Package,
  ShoppingCart,
} from 'lucide-react'

interface SalesTrendViewProps {
  branchExternalId?: string | undefined
}

export function SalesTrendView({ branchExternalId }: SalesTrendViewProps) {
  const { t } = useTranslation()
  const [months, setMonths] = React.useState<number>(4)

  const query = useSalesTrend({
    months,
    ...(branchExternalId ? { branchExternalId } : {}),
  })

  const trendData = query.data
  const monthlyList = trendData?.months ?? []
  const momVariation = trendData?.monthOverMonthVariationPercent
  const isEmpty = trendData?.empty || monthlyList.length === 0

  // Calculate totals over the window
  const totals = React.useMemo(() => {
    return monthlyList.reduce(
      (acc, m) => ({
        revenue: acc.revenue + m.totalAmount,
        sales: acc.sales + m.salesCount,
        units: acc.units + m.unitsSold,
      }),
      { revenue: 0, sales: 0, units: 0 },
    )
  }, [monthlyList])

  // Current month (last item in chronological order)
  const currentMonthData =
    monthlyList.length > 0 ? monthlyList[monthlyList.length - 1] : null

  // Max revenue for bar scale
  const maxRevenue = React.useMemo(() => {
    const max = Math.max(...monthlyList.map((m) => m.totalAmount), 0)
    return max > 0 ? max : 1
  }, [monthlyList])

  const monthNames = [
    'Ene',
    'Feb',
    'Mar',
    'Abr',
    'May',
    'Jun',
    'Jul',
    'Ago',
    'Sep',
    'Oct',
    'Nov',
    'Dic',
  ]

  const formatPeriod = (year: number, month: number) => {
    const name = monthNames[month - 1] || `M${month}`
    return `${name} ${year}`
  }

  return (
    <div className="space-y-6">
      {/* Filters Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-white p-4 rounded-xl border border-slate-200 shadow-2xs">
        <div>
          <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
            <BarChart2 className="h-5 w-5 text-indigo-600" />
            {t('analytics.salesTrend.title')}
          </h2>
          <p className="text-xs text-slate-500 mt-0.5">
            {t('analytics.salesTrend.subtitle')}
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <label
            htmlFor="months-select"
            className="text-xs font-semibold text-slate-600 flex items-center gap-1.5"
          >
            <Calendar className="h-3.5 w-3.5 text-slate-400" />
            {t('analytics.salesTrend.monthsFilter')}
          </label>
          <select
            id="months-select"
            value={months}
            onChange={(e) => setMonths(Number(e.target.value))}
            className="h-8 rounded-lg border border-slate-200 bg-white px-2.5 text-xs font-semibold text-slate-700 shadow-2xs focus:border-indigo-500 focus:outline-none"
          >
            <option value={3}>
              {t('analytics.salesTrend.monthCount', { count: 3 })}
            </option>
            <option value={4}>
              {t('analytics.salesTrend.monthCount', { count: 4 })}
            </option>
            <option value={6}>
              {t('analytics.salesTrend.monthCount', { count: 6 })}
            </option>
            <option value={12}>
              {t('analytics.salesTrend.monthCount', { count: 12 })}
            </option>
          </select>
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
              <Card key={`skeleton-card-${i}`} className="p-4">
                <Skeleton className="h-4 w-24 mb-3" />
                <Skeleton className="h-8 w-32 mb-2" />
                <Skeleton className="h-3 w-20" />
              </Card>
            ))}
          </div>
          <Card className="p-6">
            <Skeleton className="h-6 w-48 mb-4" />
            <Skeleton className="h-48 w-full" />
          </Card>
        </div>
      )}

      {/* Main Content */}
      {!query.isLoading && !query.isError && (
        <>
          {/* KPI Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* Current Month Revenue */}
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardHeader className="pb-2">
                <CardTitle className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center justify-between">
                  <span>{t('analytics.salesTrend.currentMonth')}</span>
                  <DollarSign className="h-4 w-4 text-emerald-600" />
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-black text-slate-900">
                  $
                  {currentMonthData
                    ? currentMonthData.totalAmount.toLocaleString('en-US', {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2,
                      })
                    : '0.00'}
                </div>
                <p className="text-xs text-slate-500 mt-1">
                  {currentMonthData
                    ? formatPeriod(
                        currentMonthData.year,
                        currentMonthData.month,
                      )
                    : '-'}
                </p>
              </CardContent>
            </Card>

            {/* MoM Variation Badge Card */}
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardHeader className="pb-2">
                <CardTitle className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center justify-between">
                  <span>{t('analytics.salesTrend.momVariation')}</span>
                  {momVariation !== null &&
                    momVariation !== undefined &&
                    momVariation > 0 && (
                      <ArrowUpRight className="h-4 w-4 text-emerald-600" />
                    )}
                  {momVariation !== null &&
                    momVariation !== undefined &&
                    momVariation < 0 && (
                      <ArrowDownRight className="h-4 w-4 text-rose-600" />
                    )}
                  {(momVariation === null || momVariation === 0) && (
                    <Minus className="h-4 w-4 text-slate-400" />
                  )}
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="flex items-center gap-2">
                  {momVariation !== null && momVariation !== undefined ? (
                    <Badge
                      variant="outline"
                      className={`text-sm font-bold px-2.5 py-0.5 rounded-md ${
                        momVariation > 0
                          ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                          : momVariation < 0
                            ? 'bg-rose-50 text-rose-700 border-rose-200'
                            : 'bg-slate-50 text-slate-700 border-slate-200'
                      }`}
                    >
                      {momVariation > 0
                        ? `+${momVariation.toFixed(1)}%`
                        : `${momVariation.toFixed(1)}%`}
                    </Badge>
                  ) : (
                    <span className="text-xs font-medium text-slate-500">
                      {t('analytics.salesTrend.noPriorData')}
                    </span>
                  )}
                </div>
                <p className="text-xs text-slate-500 mt-1">
                  {momVariation !== null &&
                  momVariation !== undefined &&
                  momVariation > 0
                    ? t('analytics.salesTrend.growth')
                    : momVariation !== null &&
                        momVariation !== undefined &&
                        momVariation < 0
                      ? t('analytics.salesTrend.drop')
                      : t('analytics.salesTrend.trendSummary')}
                </p>
              </CardContent>
            </Card>

            {/* Current Month Sales Count */}
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardHeader className="pb-2">
                <CardTitle className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center justify-between">
                  <span>{t('analytics.salesTrend.salesCount')}</span>
                  <ShoppingCart className="h-4 w-4 text-indigo-600" />
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-black text-slate-900">
                  {currentMonthData
                    ? currentMonthData.salesCount.toLocaleString()
                    : '0'}
                </div>
                <p className="text-xs text-slate-500 mt-1">
                  {t('analytics.salesTrend.monthCount', { count: months })}:{' '}
                  {totals.sales.toLocaleString()}{' '}
                  {t('analytics.salesTrend.salesCount').toLowerCase()}
                </p>
              </CardContent>
            </Card>

            {/* Current Month Units Sold */}
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardHeader className="pb-2">
                <CardTitle className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center justify-between">
                  <span>{t('analytics.salesTrend.unitsSold')}</span>
                  <Package className="h-4 w-4 text-amber-600" />
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-black text-slate-900">
                  {currentMonthData
                    ? currentMonthData.unitsSold.toLocaleString('en-US', {
                        maximumFractionDigits: 2,
                      })
                    : '0'}
                </div>
                <p className="text-xs text-slate-500 mt-1">
                  {t('analytics.salesTrend.monthCount', { count: months })}:{' '}
                  {totals.units.toLocaleString('en-US', {
                    maximumFractionDigits: 2,
                  })}{' '}
                  {t('analytics.salesTrend.unitsSold').toLowerCase()}
                </p>
              </CardContent>
            </Card>
          </div>

          {/* Empty State vs Monthly Evolution Table */}
          {isEmpty ? (
            <Card className="p-8 text-center bg-white border-slate-200">
              <div className="mx-auto w-12 h-12 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 mb-3">
                <BarChart2 className="h-6 w-6" />
              </div>
              <h3 className="text-sm font-bold text-slate-800">
                {t('analytics.salesTrend.emptyState')}
              </h3>
              <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
                {t('analytics.salesTrend.subtitle')}
              </p>
            </Card>
          ) : (
            <Card className="bg-white border-slate-200 shadow-2xs overflow-hidden">
              <CardHeader className="border-b border-slate-100 pb-3">
                <CardTitle className="text-sm font-bold text-slate-900 flex items-center justify-between">
                  <span>{t('analytics.salesTrend.historicalEvolution')}</span>
                  <Badge
                    variant="outline"
                    className="font-semibold text-xs text-slate-600"
                  >
                    {t('analytics.salesTrend.monthCount', {
                      count: monthlyList.length,
                    })}
                  </Badge>
                </CardTitle>
              </CardHeader>
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader className="bg-slate-50">
                    <TableRow>
                      <TableHead className="w-44 text-xs font-bold text-slate-700">
                        {t('analytics.salesTrend.monthYear')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('analytics.salesTrend.salesCount')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('analytics.salesTrend.unitsSold')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('analytics.salesTrend.totalRevenue')}
                      </TableHead>
                      <TableHead className="w-56 text-xs font-bold text-slate-700">
                        {t('analytics.salesTrend.historicalEvolution')}
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {monthlyList.map((row, idx) => {
                      const isLast = idx === monthlyList.length - 1
                      const pct = Math.min(
                        100,
                        Math.round((row.totalAmount / maxRevenue) * 100),
                      )

                      return (
                        <TableRow
                          key={`${row.year}-${row.month}`}
                          className={
                            isLast
                              ? 'bg-indigo-50/40 font-semibold'
                              : 'hover:bg-slate-50/80'
                          }
                        >
                          <TableCell className="font-medium text-slate-900 text-xs">
                            <div className="flex items-center gap-2">
                              <span>{formatPeriod(row.year, row.month)}</span>
                              {isLast && (
                                <Badge className="bg-indigo-600 text-white text-[10px] px-1.5 py-0">
                                  {t('analytics.salesTrend.currentMonth')}
                                </Badge>
                              )}
                            </div>
                          </TableCell>
                          <TableCell className="text-right text-xs text-slate-700 font-mono">
                            {row.salesCount.toLocaleString()}
                          </TableCell>
                          <TableCell className="text-right text-xs text-slate-700 font-mono">
                            {row.unitsSold.toLocaleString('en-US', {
                              minimumFractionDigits: 2,
                              maximumFractionDigits: 2,
                            })}
                          </TableCell>
                          <TableCell className="text-right text-xs text-slate-900 font-bold font-mono">
                            $
                            {row.totalAmount.toLocaleString('en-US', {
                              minimumFractionDigits: 2,
                              maximumFractionDigits: 2,
                            })}
                          </TableCell>
                          <TableCell>
                            <div className="w-full bg-slate-100 rounded-full h-2.5 overflow-hidden">
                              <div
                                className={`h-full rounded-full transition-all ${
                                  isLast ? 'bg-indigo-600' : 'bg-slate-400'
                                }`}
                                style={{ width: `${pct}%` }}
                              />
                            </div>
                          </TableCell>
                        </TableRow>
                      )
                    })}
                  </TableBody>
                </Table>
              </div>
            </Card>
          )}
        </>
      )}
    </div>
  )
}
