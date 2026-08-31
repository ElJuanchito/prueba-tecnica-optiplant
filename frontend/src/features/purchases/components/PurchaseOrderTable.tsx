import { Ban, CheckCircle2, Edit2, Eye, PackageCheck } from 'lucide-react'
import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import type {
  PurchaseOrderStatus,
  PurchaseOrderSummaryResponse,
} from '../types/index.ts'

interface PurchaseOrderTableProps {
  orders: PurchaseOrderSummaryResponse[]
  isLoading: boolean
  totalElements: number
  page: number
  size: number
  onPageChange: (page: number) => void
  onViewDetail: (order: PurchaseOrderSummaryResponse) => void
  onEdit?: ((order: PurchaseOrderSummaryResponse) => void) | undefined
  onApprove?: ((order: PurchaseOrderSummaryResponse) => void) | undefined
  onReceive?: ((order: PurchaseOrderSummaryResponse) => void) | undefined
  onCancel?: ((order: PurchaseOrderSummaryResponse) => void) | undefined
  canApprove: boolean
  canReceive: boolean
  canCancel: boolean
}

export function PurchaseOrderTable({
  orders,
  isLoading,
  totalElements,
  page,
  size,
  onPageChange,
  onViewDetail,
  onEdit,
  onApprove,
  onReceive,
  onCancel,
  canApprove,
  canReceive,
  canCancel,
}: PurchaseOrderTableProps) {
  const { t } = useTranslation()
  const totalPages = Math.ceil(totalElements / size) || 1

  const getStatusBadge = (status: PurchaseOrderStatus) => {
    switch (status) {
      case 'PENDING':
        return (
          <Badge
            variant="outline"
            className="text-[10px] font-semibold border-amber-300 bg-amber-50 text-amber-800"
          >
            {t('purchases.status.PENDING')}
          </Badge>
        )
      case 'APPROVED':
        return (
          <Badge
            variant="outline"
            className="text-[10px] font-semibold border-sky-300 bg-sky-50 text-sky-800"
          >
            {t('purchases.status.APPROVED')}
          </Badge>
        )
      case 'PARTIALLY_RECEIVED':
        return (
          <Badge
            variant="outline"
            className="text-[10px] font-semibold border-indigo-300 bg-indigo-50 text-indigo-800"
          >
            {t('purchases.status.PARTIALLY_RECEIVED')}
          </Badge>
        )
      case 'RECEIVED':
        return (
          <Badge
            variant="outline"
            className="text-[10px] font-semibold border-emerald-300 bg-emerald-50 text-emerald-800"
          >
            {t('purchases.status.RECEIVED')}
          </Badge>
        )
      case 'CANCELLED':
        return (
          <Badge
            variant="outline"
            className="text-[10px] font-semibold border-rose-300 bg-rose-50 text-rose-800"
          >
            {t('purchases.status.CANCELLED')}
          </Badge>
        )
    }
  }

  const formatDate = (isoString?: string | null) => {
    if (!isoString) return '—'
    try {
      const d = new Date(isoString)
      return d.toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      })
    } catch {
      return isoString
    }
  }

  if (isLoading) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-12 text-center shadow-2xs">
        <div className="inline-flex h-8 w-8 animate-spin rounded-full border-2 border-slate-300 border-t-rose-600" />
        <p className="mt-2 text-xs text-slate-500">{t('common.loading')}</p>
      </div>
    )
  }

  if (orders.length === 0) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-12 text-center shadow-2xs">
        <p className="text-sm font-semibold text-slate-700">
          {t('purchases.orders.noOrders')}
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xs">
        <Table>
          <TableHeader>
            <TableRow className="bg-slate-50/75 hover:bg-slate-50/75">
              <TableHead className="text-xs font-bold text-slate-700">
                {t('purchases.orders.orderNumber')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('purchases.orders.supplier')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('common.status')}
              </TableHead>
              <TableHead className="text-right text-xs font-bold text-slate-700">
                {t('purchases.orders.totalAmount')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('purchases.orders.createdAt')}
              </TableHead>
              <TableHead className="text-xs font-bold text-slate-700">
                {t('purchases.orders.receivedAt')}
              </TableHead>
              <TableHead className="text-right text-xs font-bold text-slate-700">
                {t('common.actions')}
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {orders.map((order) => {
              const isPending = order.status === 'PENDING'
              const isApproved = order.status === 'APPROVED'
              const isPartiallyReceived = order.status === 'PARTIALLY_RECEIVED'
              const canReceiveOrder =
                (isApproved || isPartiallyReceived) && canReceive
              const canCancelOrder =
                (isPending || isApproved || isPartiallyReceived) && canCancel
              const canApproveOrder = isPending && canApprove
              const canEditOrder = isPending && Boolean(onEdit)

              return (
                <TableRow
                  key={order.externalId}
                  className="hover:bg-slate-50/50 cursor-pointer"
                  onClick={() => onViewDetail(order)}
                >
                  <TableCell className="font-mono font-bold text-rose-700 text-xs">
                    {order.orderNumber}
                  </TableCell>
                  <TableCell className="text-xs font-semibold text-slate-900">
                    {order.supplier.name}
                  </TableCell>
                  <TableCell>{getStatusBadge(order.status)}</TableCell>
                  <TableCell className="text-right font-mono font-bold text-xs text-slate-900">
                    ${order.totalAmount.toFixed(2)}
                  </TableCell>
                  <TableCell className="text-xs text-slate-600">
                    {formatDate(order.createdAt)}
                  </TableCell>
                  <TableCell className="text-xs text-slate-600">
                    {formatDate(order.receivedAt)}
                  </TableCell>
                  <TableCell
                    className="text-right"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <div className="flex items-center justify-end gap-1">
                      {/* View detail button */}
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="h-7 w-7 p-0 text-slate-500 hover:text-slate-900"
                        onClick={() => onViewDetail(order)}
                        title={t('purchases.orders.viewDetail')}
                      >
                        <Eye className="h-3.5 w-3.5" />
                      </Button>

                      {/* Edit button (PENDING only) */}
                      {canEditOrder && (
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="h-7 w-7 p-0 text-slate-500 hover:text-slate-900"
                          onClick={() => onEdit?.(order)}
                          title={t('purchases.orders.editOrder')}
                        >
                          <Edit2 className="h-3.5 w-3.5" />
                        </Button>
                      )}

                      {/* Approve button (PENDING & manager/admin) */}
                      {canApproveOrder && (
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="h-7 w-7 p-0 text-sky-600 hover:bg-sky-50"
                          onClick={() => onApprove?.(order)}
                          title={t('purchases.orders.approveOrder')}
                        >
                          <CheckCircle2 className="h-3.5 w-3.5" />
                        </Button>
                      )}

                      {/* Receive button (APPROVED or PARTIALLY_RECEIVED) */}
                      {canReceiveOrder && (
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="h-7 w-7 p-0 text-emerald-600 hover:bg-emerald-50"
                          onClick={() => onReceive?.(order)}
                          title={t('purchases.orders.receiveOrder')}
                        >
                          <PackageCheck className="h-3.5 w-3.5" />
                        </Button>
                      )}

                      {/* Cancel button (PENDING, APPROVED, PARTIALLY_RECEIVED & manager/admin) */}
                      {canCancelOrder && (
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="h-7 w-7 p-0 text-rose-600 hover:bg-rose-50"
                          onClick={() => onCancel?.(order)}
                          title={t('purchases.orders.cancelOrder')}
                        >
                          <Ban className="h-3.5 w-3.5" />
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      <div className="flex items-center justify-between text-xs text-slate-500 px-1">
        <span>
          {t('common.showing')}{' '}
          <strong className="font-semibold text-slate-700">
            {totalElements > 0 ? page * size + 1 : 0}
          </strong>{' '}
          {t('common.to')}{' '}
          <strong className="font-semibold text-slate-700">
            {Math.min((page + 1) * size, totalElements)}
          </strong>{' '}
          {t('common.of')}{' '}
          <strong className="font-semibold text-slate-700">
            {totalElements}
          </strong>{' '}
          {t('common.results')}
        </span>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => onPageChange(page - 1)}
            className="h-7 text-xs px-2.5"
          >
            {t('common.previous')}
          </Button>
          <span className="font-medium">
            {page + 1} / {totalPages}
          </span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={page + 1 >= totalPages}
            onClick={() => onPageChange(page + 1)}
            className="h-7 text-xs px-2.5"
          >
            {t('common.next')}
          </Button>
        </div>
      </div>
    </div>
  )
}
