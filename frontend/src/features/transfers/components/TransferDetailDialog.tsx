import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert.tsx'
import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog.tsx'
import { Skeleton } from '@/components/ui/skeleton.tsx'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table.tsx'
import { useTransferDetail } from '../hooks/use-transfers.ts'
import type {
  TransferPriority,
  TransferStatus,
} from '../types/transfer.types.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  AlertCircle,
  AlertTriangle,
  ArrowLeftRight,
  ArrowRight,
  Clock,
  MapPin,
  Truck,
} from 'lucide-react'

interface TransferDetailDialogProps {
  externalId: string | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function TransferDetailDialog({
  externalId,
  open,
  onOpenChange,
}: TransferDetailDialogProps) {
  const { t } = useTranslation()
  const detailQuery = useTransferDetail(
    externalId ?? '',
    Boolean(externalId && open),
  )

  const detail = detailQuery.data

  const getStatusBadge = (status: TransferStatus) => {
    switch (status) {
      case 'REQUESTED':
        return (
          <Badge
            variant="outline"
            className="bg-sky-50 text-sky-700 border-sky-200"
          >
            {t('transfers.statuses.REQUESTED')}
          </Badge>
        )
      case 'IN_PREPARATION':
        return (
          <Badge
            variant="outline"
            className="bg-amber-50 text-amber-700 border-amber-200"
          >
            {t('transfers.statuses.IN_PREPARATION')}
          </Badge>
        )
      case 'IN_TRANSIT':
        return (
          <Badge
            variant="outline"
            className="bg-indigo-50 text-indigo-700 border-indigo-200"
          >
            {t('transfers.statuses.IN_TRANSIT')}
          </Badge>
        )
      case 'RECEIVED':
        return (
          <Badge
            variant="outline"
            className="bg-emerald-50 text-emerald-700 border-emerald-200"
          >
            {t('transfers.statuses.RECEIVED')}
          </Badge>
        )
      case 'RECEIVED_WITH_DISCREPANCY':
        return (
          <Badge
            variant="outline"
            className="bg-rose-50 text-rose-700 border-rose-200"
          >
            {t('transfers.statuses.RECEIVED_WITH_DISCREPANCY')}
          </Badge>
        )
      case 'CANCELLED':
        return (
          <Badge
            variant="outline"
            className="bg-slate-100 text-slate-600 border-slate-200"
          >
            {t('transfers.statuses.CANCELLED')}
          </Badge>
        )
      default:
        return <Badge variant="outline">{status}</Badge>
    }
  }

  const getPriorityBadge = (priority: TransferPriority) => {
    switch (priority) {
      case 'URGENT':
        return (
          <Badge
            variant="outline"
            className="bg-red-50 text-red-700 border-red-200 font-semibold"
          >
            {t('transfers.priorities.URGENT')}
          </Badge>
        )
      case 'STANDARD':
        return (
          <Badge
            variant="outline"
            className="bg-slate-100 text-slate-700 border-slate-200"
          >
            {t('transfers.priorities.STANDARD')}
          </Badge>
        )
      case 'LOW':
        return (
          <Badge
            variant="outline"
            className="bg-zinc-50 text-zinc-600 border-zinc-200"
          >
            {t('transfers.priorities.LOW')}
          </Badge>
        )
      default:
        return <Badge variant="outline">{priority}</Badge>
    }
  }

  const formatObservation = (obs: string) => {
    let formatted = obs
    if (detail?.items) {
      detail.items.forEach((item) => {
        if (
          item.productExternalId &&
          formatted.includes(item.productExternalId)
        ) {
          const displayName = item.name
            ? `${item.name}${item.sku ? ` (${item.sku})` : ''}`
            : item.sku || 'Producto'
          formatted = formatted.replaceAll(
            `Item ${item.productExternalId}`,
            displayName,
          )
          formatted = formatted.replaceAll(item.productExternalId, displayName)
        }
      })
    }

    // Match patterns like "Producto (SKU) approved at 1 instead of the requested 2.0000"
    const match = formatted.match(
      /^(?:Item\s+)?(.+?)\s+approved at\s+([\d.]+)\s+instead of the requested\s+([\d.]+)$/i,
    )
    if (match && match[1] && match[2] && match[3]) {
      const prodName = match[1]
      const formattedApproved = parseFloat(match[2]).toString()
      const formattedRequested = parseFloat(match[3]).toString()
      return `Producto "${prodName}": Aprobado con ${formattedApproved} en lugar de los ${formattedRequested} solicitados.`
    }

    return formatted
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl bg-white p-6 sm:rounded-xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <div className="flex items-center justify-between gap-4">
            <div className="flex items-center space-x-2">
              <div className="h-8 w-8 rounded-lg bg-cyan-50 text-cyan-600 flex items-center justify-center border border-cyan-200">
                <ArrowLeftRight className="h-4 w-4" />
              </div>
              <div>
                <DialogTitle className="text-base font-bold text-slate-900 flex items-center gap-2">
                  {detail?.transferNumber ?? 'Transfer Details'}
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-500">
                  {t('transfers.subtitle')}
                </DialogDescription>
              </div>
            </div>
            {detail && (
              <div className="flex items-center gap-2">
                {getPriorityBadge(detail.priority)}
                {getStatusBadge(detail.status)}
              </div>
            )}
          </div>
        </DialogHeader>

        {detailQuery.isLoading && (
          <div className="space-y-4 py-4">
            <Skeleton className="h-20 w-full rounded-lg" />
            <Skeleton className="h-32 w-full rounded-lg" />
            <Skeleton className="h-40 w-full rounded-lg" />
          </div>
        )}

        {detailQuery.isError && (
          <Alert variant="destructive" className="my-4">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle className="text-xs font-semibold">Error</AlertTitle>
            <AlertDescription className="text-xs">
              {detailQuery.error?.message || 'Failed to load transfer details'}
            </AlertDescription>
          </Alert>
        )}

        {detail && (
          <div className="space-y-5 py-2">
            {/* Origin -> Destination Card */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 p-3.5 rounded-xl bg-slate-50 border border-slate-200 text-xs">
              <div className="space-y-1">
                <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1">
                  <MapPin className="h-3 w-3 text-cyan-600" />
                  {t('transfers.originBranch')}
                </span>
                <p className="font-bold text-slate-900 text-sm">
                  {detail.originBranch?.name ?? '—'}
                </p>
              </div>

              <div className="space-y-1">
                <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1">
                  <ArrowRight className="h-3 w-3 text-cyan-600" />
                  {t('transfers.destinationBranch')}
                </span>
                <p className="font-bold text-slate-900 text-sm">
                  {detail.destinationBranch?.name ?? '—'}
                </p>
              </div>
            </div>

            {/* Logistics & Tracking Info */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 p-3 rounded-lg border border-slate-200 bg-white text-xs">
              <div className="space-y-0.5">
                <span className="text-slate-400 text-[10px] uppercase font-bold flex items-center gap-1">
                  <Truck className="h-2.5 w-2.5" />
                  {t('transfers.carrier')}
                </span>
                <p className="font-semibold text-slate-800">
                  {detail.carrierName ?? '—'}
                </p>
              </div>

              <div className="space-y-0.5">
                <span className="text-slate-400 text-[10px] uppercase font-bold">
                  {t('transfers.trackingNumber')}
                </span>
                <p className="font-mono text-slate-800">
                  {detail.trackingNumber ?? '—'}
                </p>
              </div>

              <div className="space-y-0.5">
                <span className="text-slate-400 text-[10px] uppercase font-bold flex items-center gap-1">
                  <Clock className="h-2.5 w-2.5" />
                  {t('transfers.estimatedArrival')}
                </span>
                <p className="text-slate-800">
                  {detail.estimatedArrivalAt
                    ? new Date(detail.estimatedArrivalAt).toLocaleString()
                    : '—'}
                </p>
              </div>

              <div className="space-y-0.5">
                <span className="text-slate-400 text-[10px] uppercase font-bold">
                  {t('transfers.actualArrival')}
                </span>
                <p className="text-slate-800">
                  {detail.actualArrivalAt
                    ? new Date(detail.actualArrivalAt).toLocaleString()
                    : '—'}
                </p>
                {detail.deviationHours !== null &&
                  detail.deviationHours !== undefined && (
                    <span className="text-[10px] font-mono text-slate-500 block">
                      {detail.deviationHours > 0
                        ? `+${detail.deviationHours} hrs`
                        : `${detail.deviationHours} hrs`}
                    </span>
                  )}
              </div>
            </div>

            {/* Observations / Notes */}
            {detail.observations && detail.observations.length > 0 && (
              <div className="p-3 bg-amber-50/60 border border-amber-200 rounded-lg text-xs space-y-1">
                <span className="font-bold text-amber-900 block text-[11px]">
                  {t('transfers.observations')} / {t('transfers.notes')}
                </span>
                <ul className="list-disc pl-4 space-y-0.5 text-amber-800">
                  {detail.observations.map((obs, idx) => (
                    <li key={idx}>{formatObservation(obs)}</li>
                  ))}
                </ul>
              </div>
            )}

            {/* Items Table */}
            <div className="space-y-2">
              <span className="font-bold text-xs text-slate-800 block">
                {t('transfers.items')} ({detail.items.length})
              </span>
              <div className="border border-slate-200 rounded-lg overflow-hidden">
                <Table>
                  <TableHeader className="bg-slate-50">
                    <TableRow>
                      <TableHead className="text-xs">
                        {t('transfers.sku')}
                      </TableHead>
                      <TableHead className="text-xs">
                        {t('transfers.product')}
                      </TableHead>
                      <TableHead className="text-xs text-right">
                        {t('transfers.requestedQty')}
                      </TableHead>
                      <TableHead className="text-xs text-right">
                        {t('transfers.dispatchedQty')}
                      </TableHead>
                      <TableHead className="text-xs text-right">
                        {t('transfers.receivedQty')}
                      </TableHead>
                      <TableHead className="text-xs text-right">
                        {t('transfers.discrepancyQty')}
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {detail.items.map((item) => (
                      <TableRow key={item.externalId}>
                        <TableCell className="text-xs font-mono font-semibold text-slate-700">
                          {item.sku}
                        </TableCell>
                        <TableCell className="text-xs font-medium text-slate-900">
                          {item.name}
                        </TableCell>
                        <TableCell className="text-xs text-right font-mono font-semibold">
                          {item.requestedQuantity}
                        </TableCell>
                        <TableCell className="text-xs text-right font-mono">
                          {item.dispatchedQuantity ?? '—'}
                        </TableCell>
                        <TableCell className="text-xs text-right font-mono">
                          {item.receivedQuantity ?? '—'}
                        </TableCell>
                        <TableCell className="text-xs text-right font-mono">
                          {item.discrepancyQuantity !== null &&
                          item.discrepancyQuantity !== undefined &&
                          item.discrepancyQuantity > 0 ? (
                            <span className="text-rose-600 font-bold flex items-center justify-end gap-1">
                              <AlertTriangle className="h-3 w-3" />
                              {item.discrepancyQuantity}
                            </span>
                          ) : (
                            '—'
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </div>
          </div>
        )}

        <DialogFooter className="pt-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onOpenChange(false)}
            className="text-xs"
          >
            {t('common.close')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
