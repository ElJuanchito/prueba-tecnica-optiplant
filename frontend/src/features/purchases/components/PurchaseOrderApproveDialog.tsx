import * as React from 'react'
import { CheckCircle2, Info, Loader2 } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import type {
  PurchaseOrderDetailResponse,
  PurchaseOrderSummaryResponse,
} from '../types/index.ts'
import { useApprovePurchaseOrder } from '../hooks/use-purchases.ts'
import { ApiError } from '@/lib/api-client.ts'

interface PurchaseOrderApproveDialogProps {
  order?: PurchaseOrderSummaryResponse | PurchaseOrderDetailResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: (order: PurchaseOrderDetailResponse) => void
}

export function PurchaseOrderApproveDialog({
  order,
  open,
  onOpenChange,
  onSuccess,
}: PurchaseOrderApproveDialogProps) {
  const { t } = useTranslation()
  const [serverError, setServerError] = React.useState<string | null>(null)

  const approveMutation = useApprovePurchaseOrder()

  React.useEffect(() => {
    if (open) {
      setServerError(null)
    }
  }, [open])

  const handleConfirm = () => {
    if (!order) return
    setServerError(null)

    approveMutation.mutate(order.externalId, {
      onSuccess: (res) => {
        onOpenChange(false)
        onSuccess?.(res)
      },
      onError: (err) => {
        if (err instanceof ApiError && err.code) {
          const key = `purchases.errors.${err.code}`
          const translated = t(key)
          setServerError(translated !== key ? translated : err.message)
        } else {
          setServerError(err.message || t('common.error'))
        }
      },
    })
  }

  const isSubmitting = approveMutation.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <div className="flex items-center gap-2.5 text-sky-600 mb-1">
            <div className="h-9 w-9 rounded-xl bg-sky-50 border border-sky-200 flex items-center justify-center text-sky-600 shrink-0">
              <CheckCircle2 className="h-5 w-5" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold text-slate-900">
                {t('purchases.orders.approveOrder')}
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-500">
                {t('purchases.orders.confirmApprove')}
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {order && (
          <div className="my-2 p-3 bg-slate-50 rounded-xl border border-slate-200 text-xs flex items-center justify-between">
            <div>
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                {t('purchases.orders.orderNumber')}
              </span>
              <span className="font-mono font-bold text-rose-700">
                {order.orderNumber}
              </span>
            </div>
            <div className="text-right">
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                {t('purchases.orders.supplier')}
              </span>
              <span className="font-semibold text-slate-800">
                {order.supplier.name}
              </span>
            </div>
          </div>
        )}

        {serverError && (
          <div className="my-2">
            <Alert variant="destructive">
              <AlertTitle className="text-xs font-bold">
                {t('common.error')}
              </AlertTitle>
              <AlertDescription className="text-xs">
                {serverError}
              </AlertDescription>
            </Alert>
          </div>
        )}

        <div className="py-2 space-y-2">
          <div className="flex items-start gap-2.5 p-3 bg-sky-50/70 rounded-xl border border-sky-200 text-sky-900 text-xs">
            <Info className="h-4 w-4 text-sky-600 shrink-0 mt-0.5" />
            <div className="space-y-1">
              <p className="font-semibold leading-tight">
                Al aprobar esta orden de compra:
              </p>
              <ul className="list-disc list-inside text-[11px] text-sky-800 space-y-0.5">
                <li>El estado cambiará formalmente a <strong>APROBADA (APPROVED)</strong>.</li>
                <li>Se autorizará la recepción de ítems en bodega.</li>
                <li>Los precios y cantidades acordados quedarán registrados para liquidación.</li>
              </ul>
            </div>
          </div>
        </div>

        <DialogFooter className="gap-2 sm:gap-0 pt-3 border-t border-slate-100">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onOpenChange(false)}
            disabled={isSubmitting}
            className="text-xs"
          >
            {t('common.cancel')}
          </Button>
          <Button
            type="button"
            size="sm"
            onClick={handleConfirm}
            disabled={isSubmitting}
            className="text-xs bg-sky-600 hover:bg-sky-700 text-white font-semibold shadow-xs"
          >
            {isSubmitting ? (
              <>
                <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
                {t('purchases.orders.approving')}
              </>
            ) : (
              <>
                <CheckCircle2 className="h-3.5 w-3.5 mr-1.5" />
                {t('purchases.orders.approveOrder')}
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
