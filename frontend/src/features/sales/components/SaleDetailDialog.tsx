import {
  AlertTriangle,
  Ban,
  Building2,
  Calendar,
  DollarSign,
  FileText,
  Percent,
  Printer,
  Receipt,
  User,
} from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Badge } from '@/components/ui/badge.tsx'
import { Skeleton } from '@/components/ui/skeleton.tsx'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { useSaleDetail } from '../hooks/use-sales.ts'

interface SaleDetailDialogProps {
  saleExternalId: string | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onCancelSale?: (saleExternalId: string) => void
  canCancel?: boolean
}

export function SaleDetailDialog({
  saleExternalId,
  open,
  onOpenChange,
  onCancelSale,
  canCancel = false,
}: SaleDetailDialogProps) {
  const { t } = useTranslation()
  const detailQuery = useSaleDetail(saleExternalId ?? '', open && Boolean(saleExternalId))
  const sale = detailQuery.data

  const handlePrint = () => {
    window.print()
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <div className="flex items-center justify-between gap-4 mr-6">
            <div className="flex items-center gap-2.5">
              <div className="h-9 w-9 rounded-xl bg-teal-50 text-teal-700 flex items-center justify-center border border-teal-200">
                <Receipt className="h-5 w-5" />
              </div>
              <div>
                <DialogTitle className="text-base font-bold text-slate-900 flex items-center gap-2">
                  <span>{t('sales.detail.title')}</span>
                  {sale && (
                    <span className="font-mono text-teal-700 font-extrabold">
                      {sale.invoiceNumber}
                    </span>
                  )}
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-500">
                  {sale?.createdAt && (
                    <span className="flex items-center gap-1 mt-0.5">
                      <Calendar className="h-3 w-3" />
                      {new Date(sale.createdAt).toLocaleString(undefined, {
                        dateStyle: 'full',
                        timeStyle: 'medium',
                      })}
                    </span>
                  )}
                </DialogDescription>
              </div>
            </div>

            {sale && (
              <Badge
                variant="outline"
                className={`text-xs font-bold py-1 px-3 ${
                  sale.status === 'COMPLETED'
                    ? 'bg-emerald-50 text-emerald-800 border-emerald-300'
                    : 'bg-rose-50 text-rose-800 border-rose-300'
                }`}
              >
                {sale.status === 'COMPLETED'
                  ? t('sales.status.COMPLETED')
                  : t('sales.status.CANCELLED')}
              </Badge>
            )}
          </div>
        </DialogHeader>

        {detailQuery.isLoading ? (
          <div className="space-y-4 py-4">
            <Skeleton className="h-20 w-full rounded-xl" />
            <Skeleton className="h-40 w-full rounded-xl" />
            <Skeleton className="h-24 w-full rounded-xl" />
          </div>
        ) : sale ? (
          <div className="space-y-4 py-2">
            {/* Cancellation Banner */}
            {sale.status === 'CANCELLED' && (
              <div className="p-3.5 bg-rose-50 border border-rose-200 rounded-xl text-rose-900 flex items-start gap-3">
                <AlertTriangle className="h-5 w-5 text-rose-600 shrink-0 mt-0.5" />
                <div className="text-xs space-y-1">
                  <p className="font-bold tracking-wide uppercase">
                    {t('sales.detail.voidBanner')}
                  </p>
                  {sale.cancellationReason && (
                    <p className="text-slate-700">
                      <span className="font-semibold text-rose-800">
                        {t('sales.detail.cancellationReason')}:
                      </span>{' '}
                      {sale.cancellationReason}
                    </p>
                  )}
                </div>
              </div>
            )}

            {/* Metadata Summary Grid */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 p-3.5 bg-slate-50 border border-slate-200 rounded-xl text-xs">
              <div className="space-y-1">
                <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                  {t('sales.table.customer')}
                </span>
                <p className="font-semibold text-slate-900">{sale.customerName}</p>
                {sale.customerTaxId && (
                  <p className="text-slate-500 font-mono text-[11px]">
                    ID: {sale.customerTaxId}
                  </p>
                )}
                {sale.customer && (
                  <div className="pt-1 mt-1 border-t border-slate-200 text-[11px] text-teal-700">
                    <span className="font-semibold">{t('customers.customerLinked')}:</span>{' '}
                    <span>{sale.customer.name}</span>
                  </div>
                )}
              </div>

              <div className="space-y-1">
                <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                  {t('sales.table.branch')} & {t('sales.detail.seller')}
                </span>
                <div className="flex items-center gap-1.5 text-slate-700 font-medium">
                  <Building2 className="h-3.5 w-3.5 text-slate-400 shrink-0" />
                  <span>{sale.branch?.name ?? '—'}</span>
                </div>
                <div className="flex items-center gap-1.5 text-slate-600">
                  <User className="h-3.5 w-3.5 text-slate-400 shrink-0" />
                  <span>{sale.soldBy?.username ?? '—'}</span>
                </div>
              </div>

              <div className="space-y-1">
                <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                  {t('sales.table.priceList')}
                </span>
                <div className="flex items-center gap-1 text-slate-800 font-medium">
                  <TagIcon className="h-3.5 w-3.5 text-slate-400 shrink-0" />
                  <span>{sale.priceList?.code ?? '—'}</span>
                </div>
                {sale.priceList && (
                  <span className="text-[10px] text-slate-500">
                    {t('pricing.priceLists.maxDiscount')}: {sale.priceList.maxDiscountPercent}%
                  </span>
                )}
              </div>
            </div>

            {sale.notes && (
              <div className="px-3.5 py-2 bg-amber-50/70 border border-amber-200 rounded-lg text-xs text-amber-900 flex items-center gap-2">
                <FileText className="h-3.5 w-3.5 text-amber-600 shrink-0" />
                <span>
                  <strong>{t('sales.dialog.notes')}:</strong> {sale.notes}
                </span>
              </div>
            )}

            {/* Items Table */}
            <div className="rounded-xl border border-slate-200 overflow-hidden">
              <Table>
                <TableHeader className="bg-slate-50 border-b border-slate-200">
                  <TableRow>
                    <TableHead className="text-xs font-bold text-slate-700 h-9 px-3">
                      {t('sales.dialog.product')}
                    </TableHead>
                    <TableHead className="text-xs font-bold text-slate-700 h-9 px-3 text-right">
                      {t('sales.dialog.quantity')}
                    </TableHead>
                    <TableHead className="text-xs font-bold text-slate-700 h-9 px-3 text-right">
                      {t('sales.dialog.listPrice')}
                    </TableHead>
                    <TableHead className="text-xs font-bold text-slate-700 h-9 px-3 text-right">
                      {t('sales.dialog.discountPercent')}
                    </TableHead>
                    <TableHead className="text-xs font-bold text-slate-700 h-9 px-3 text-right">
                      {t('sales.dialog.unitPrice')}
                    </TableHead>
                    <TableHead className="text-xs font-bold text-slate-700 h-9 px-3 text-right">
                      {t('sales.dialog.subtotal')}
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sale.items.map((item) => (
                    <TableRow
                      key={item.externalId}
                      className="border-b border-slate-100 last:border-0 text-xs"
                    >
                      <TableCell className="py-2.5 px-3">
                        <div className="font-semibold text-slate-900">{item.name}</div>
                        <div className="font-mono text-[10px] text-slate-500">
                          {item.sku}
                        </div>
                      </TableCell>
                      <TableCell className="py-2.5 px-3 text-right font-medium">
                        {item.quantity}
                      </TableCell>
                      <TableCell className="py-2.5 px-3 text-right font-mono text-slate-500">
                        ${item.listUnitPrice.toFixed(2)}
                      </TableCell>
                      <TableCell className="py-2.5 px-3 text-right font-mono">
                        {item.discountPercent > 0 ? (
                          <span className="text-amber-700 font-semibold">
                            -{item.discountPercent}%
                          </span>
                        ) : (
                          <span className="text-slate-400">0%</span>
                        )}
                      </TableCell>
                      <TableCell className="py-2.5 px-3 text-right font-mono font-semibold text-slate-800">
                        ${item.unitPrice.toFixed(2)}
                      </TableCell>
                      <TableCell className="py-2.5 px-3 text-right font-mono font-bold text-slate-900">
                        ${item.subtotal.toFixed(2)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            {/* Financial Summary Card */}
            <div className="flex justify-end pt-2">
              <div className="w-72 bg-slate-50 border border-slate-200 rounded-xl p-3.5 space-y-2 text-xs">
                <div className="flex justify-between text-slate-600">
                  <span>{t('sales.dialog.subtotal')}</span>
                  <span className="font-mono font-semibold">${sale.subtotal.toFixed(2)}</span>
                </div>
                {sale.discountAmount > 0 && (
                  <div className="flex justify-between text-amber-700 font-medium">
                    <span className="flex items-center gap-1">
                      <Percent className="h-3 w-3" />
                      {t('sales.dialog.discountAmount')}
                    </span>
                    <span className="font-mono">-${sale.discountAmount.toFixed(2)}</span>
                  </div>
                )}
                {sale.taxAmount > 0 && (
                  <div className="flex justify-between text-slate-600">
                    <span>{t('sales.dialog.taxAmount')}</span>
                    <span className="font-mono font-semibold">${sale.taxAmount.toFixed(2)}</span>
                  </div>
                )}
                <div className="pt-2 border-t border-slate-200 flex justify-between text-sm font-bold text-slate-900">
                  <span className="flex items-center gap-1">
                    <DollarSign className="h-4 w-4 text-teal-600" />
                    {t('sales.dialog.totalAmount')}
                  </span>
                  <span className="font-mono text-base text-teal-700 font-extrabold">
                    ${sale.totalAmount.toFixed(2)}
                  </span>
                </div>
              </div>
            </div>
          </div>
        ) : null}

        <DialogFooter className="flex items-center justify-between gap-2 border-t border-slate-100 pt-3">
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="text-xs border-slate-300 text-slate-700"
              onClick={handlePrint}
            >
              <Printer className="h-3.5 w-3.5 mr-1" />
              {t('sales.detail.printReceipt')}
            </Button>
          </div>

          <div className="flex items-center gap-2">
            {sale && sale.status === 'COMPLETED' && canCancel && onCancelSale && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="text-xs border-rose-300 text-rose-700 hover:bg-rose-50"
                onClick={() => {
                  onOpenChange(false)
                  onCancelSale(sale.externalId)
                }}
              >
                <Ban className="h-3.5 w-3.5 mr-1" />
                {t('sales.detail.voidSale')}
              </Button>
            )}
            <Button
              type="button"
              variant="default"
              size="sm"
              className="text-xs bg-slate-900 hover:bg-slate-800 text-white"
              onClick={() => onOpenChange(false)}
            >
              {t('common.close')}
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function TagIcon(props: React.SVGProps<SVGSVGElement>) {
  return (
    <svg
      {...props}
      xmlns="http://www.w3.org/2000/svg"
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z" />
      <circle cx="7.5" cy="7.5" r=".5" fill="currentColor" />
    </svg>
  )
}
