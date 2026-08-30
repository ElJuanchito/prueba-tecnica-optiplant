import * as React from 'react'
import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Card, CardContent } from '@/components/ui/card.tsx'
import { Input } from '@/components/ui/input.tsx'
import { Label } from '@/components/ui/label.tsx'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select.tsx'
import { Skeleton } from '@/components/ui/skeleton.tsx'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog.tsx'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table.tsx'
import { useComplianceReport } from '../hooks/use-logistics.ts'
import { useBranches } from '@/features/iam/hooks/use-branches.ts'
import type {
  ComplianceGrouping,
  ComplianceQueryParams,
} from '../types/compliance.types.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  AlertTriangle,
  ArrowRight,
  Award,
  BarChart3,
  Calendar,
  CheckCircle,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  HelpCircle,
  Info,
  RefreshCw,
  TrendingDown,
  TrendingUp,
} from 'lucide-react'

export function ComplianceReportView() {
  const { t } = useTranslation()
  const [isGuideOpen, setIsGuideOpen] = React.useState(false)

  // Default date range: last 30 days
  const [fromDate, setFromDate] = React.useState<string>(() => {
    const d = new Date()
    d.setDate(d.getDate() - 30)
    return d.toISOString().split('T')[0] ?? ''
  })
  const [toDate, setToDate] = React.useState<string>(() => {
    return new Date().toISOString().split('T')[0] ?? ''
  })
  const [groupBy, setGroupBy] = React.useState<ComplianceGrouping>('ROUTE')
  const [page, setPage] = React.useState<number>(0)
  const pageSize = 10

  const queryParams: ComplianceQueryParams = {
    from: fromDate ? new Date(`${fromDate}T00:00:00Z`).toISOString() : '',
    to: toDate ? new Date(`${toDate}T23:59:59Z`).toISOString() : '',
    groupBy,
    page,
    size: pageSize,
  }

  const complianceQuery = useComplianceReport(
    queryParams,
    Boolean(fromDate && toDate),
  )

  // Aggregate totals for KPI cards
  const content = complianceQuery.data?.content ?? []
  const totalDelivered = content.reduce((acc, r) => acc + r.deliveredCount, 0)
  const totalOnTime = content.reduce((acc, r) => acc + r.onTimeCount, 0)
  const totalUnmeasured = content.reduce((acc, r) => acc + r.unmeasuredCount, 0)
  const measuredCount = totalDelivered - totalUnmeasured
  const overallRate =
    measuredCount > 0 ? (totalOnTime / measuredCount) * 100 : 0
  const avgDev =
    content.length > 0
      ? content.reduce((acc, r) => acc + Number(r.averageDeviationHours), 0) /
        content.length
      : 0

  const branchesQuery = useBranches({ page: 0, size: 100 })
  const branchesMap = React.useMemo(() => {
    const map = new Map<string, string>()
    branchesQuery.data?.content?.forEach((b) => {
      map.set(b.externalId, b.name)
    })
    return map
  }, [branchesQuery.data?.content])

  const formatComplianceLabel = (
    row: { key: string; label: string },
    grouping: ComplianceGrouping,
  ) => {
    const rawText = row.label || row.key
    if (grouping === 'BRANCH') {
      const bName = branchesMap.get(row.key) || branchesMap.get(row.label)
      return (
        <span className="font-semibold text-slate-900">
          {bName ? `Sucursal ${bName}` : rawText.length > 20 ? 'Sucursal Destino' : rawText}
        </span>
      )
    }

    // grouping === 'ROUTE'
    const parts = rawText.split('->')
    if (parts.length === 2) {
      const originId = parts[0]!.trim()
      const destId = parts[1]!.trim()
      const originName =
        branchesMap.get(originId) ||
        (originId.length > 20 ? 'Sucursal Origen' : originId)
      const destName =
        branchesMap.get(destId) ||
        (destId.length > 20 ? 'Sucursal Destino' : destId)
      return (
        <div className="flex items-center gap-1.5 text-slate-800 font-semibold">
          <span>{originName}</span>
          <ArrowRight className="h-3 w-3 text-slate-400 shrink-0" />
          <span>{destName}</span>
        </div>
      )
    }

    return <span className="font-semibold text-slate-900">{rawText}</span>
  }

  const totalPages = complianceQuery.data
    ? Math.ceil(complianceQuery.data.totalElements / pageSize)
    : 1

  return (
    <div className="space-y-5">
      {/* Filters Bar */}
      <div className="flex flex-col md:flex-row gap-3 items-start md:items-end justify-between bg-white p-4 rounded-xl border border-slate-200 shadow-2xs">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 w-full md:w-auto">
          {/* From Date */}
          <div className="space-y-1">
            <Label
              htmlFor="dateFrom"
              className="text-[11px] font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1"
            >
              <Calendar className="h-3 w-3" />
              {t('logistics.compliance.dateFrom')}
            </Label>
            <Input
              id="dateFrom"
              type="date"
              value={fromDate}
              onChange={(e) => {
                setFromDate(e.target.value)
                setPage(0)
              }}
              className="text-xs h-9 bg-slate-50 border-slate-200"
            />
          </div>

          {/* To Date */}
          <div className="space-y-1">
            <Label
              htmlFor="dateTo"
              className="text-[11px] font-bold text-slate-500 uppercase tracking-wider flex items-center gap-1"
            >
              <Calendar className="h-3 w-3" />
              {t('logistics.compliance.dateTo')}
            </Label>
            <Input
              id="dateTo"
              type="date"
              value={toDate}
              onChange={(e) => {
                setToDate(e.target.value)
                setPage(0)
              }}
              className="text-xs h-9 bg-slate-50 border-slate-200"
            />
          </div>

          {/* Group By */}
          <div className="space-y-1">
            <Label className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">
              {t('logistics.compliance.groupBy')}
            </Label>
            <Select
              value={groupBy}
              onValueChange={(val) => {
                setGroupBy(val as ComplianceGrouping)
                setPage(0)
              }}
            >
              <SelectTrigger className="text-xs h-9 bg-slate-50 border-slate-200">
                <SelectValue placeholder="Agrupar por" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ROUTE" className="text-xs">
                  {t('logistics.compliance.groupByRoute')}
                </SelectItem>
                <SelectItem value="BRANCH" className="text-xs">
                  {t('logistics.compliance.groupByBranch')}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setIsGuideOpen(true)}
            className="text-xs h-9 gap-1.5 text-indigo-700 bg-indigo-50/70 hover:bg-indigo-100/70 border-indigo-200 font-medium cursor-pointer"
          >
            <HelpCircle className="h-4 w-4 text-indigo-600" />
            <span className="hidden sm:inline">¿Cómo interpretar métricas?</span>
            <span className="sm:hidden">Guía</span>
          </Button>

          <Button
            variant="outline"
            size="sm"
            onClick={() => complianceQuery.refetch()}
            disabled={complianceQuery.isFetching}
            className="text-xs h-9 gap-1 text-slate-600 cursor-pointer"
            title={t('common.refresh')}
          >
            <RefreshCw
              className={`h-3.5 w-3.5 ${
                complianceQuery.isFetching ? 'animate-spin' : ''
              }`}
            />
            <span className="hidden sm:inline">{t('common.refresh')}</span>
          </Button>
        </div>
      </div>

      {/* KPI Metric Summary Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
        <Card className="border-slate-200 bg-white shadow-2xs">
          <CardContent className="p-3.5 flex items-center justify-between">
            <div>
              <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                {t('logistics.compliance.deliveredCount')}
              </p>
              <p className="text-lg font-black text-slate-900 font-mono mt-0.5">
                {totalDelivered}
              </p>
            </div>
            <div className="h-8 w-8 rounded-lg bg-slate-50 text-slate-600 flex items-center justify-center">
              <BarChart3 className="h-4 w-4" />
            </div>
          </CardContent>
        </Card>

        <Card className="border-slate-200 bg-white shadow-2xs">
          <CardContent className="p-3.5 flex items-center justify-between">
            <div>
              <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                {t('logistics.compliance.onTimeCount')}
              </p>
              <p className="text-lg font-black text-emerald-600 font-mono mt-0.5">
                {totalOnTime}
              </p>
            </div>
            <div className="h-8 w-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
              <CheckCircle2 className="h-4 w-4" />
            </div>
          </CardContent>
        </Card>

        <Card className="border-slate-200 bg-white shadow-2xs">
          <CardContent className="p-3.5 flex items-center justify-between">
            <div>
              <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                {t('logistics.compliance.onTimeRate')}
              </p>
              <p className="text-lg font-black text-indigo-600 font-mono mt-0.5">
                {overallRate.toFixed(1)}%
              </p>
            </div>
            <div className="h-8 w-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
              <Award className="h-4 w-4" />
            </div>
          </CardContent>
        </Card>

        <Card
          onClick={() => setIsGuideOpen(true)}
          className="border-slate-200 bg-white shadow-2xs hover:border-amber-400 hover:shadow-xs transition-all cursor-pointer group"
          title="Haz clic para ver la explicación detallada de la Desviación Promedio"
        >
          <CardContent className="p-3.5 flex items-center justify-between">
            <div>
              <div className="flex items-center gap-1">
                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                  {t('logistics.compliance.avgDeviation')}
                </p>
                <Info className="h-3 w-3 text-slate-400 group-hover:text-amber-600 transition-colors" />
              </div>
              <p
                className={`text-lg font-black font-mono mt-0.5 ${
                  avgDev < 0
                    ? 'text-emerald-600'
                    : avgDev > 0
                      ? 'text-amber-600'
                      : 'text-slate-900'
                }`}
              >
                {avgDev > 0 ? `+${avgDev.toFixed(1)}` : avgDev.toFixed(1)} hrs
              </p>
              <p className="text-[10px] text-slate-400 font-medium">
                {avgDev < 0
                  ? '⚡ Anticipada a ETA'
                  : avgDev > 0
                    ? '⚠️ Demora sobre ETA'
                    : 'Puntual exacto'}
              </p>
            </div>
            <div
              className={`h-8 w-8 rounded-lg flex items-center justify-center transition-transform group-hover:scale-105 ${
                avgDev < 0
                  ? 'bg-emerald-50 text-emerald-600'
                  : 'bg-amber-50 text-amber-600'
              }`}
            >
              <Clock className="h-4 w-4" />
            </div>
          </CardContent>
        </Card>

        <Card className="border-slate-200 bg-white shadow-2xs col-span-2 sm:col-span-1">
          <CardContent className="p-3.5 flex items-center justify-between">
            <div>
              <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                {t('logistics.compliance.unmeasured')}
              </p>
              <p className="text-lg font-black text-slate-500 font-mono mt-0.5">
                {totalUnmeasured}
              </p>
            </div>
            <div className="h-8 w-8 rounded-lg bg-slate-100 text-slate-400 flex items-center justify-center">
              <HelpCircle className="h-4 w-4" />
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Quick Visual Helper Ribbon */}
      <div className="bg-slate-50 border border-slate-200 rounded-xl p-3 px-4 flex flex-col sm:flex-row items-start sm:items-center justify-between text-xs gap-2">
        <div className="flex items-center gap-2 text-slate-700">
          <div className="h-6 w-6 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center shrink-0">
            <Info className="h-3.5 w-3.5" />
          </div>
          <span>
            <strong className="text-slate-900">¿Qué es la Desviación?</strong>{' '}
            Diferencia entre la llegada real y la estimada (ETA):{' '}
            <span className="inline-flex items-center text-emerald-700 font-bold bg-emerald-50 border border-emerald-200 rounded px-1.5 py-0.2 mx-1">
              Signo negativo (-) = Entrega anticipada
            </span>{' '}
            <span className="inline-flex items-center text-rose-700 font-bold bg-rose-50 border border-rose-200 rounded px-1.5 py-0.2 mx-1">
              Signo positivo (+) = Retraso
            </span>
          </span>
        </div>
        <button
          type="button"
          onClick={() => setIsGuideOpen(true)}
          className="text-xs text-indigo-600 font-bold hover:underline hover:text-indigo-800 shrink-0 cursor-pointer self-end sm:self-center"
        >
          Ver guía completa →
        </button>
      </div>

      {/* Compliance Data Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-2xs overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50/80 border-b border-slate-200">
            <TableRow>
              <TableHead className="text-xs font-bold text-slate-700 py-3">
                {groupBy === 'ROUTE' ? 'Ruta Logística' : 'Sucursal Destino'}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700 text-right">
                {t('logistics.compliance.deliveredCount')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700 text-right">
                {t('logistics.compliance.onTimeCount')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700 text-right">
                {t('logistics.compliance.onTimeRate')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700 text-right">
                {t('logistics.compliance.avgDeviation')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700 text-right pr-4">
                {t('logistics.compliance.unmeasured')}
              </TableHead>
            </TableRow>
          </TableHeader>

          <TableBody>
            {complianceQuery.isLoading && (
              <>
                {[...Array(4)].map((_, i) => (
                  <TableRow key={i}>
                    <TableCell>
                      <Skeleton className="h-4 w-48" />
                    </TableCell>
                    <TableCell className="text-right">
                      <Skeleton className="h-4 w-12 ml-auto" />
                    </TableCell>
                    <TableCell className="text-right">
                      <Skeleton className="h-4 w-12 ml-auto" />
                    </TableCell>
                    <TableCell className="text-right">
                      <Skeleton className="h-5 w-16 ml-auto" />
                    </TableCell>
                    <TableCell className="text-right">
                      <Skeleton className="h-4 w-16 ml-auto" />
                    </TableCell>
                    <TableCell className="text-right">
                      <Skeleton className="h-4 w-12 ml-auto" />
                    </TableCell>
                  </TableRow>
                ))}
              </>
            )}

            {!complianceQuery.isLoading && content.length === 0 && (
              <TableRow>
                <TableCell
                  colSpan={6}
                  className="text-center py-12 text-slate-400"
                >
                  <AlertTriangle className="h-8 w-8 mx-auto mb-2 text-slate-300" />
                  <p className="text-sm font-semibold">
                    No delivered transfers recorded in this date range.
                  </p>
                </TableCell>
              </TableRow>
            )}

            {!complianceQuery.isLoading &&
              content.map((row) => {
                const percentage = Number(row.onTimePercentage)
                return (
                  <TableRow key={row.key} className="hover:bg-slate-50/60">
                    {/* Label / Key */}
                    <TableCell className="text-xs font-semibold text-slate-900">
                      {formatComplianceLabel(row, groupBy)}
                    </TableCell>

                    {/* Delivered */}
                    <TableCell className="text-xs text-right font-mono font-bold text-slate-800">
                      {row.deliveredCount}
                    </TableCell>

                    {/* On Time */}
                    <TableCell className="text-xs text-right font-mono font-bold text-emerald-600">
                      {row.onTimeCount}
                    </TableCell>

                    {/* On Time % */}
                    <TableCell className="text-right">
                      <Badge
                        variant="outline"
                        className={`text-[10px] font-mono font-bold ${
                          percentage >= 90
                            ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                            : percentage >= 75
                              ? 'bg-amber-50 text-amber-700 border-amber-200'
                              : 'bg-rose-50 text-rose-700 border-rose-200'
                        }`}
                      >
                        {percentage.toFixed(1)}%
                      </Badge>
                    </TableCell>

                    {/* Average Deviation */}
                    <TableCell className="text-xs text-right font-mono text-slate-700">
                      {Number(row.averageDeviationHours) > 0
                        ? `+${Number(row.averageDeviationHours).toFixed(1)} hrs`
                        : `${Number(row.averageDeviationHours).toFixed(1)} hrs`}
                    </TableCell>

                    {/* Unmeasured without ETA (R-26) */}
                    <TableCell className="text-xs text-right font-mono text-slate-400 pr-4">
                      {row.unmeasuredCount}
                    </TableCell>
                  </TableRow>
                )
              })}
          </TableBody>
        </Table>

        {/* Pagination Footer */}
        {complianceQuery.data && complianceQuery.data.totalElements > 0 && (
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
                disabled={page === 0 || complianceQuery.isFetching}
                className="h-7 px-2 text-xs"
              >
                <ChevronLeft className="h-3.5 w-3.5 mr-1" />
                {t('common.previous', { defaultValue: 'Anterior' })}
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.min(p + 1, totalPages - 1))}
                disabled={page >= totalPages - 1 || complianceQuery.isFetching}
                className="h-7 px-2 text-xs"
              >
                {t('common.next', { defaultValue: 'Siguiente' })}
                <ChevronRight className="h-3.5 w-3.5 ml-1" />
              </Button>
            </div>
          </div>
        )}
      </div>

      {/* Visual Guide Modal for Logistics Metrics */}
      <Dialog open={isGuideOpen} onOpenChange={setIsGuideOpen}>
        <DialogContent className="max-w-2xl bg-white p-6 sm:rounded-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <div className="flex items-center space-x-2.5">
              <div className="h-9 w-9 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center border border-indigo-200">
                <HelpCircle className="h-5 w-5" />
              </div>
              <div>
                <DialogTitle className="text-base font-bold text-slate-900">
                  Guía de Interpretación de Métricas Logísticas
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-500">
                  Aprende a interpretar los indicadores clave de rendimiento (KPIs) de entregas y puntualidad.
                </DialogDescription>
              </div>
            </div>
          </DialogHeader>

          <div className="space-y-4 py-2 text-xs">
            {/* Metric 1: Desviación Promedio */}
            <div className="p-4 rounded-xl border border-amber-200 bg-amber-50/30 space-y-3">
              <div className="flex items-center justify-between flex-wrap gap-2">
                <div className="flex items-center gap-2">
                  <Clock className="h-4 w-4 text-amber-600" />
                  <h4 className="font-bold text-slate-900 text-sm">
                    1. Desviación Promedio (Horas)
                  </h4>
                </div>
                <Badge
                  variant="outline"
                  className="bg-white text-amber-800 border-amber-300 font-mono text-[10px]"
                >
                  Hora Real - Hora Estimada
                </Badge>
              </div>

              <p className="text-slate-600 leading-relaxed">
                Mide la diferencia matemática en horas entre el momento exacto en que la sucursal de destino confirmó la recepción física y la hora estimada de llegada (<strong className="text-slate-800">ETA</strong>) calculada al despachar el traslado.
              </p>

              <div className="bg-white p-3 rounded-lg border border-amber-200 font-mono text-center text-xs text-slate-800 shadow-2xs">
                <strong>Desviación (hrs)</strong> = Hora Real de Llegada - Hora Estimada de Llegada (ETA)
              </div>

              {/* 3 Visual Interpretation Cards */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5 pt-1">
                {/* Early / Negative */}
                <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-lg space-y-1">
                  <div className="flex items-center gap-1.5 text-emerald-800 font-bold">
                    <TrendingDown className="h-4 w-4 text-emerald-600" />
                    <span>Valor Negativo (-)</span>
                  </div>
                  <p className="text-[11px] font-semibold text-emerald-700">
                    Ejemplo: -8.3 hrs
                  </p>
                  <p className="text-[11px] text-emerald-900 leading-tight">
                    <strong>Entrega anticipada:</strong> El transporte arribó antes de la hora programada. Mayor eficiencia operativa.
                  </p>
                </div>

                {/* Exact On Time */}
                <div className="p-3 bg-indigo-50 border border-indigo-200 rounded-lg space-y-1">
                  <div className="flex items-center gap-1.5 text-indigo-800 font-bold">
                    <CheckCircle className="h-4 w-4 text-indigo-600" />
                    <span>Valor Cero (0.0)</span>
                  </div>
                  <p className="text-[11px] font-semibold text-indigo-700">
                    Ejemplo: 0.0 hrs
                  </p>
                  <p className="text-[11px] text-indigo-900 leading-tight">
                    <strong>Puntualidad exacta:</strong> El transporte arribó exactamente a la hora calculada.
                  </p>
                </div>

                {/* Delayed / Positive */}
                <div className="p-3 bg-rose-50 border border-rose-200 rounded-lg space-y-1">
                  <div className="flex items-center gap-1.5 text-rose-800 font-bold">
                    <TrendingUp className="h-4 w-4 text-rose-600" />
                    <span>Valor Positivo (+)</span>
                  </div>
                  <p className="text-[11px] font-semibold text-rose-700">
                    Ejemplo: +4.5 hrs
                  </p>
                  <p className="text-[11px] text-rose-900 leading-tight">
                    <strong>Demora o retraso:</strong> El transporte demoró más de lo programado respecto al SLA pactado.
                  </p>
                </div>
              </div>
            </div>

            {/* Metric 2: % Cumplimiento */}
            <div className="p-4 rounded-xl border border-slate-200 bg-white space-y-2">
              <div className="flex items-center gap-2">
                <Award className="h-4 w-4 text-indigo-600" />
                <h4 className="font-bold text-slate-900 text-sm">
                  2. Porcentaje de Cumplimiento (% On-Time)
                </h4>
              </div>
              <p className="text-slate-600 leading-relaxed">
                Representa el porcentaje de traslados medidos que llegaron a destino <strong className="text-slate-800">a tiempo o antes de la fecha límite (ETA)</strong>.
              </p>
              <div className="bg-slate-50 p-2.5 rounded-lg border border-slate-200 font-mono text-center text-xs text-slate-800">
                <strong>% Cumplimiento</strong> = (Traslados a Tiempo / Total de Traslados Medidos) × 100
              </div>
            </div>

            {/* Metric 3: Sin ETA / No Medidos */}
            <div className="p-4 rounded-xl border border-slate-200 bg-white space-y-2">
              <div className="flex items-center gap-2">
                <HelpCircle className="h-4 w-4 text-slate-500" />
                <h4 className="font-bold text-slate-900 text-sm">
                  3. Entregas Sin ETA / No Medidos (Regla R-26)
                </h4>
              </div>
              <p className="text-slate-600 leading-relaxed">
                Son traslados que se despacharon sin una fecha estimada de llegada (ETA) parametrizada. Según la regla de negocio <strong className="text-slate-800">R-26</strong>, estos traslados se contabilizan de forma independiente y <strong className="text-emerald-700">no penalizan</strong> el porcentaje de cumplimiento logístico.
              </p>
            </div>
          </div>

          <DialogFooter>
            <Button
              onClick={() => setIsGuideOpen(false)}
              className="w-full sm:w-auto text-xs bg-slate-900 hover:bg-slate-800 text-white cursor-pointer"
            >
              Entendido
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
