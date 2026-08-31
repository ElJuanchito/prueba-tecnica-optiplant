import {
  AlertTriangle,
  Ban,
  Building2,
  Calendar,
  CheckCircle2,
  Edit2,
  PackageCheck,
  Printer,
  ShoppingBag,
  User,
} from 'lucide-react'
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { usePurchaseOrderDetail } from '../hooks/use-purchases.ts'
import type { PurchaseOrderStatus } from '../types/index.ts'
import { PurchaseOrderPrintDocument } from './PurchaseOrderPrintDocument.tsx'

interface PurchaseOrderDetailDialogProps {
  orderExternalId: string | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onEdit?: ((orderExternalId: string) => void) | undefined
  onApprove?: ((orderExternalId: string) => void) | undefined
  onReceive?: ((orderExternalId: string) => void) | undefined
  onCancel?: ((orderExternalId: string) => void) | undefined
  canApprove: boolean
  canReceive: boolean
  canCancel: boolean
}

export function PurchaseOrderDetailDialog({
  orderExternalId,
  open,
  onOpenChange,
  onEdit,
  onApprove,
  onReceive,
  onCancel,
  canApprove,
  canReceive,
  canCancel,
}: PurchaseOrderDetailDialogProps) {
  const { t } = useTranslation()

  const orderQuery = usePurchaseOrderDetail(
    orderExternalId ?? '',
    Boolean(orderExternalId) && open,
  )
  const order = orderQuery.data

  const getStatusBadge = (status: PurchaseOrderStatus) => {
    switch (status) {
      case 'PENDING':
        return (
          <Badge
            variant="outline"
            className="text-xs font-bold border-amber-300 bg-amber-50 text-amber-800"
          >
            {t('purchases.status.PENDING')}
          </Badge>
        )
      case 'APPROVED':
        return (
          <Badge
            variant="outline"
            className="text-xs font-bold border-sky-300 bg-sky-50 text-sky-800"
          >
            {t('purchases.status.APPROVED')}
          </Badge>
        )
      case 'PARTIALLY_RECEIVED':
        return (
          <Badge
            variant="outline"
            className="text-xs font-bold border-indigo-300 bg-indigo-50 text-indigo-800"
          >
            {t('purchases.status.PARTIALLY_RECEIVED')}
          </Badge>
        )
      case 'RECEIVED':
        return (
          <Badge
            variant="outline"
            className="text-xs font-bold border-emerald-300 bg-emerald-50 text-emerald-800"
          >
            {t('purchases.status.RECEIVED')}
          </Badge>
        )
      case 'CANCELLED':
        return (
          <Badge
            variant="outline"
            className="text-xs font-bold border-rose-300 bg-rose-50 text-rose-800"
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
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return isoString
    }
  }

  const handlePrint = () => {
    window.print()
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-4xl max-h-[90vh] overflow-y-auto">
        <DialogHeader className="border-b border-slate-100 pb-3">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div className="flex items-center gap-2.5">
              <div className="h-9 w-9 rounded-xl bg-rose-600 text-white flex items-center justify-center shadow-xs shrink-0">
                <ShoppingBag className="h-5 w-5" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <DialogTitle className="text-base font-bold text-slate-900">
                    {t('purchases.detailDialog.title')}
                  </DialogTitle>
                  {order && (
                    <span className="font-mono font-bold text-rose-700 bg-rose-50 px-2 py-0.5 rounded text-xs">
                      {order.orderNumber}
                    </span>
                  )}
                </div>
                <DialogDescription className="text-xs text-slate-500">
                  {order?.supplier.name}
                </DialogDescription>
              </div>
            </div>

            {order && <div>{getStatusBadge(order.status)}</div>}
          </div>
        </DialogHeader>

        {orderQuery.isLoading ? (
          <div className="py-16 text-center">
            <div className="inline-flex h-8 w-8 animate-spin rounded-full border-2 border-slate-300 border-t-rose-600" />
            <p className="mt-2 text-xs text-slate-500">{t('common.loading')}</p>
          </div>
        ) : !order ? (
          <div className="py-12 text-center text-xs text-slate-500">
            {t('common.noData')}
          </div>
        ) : (
          <div className="space-y-4 py-2">
            {/* Status Banners */}
            {order.status === 'CANCELLED' && (
              <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl flex items-start gap-2.5 text-rose-900">
                <AlertTriangle className="h-5 w-5 text-rose-600 shrink-0 mt-0.5" />
                <div className="text-xs space-y-1">
                  <span className="font-bold block">
                    {t('purchases.detailDialog.cancelBanner')}
                  </span>
                  {order.cancellationReason && (
                    <p className="text-rose-700">
                      <strong>
                        {t('purchases.detailDialog.cancellationReason')}:
                      </strong>{' '}
                      {order.cancellationReason}
                    </p>
                  )}
                </div>
              </div>
            )}

            {order.status === 'RECEIVED' && (
              <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl flex items-center gap-2.5 text-emerald-900 text-xs font-semibold">
                <CheckCircle2 className="h-5 w-5 text-emerald-600 shrink-0" />
                <span>{t('purchases.detailDialog.receivedBanner')}</span>
              </div>
            )}

            {order.status === 'PARTIALLY_RECEIVED' && (
              <div className="p-3 bg-indigo-50 border border-indigo-200 rounded-xl flex items-center gap-2.5 text-indigo-900 text-xs font-semibold">
                <PackageCheck className="h-5 w-5 text-indigo-600 shrink-0" />
                <span>
                  {t('purchases.detailDialog.partiallyReceivedBanner')}
                </span>
              </div>
            )}

            {/* Info Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3 p-3.5 bg-slate-50 rounded-xl border border-slate-200 text-xs">
              {/* Supplier Info */}
              <div className="space-y-1">
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                  {t('purchases.detailDialog.supplierInfo')}
                </span>
                <p className="font-bold text-slate-900">
                  {order.supplier.name}
                </p>
                {order.supplier.taxId && (
                  <p className="font-mono text-[11px] text-slate-500">
                    NIT/RUC: {order.supplier.taxId}
                  </p>
                )}
              </div>

              {/* Branch & User */}
              <div className="space-y-1">
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                  {t('purchases.detailDialog.branch')} /{' '}
                  {t('purchases.detailDialog.createdBy')}
                </span>
                <p className="text-slate-800 flex items-center gap-1">
                  <Building2 className="h-3.5 w-3.5 text-slate-400" />
                  {order.branch?.name || '—'}
                </p>
                <p className="text-slate-600 flex items-center gap-1">
                  <User className="h-3.5 w-3.5 text-slate-400" />
                  {order.createdBy?.username || '—'}
                </p>
              </div>

              {/* Dates & Terms */}
              <div className="space-y-1">
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                  {t('purchases.orders.createdAt')} /{' '}
                  {t('purchases.detailDialog.paymentTerms')}
                </span>
                <p className="text-slate-800 flex items-center gap-1">
                  <Calendar className="h-3.5 w-3.5 text-slate-400" />
                  {formatDate(order.createdAt)}
                </p>
                <p className="text-slate-600 font-medium truncate">
                  {order.paymentTerms || (
                    <span className="text-slate-400 italic">—</span>
                  )}
                </p>
              </div>
            </div>

            {/* Notes if any */}
            {order.notes && (
              <div className="p-3 bg-white border border-slate-200 rounded-xl text-xs space-y-1">
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                  {t('purchases.orderDialog.notes')}
                </span>
                <p className="text-slate-700">{order.notes}</p>
              </div>
            )}

            {/* Items Table */}
            <div className="space-y-2">
              <span className="text-xs font-bold text-slate-800 uppercase tracking-wider">
                {t('purchases.detailDialog.itemsList')} ({order.items.length})
              </span>

              <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xs">
                <Table>
                  <TableHeader>
                    <TableRow className="bg-slate-50/75 hover:bg-slate-50/75">
                      <TableHead className="text-xs font-bold text-slate-700">
                        {t('purchases.orderDialog.product')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('purchases.detailDialog.orderedQty')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('purchases.detailDialog.receivedQty')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('purchases.detailDialog.pendingQty')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('purchases.detailDialog.unitCost')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('purchases.detailDialog.discount')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('purchases.detailDialog.effectiveCost')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('purchases.detailDialog.subtotal')}
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {order.items.map((item) => (
                      <TableRow key={item.externalId}>
                        <TableCell className="text-xs">
                          <div className="font-semibold text-slate-900">
                            {item.name}
                          </div>
                          <div className="font-mono text-[10px] text-rose-700">
                            {item.sku}
                          </div>
                        </TableCell>
                        <TableCell className="text-right font-mono font-bold text-xs text-slate-900">
                          {item.orderedQuantity}
                        </TableCell>
                        <TableCell className="text-right font-mono text-xs text-emerald-700 font-semibold">
                          {item.receivedQuantity}
                        </TableCell>
                        <TableCell className="text-right font-mono text-xs text-amber-700 font-semibold">
                          {item.pendingQuantity}
                        </TableCell>
                        <TableCell className="text-right font-mono text-xs text-slate-600">
                          ${item.unitCost.toFixed(2)}
                        </TableCell>
                        <TableCell className="text-right font-mono text-xs text-slate-600">
                          {item.discountPercent > 0
                            ? `${item.discountPercent}%`
                            : '—'}
                        </TableCell>
                        <TableCell className="text-right font-mono font-bold text-xs text-slate-800">
                          ${item.effectiveUnitCost.toFixed(2)}
                        </TableCell>
                        <TableCell className="text-right font-mono font-black text-xs text-slate-900">
                          ${item.subtotal.toFixed(2)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>

              {/* Total Amount Summary */}
              <div className="p-4 bg-slate-900 text-white rounded-xl flex items-center justify-between shadow-2xs">
                <span className="text-xs font-bold uppercase tracking-wider text-slate-300">
                  {t('purchases.detailDialog.totalAmount')}
                </span>
                <span className="text-xl font-black font-mono">
                  ${order.totalAmount.toFixed(2)}
                </span>
              </div>
            </div>

            {/* Specialized Print Document (Visible only when printing) */}
            <PurchaseOrderPrintDocument order={order} />
          </div>
        )}

        <DialogFooter className="flex flex-col sm:flex-row items-center justify-between gap-2 border-t border-slate-100 pt-3">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handlePrint}
            className="text-xs border-slate-200"
          >
            <Printer className="h-3.5 w-3.5 mr-1.5 text-slate-500" />
            Imprimir
          </Button>

          <div className="flex items-center gap-2">
            {order?.status === 'PENDING' && onEdit && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="text-xs"
                onClick={() => {
                  onOpenChange(false)
                  onEdit(order.externalId)
                }}
              >
                <Edit2 className="h-3.5 w-3.5 mr-1 text-slate-500" />
                {t('purchases.orders.editOrder')}
              </Button>
            )}

            {order?.status === 'PENDING' && canApprove && onApprove && (
              <Button
                type="button"
                size="sm"
                className="text-xs bg-sky-600 hover:bg-sky-700 text-white font-semibold shadow-xs"
                onClick={() => {
                  onOpenChange(false)
                  onApprove(order.externalId)
                }}
              >
                <CheckCircle2 className="h-3.5 w-3.5 mr-1" />
                {t('purchases.orders.approveOrder')}
              </Button>
            )}

            {(order?.status === 'APPROVED' ||
              order?.status === 'PARTIALLY_RECEIVED') &&
              canReceive &&
              onReceive && (
                <Button
                  type="button"
                  size="sm"
                  className="text-xs bg-emerald-600 hover:bg-emerald-700 text-white font-semibold shadow-xs"
                  onClick={() => {
                    onOpenChange(false)
                    onReceive(order.externalId)
                  }}
                >
                  <PackageCheck className="h-3.5 w-3.5 mr-1" />
                  {t('purchases.orders.receiveOrder')}
                </Button>
              )}

            {(order?.status === 'PENDING' ||
              order?.status === 'APPROVED' ||
              order?.status === 'PARTIALLY_RECEIVED') &&
              canCancel &&
              onCancel && (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="text-xs text-rose-600 hover:bg-rose-50"
                  onClick={() => {
                    onOpenChange(false)
                    onCancel(order.externalId)
                  }}
                >
                  <Ban className="h-3.5 w-3.5 mr-1" />
                  {t('purchases.orders.cancelOrder')}
                </Button>
              )}

            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onOpenChange(false)}
              className="text-xs"
            >
              {t('common.close')}
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
