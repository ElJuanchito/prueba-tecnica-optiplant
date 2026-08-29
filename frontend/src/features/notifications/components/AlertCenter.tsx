import * as React from 'react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert.tsx'
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
import { useAlerts, useResolveAlert } from '../hooks/use-alerts.ts'
import type { AlertSeverity, AlertType } from '../types/index.ts'
import {
  AlertCircle,
  AlertOctagon,
  AlertTriangle,
  Bell,
  CheckCircle,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  Info,
  Loader2,
  RotateCcw,
} from 'lucide-react'

interface AlertCenterProps {
  currentActorRole?: 'ADMIN' | 'BRANCH_MANAGER' | 'OPERATOR'
}

export function AlertCenter({
  currentActorRole = 'OPERATOR',
}: AlertCenterProps) {
  const [page, setPage] = React.useState(0)
  const [size] = React.useState(15)
  const [statusFilter, setStatusFilter] = React.useState<'unresolved' | 'resolved' | 'all'>('unresolved')
  const [severityFilter, setSeverityFilter] = React.useState<AlertSeverity | 'ALL'>('ALL')
  const [typeFilter, setTypeFilter] = React.useState<AlertType | 'ALL'>('ALL')
  const [resolvingId, setResolvingId] = React.useState<string | null>(null)
  const [actionError, setActionError] = React.useState<string | null>(null)

  const canResolve = currentActorRole === 'ADMIN' || currentActorRole === 'BRANCH_MANAGER'

  const alertsQuery = useAlerts({
    page,
    size,
    resolved:
      statusFilter === 'unresolved'
        ? false
        : statusFilter === 'resolved'
          ? true
          : undefined,
    severity: severityFilter !== 'ALL' ? severityFilter : undefined,
    alertType: typeFilter !== 'ALL' ? typeFilter : undefined,
  })

  const resolveMutation = useResolveAlert()

  const handleResolve = (externalId: string) => {
    setResolvingId(externalId)
    setActionError(null)
    resolveMutation.mutate(externalId, {
      onSettled: () => {
        setResolvingId(null)
      },
      onError: (err) => {
        setActionError(err.message || 'Failed to resolve alert')
      },
    })
  }

  const rawAlerts = alertsQuery.data?.content ?? []
  const totalElements = alertsQuery.data?.totalElements ?? 0
  const totalPages = Math.ceil(totalElements / size) || 1

  const getSeverityIcon = (severity: AlertSeverity) => {
    switch (severity) {
      case 'CRITICAL':
        return <AlertOctagon className="h-4 w-4 text-rose-600 shrink-0" />
      case 'WARNING':
        return <AlertTriangle className="h-4 w-4 text-amber-600 shrink-0" />
      case 'INFO':
      default:
        return <Info className="h-4 w-4 text-sky-600 shrink-0" />
    }
  }

  const getSeverityBadge = (severity: AlertSeverity) => {
    switch (severity) {
      case 'CRITICAL':
        return (
          <Badge className="bg-rose-100 text-rose-800 border-rose-200 text-[10px] py-0 px-1.5 font-bold">
            CRITICAL
          </Badge>
        )
      case 'WARNING':
        return (
          <Badge className="bg-amber-100 text-amber-800 border-amber-200 text-[10px] py-0 px-1.5 font-semibold">
            WARNING
          </Badge>
        )
      case 'INFO':
      default:
        return (
          <Badge className="bg-sky-100 text-sky-800 border-sky-200 text-[10px] py-0 px-1.5">
            INFO
          </Badge>
        )
    }
  }

  return (
    <div className="space-y-4">
      {/* Filter Control Bar */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-2xs space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <div className="h-6 w-6 rounded bg-rose-50 text-rose-600 flex items-center justify-center border border-rose-200">
              <Bell className="h-3.5 w-3.5" />
            </div>
            <span className="text-xs font-bold text-slate-900 uppercase tracking-wider">
              Operational Alert Management Center
            </span>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setStatusFilter('unresolved')
              setSeverityFilter('ALL')
              setTypeFilter('ALL')
              setPage(0)
            }}
            className="text-xs h-7 text-slate-600 hover:text-slate-900"
          >
            <RotateCcw className="h-3 w-3 mr-1" />
            Reset Filters
          </Button>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-1">
          <div>
            <label className="text-[11px] font-semibold text-slate-600 block mb-1">
              Resolution Status
            </label>
            <Select
              value={statusFilter}
              onValueChange={(val) => {
                setStatusFilter(val as 'unresolved' | 'resolved' | 'all')
                setPage(0)
              }}
            >
              <SelectTrigger className="text-xs h-8 bg-slate-50 border-slate-200">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="unresolved">Active (Unresolved Only)</SelectItem>
                <SelectItem value="resolved">Resolved History</SelectItem>
                <SelectItem value="all">All Alerts</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div>
            <label className="text-[11px] font-semibold text-slate-600 block mb-1">
              Severity Level
            </label>
            <Select
              value={severityFilter}
              onValueChange={(val) => {
                setSeverityFilter(val as AlertSeverity | 'ALL')
                setPage(0)
              }}
            >
              <SelectTrigger className="text-xs h-8 bg-slate-50 border-slate-200">
                <SelectValue placeholder="All Severities" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Severities</SelectItem>
                <SelectItem value="CRITICAL">CRITICAL</SelectItem>
                <SelectItem value="WARNING">WARNING</SelectItem>
                <SelectItem value="INFO">INFO</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div>
            <label className="text-[11px] font-semibold text-slate-600 block mb-1">
              Alert Category
            </label>
            <Select
              value={typeFilter}
              onValueChange={(val) => {
                setTypeFilter(val as AlertType | 'ALL')
                setPage(0)
              }}
            >
              <SelectTrigger className="text-xs h-8 bg-slate-50 border-slate-200">
                <SelectValue placeholder="All Categories" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Categories</SelectItem>
                <SelectItem value="STOCK_MINIMUM">STOCK_MINIMUM</SelectItem>
                <SelectItem value="LOGISTIC_DELAY">LOGISTIC_DELAY</SelectItem>
                <SelectItem value="TRANSFER_DISCREPANCY">TRANSFER_DISCREPANCY</SelectItem>
                <SelectItem value="PRICE_CHANGE">PRICE_CHANGE</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>

      {actionError && (
        <Alert variant="destructive" className="py-2.5">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle className="text-xs font-semibold">Action Failed</AlertTitle>
          <AlertDescription className="text-xs">{actionError}</AlertDescription>
        </Alert>
      )}

      {/* Alerts List */}
      <div className="space-y-3">
        {alertsQuery.isLoading ? (
          <div className="space-y-3">
            <Skeleton className="h-20 w-full rounded-xl" />
            <Skeleton className="h-20 w-full rounded-xl" />
            <Skeleton className="h-20 w-full rounded-xl" />
          </div>
        ) : alertsQuery.isError ? (
          <div className="p-8 text-center bg-white rounded-xl border border-slate-200">
            <div className="h-10 w-10 rounded-full bg-rose-50 text-rose-600 flex items-center justify-center mx-auto mb-3">
              <AlertCircle className="h-5 w-5" />
            </div>
            <p className="text-sm font-bold text-slate-900">Failed to load alerts</p>
            <p className="text-xs text-slate-500 mt-1">{alertsQuery.error.message}</p>
          </div>
        ) : rawAlerts.length === 0 ? (
          <div className="p-12 text-center bg-white rounded-xl border border-slate-200 shadow-2xs">
            <div className="h-12 w-12 rounded-full bg-emerald-50 text-emerald-600 flex items-center justify-center mx-auto mb-3">
              <CheckCircle className="h-6 w-6" />
            </div>
            <p className="text-sm font-bold text-slate-800">
              {statusFilter === 'unresolved'
                ? 'All Clear — No Active Operational Alerts'
                : 'No alerts match the selected criteria'}
            </p>
            <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
              {statusFilter === 'unresolved'
                ? 'All stock levels and operations in this branch are currently within safety limits.'
                : 'Try adjusting your filters to view past alerts.'}
            </p>
          </div>
        ) : (
          rawAlerts.map((alert) => (
            <div
              key={alert.externalId}
              className={`p-4 rounded-xl border transition-all ${
                alert.isResolved
                  ? 'bg-slate-50 border-slate-200 opacity-75'
                  : alert.severity === 'CRITICAL'
                    ? 'bg-white border-rose-300 shadow-xs ring-1 ring-rose-200'
                    : 'bg-white border-slate-200 shadow-2xs'
              }`}
            >
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex items-start space-x-3">
                  <div className="mt-0.5">{getSeverityIcon(alert.severity)}</div>
                  <div className="space-y-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-bold text-xs text-slate-900">
                        {alert.title}
                      </span>
                      {getSeverityBadge(alert.severity)}
                      <Badge
                        variant="outline"
                        className="text-[10px] py-0 px-1.5 font-mono text-slate-600 border-slate-300"
                      >
                        {alert.alertType}
                      </Badge>
                      {alert.isResolved && (
                        <Badge
                          variant="outline"
                          className="bg-emerald-50 text-emerald-700 border-emerald-300 text-[10px] py-0 px-1.5 flex items-center gap-1"
                        >
                          <CheckCircle2 className="h-2.5 w-2.5" />
                          Resolved
                        </Badge>
                      )}
                    </div>
                    <p className="text-xs text-slate-700 leading-relaxed">
                      {alert.message}
                    </p>
                    <div className="flex items-center gap-3 text-[10px] text-slate-500 pt-0.5 font-mono">
                      <span className="flex items-center gap-1">
                        <Clock className="h-3 w-3" />
                        Raised: {new Date(alert.createdAt).toLocaleString()}
                      </span>
                      {alert.resolvedAt && (
                        <span className="flex items-center gap-1 text-emerald-700">
                          <CheckCircle2 className="h-3 w-3" />
                          Resolved: {new Date(alert.resolvedAt).toLocaleString()}
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                {!alert.isResolved && canResolve && (
                  <div className="sm:self-center shrink-0">
                    <Button
                      size="sm"
                      onClick={() => handleResolve(alert.externalId)}
                      disabled={resolvingId === alert.externalId}
                      className="text-xs bg-slate-900 hover:bg-slate-800 text-white h-8"
                    >
                      {resolvingId === alert.externalId ? (
                        <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
                      ) : (
                        <CheckCircle2 className="h-3.5 w-3.5 mr-1.5" />
                      )}
                      Mark Resolved
                    </Button>
                  </div>
                )}
              </div>
            </div>
          ))
        )}

        {/* Pagination Bar */}
        {!alertsQuery.isLoading && !alertsQuery.isError && rawAlerts.length > 0 && (
          <div className="p-3.5 border border-slate-200 rounded-xl bg-white flex items-center justify-between text-xs text-slate-600 shadow-2xs">
            <div>
              Showing{' '}
              <span className="font-semibold text-slate-900">
                {page * size + 1}
              </span>{' '}
              to{' '}
              <span className="font-semibold text-slate-900">
                {Math.min((page + 1) * size, totalElements)}
              </span>{' '}
              of{' '}
              <span className="font-semibold text-slate-900">
                {totalElements}
              </span>{' '}
              alerts
            </div>
            <div className="flex items-center gap-1.5">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="h-7 px-2.5 text-xs"
              >
                <ChevronLeft className="h-3 w-3 mr-1" />
                Previous
              </Button>
              <span className="px-2 text-xs font-medium">
                Page {page + 1} of {totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="h-7 px-2.5 text-xs"
              >
                Next
                <ChevronRight className="h-3 w-3 ml-1" />
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
