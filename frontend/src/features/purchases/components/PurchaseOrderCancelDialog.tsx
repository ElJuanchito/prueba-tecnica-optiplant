import * as React from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { AlertTriangle, Ban } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Input } from '@/components/ui/input.tsx'
import { Label } from '@/components/ui/label.tsx'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { cancellationRequestSchema } from '../schemas/purchases.schema.ts'
import type {
  CancellationRequest,
  PurchaseOrderDetailResponse,
  PurchaseOrderSummaryResponse,
} from '../types/index.ts'
import { useCancelPurchaseOrder } from '../hooks/use-purchases.ts'
import { ApiError } from '@/lib/api-client.ts'

interface PurchaseOrderCancelDialogProps {
  order?: PurchaseOrderSummaryResponse | PurchaseOrderDetailResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: (order: PurchaseOrderDetailResponse) => void
}

export function PurchaseOrderCancelDialog({
  order,
  open,
  onOpenChange,
  onSuccess,
}: PurchaseOrderCancelDialogProps) {
  const { t } = useTranslation()
  const [serverError, setServerError] = React.useState<string | null>(null)

  const cancelMutation = useCancelPurchaseOrder()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CancellationRequest>({
    resolver: zodResolver(cancellationRequestSchema),
    defaultValues: {
      reason: '',
    },
  })

  React.useEffect(() => {
    if (open) {
      reset({ reason: '' })
      setServerError(null)
    }
  }, [open, reset])

  const onSubmit = (data: CancellationRequest) => {
    if (!order) return
    setServerError(null)

    cancelMutation.mutate(
      {
        externalId: order.externalId,
        input: { reason: data.reason.trim() },
      },
      {
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
      },
    )
  }

  const isSubmitting = cancelMutation.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogHeader>
            <div className="flex items-center gap-2 text-rose-600 mb-1">
              <Ban className="h-5 w-5" />
              <DialogTitle className="text-base font-bold text-slate-900">
                {t('purchases.cancelDialog.title')}
              </DialogTitle>
            </div>
            <DialogDescription className="text-xs text-slate-500">
              {t('purchases.cancelDialog.desc')}
            </DialogDescription>
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

          <div className="space-y-3 py-3">
            <div className="space-y-1">
              <Label
                htmlFor="reason"
                className="text-xs font-semibold text-slate-700"
              >
                {t('purchases.cancelDialog.reason')}{' '}
                <span className="text-rose-500">*</span>
              </Label>
              <Input
                id="reason"
                placeholder={t('purchases.cancelDialog.reasonPlaceholder')}
                {...register('reason')}
                className="text-xs h-9 bg-slate-50 border-slate-200"
              />
              {errors.reason && (
                <p className="text-[11px] text-rose-600 font-medium">
                  {errors.reason.message}
                </p>
              )}
            </div>

            <div className="flex items-start gap-2 p-2.5 bg-amber-50 rounded-lg border border-amber-200 text-amber-900 text-[11px]">
              <AlertTriangle className="h-4 w-4 text-amber-600 shrink-0 mt-0.5" />
              <span>
                Esta acción no se puede deshacer. La orden pasará a estado{' '}
                <strong>CANCELLED</strong>.
              </span>
            </div>
          </div>

          <DialogFooter className="gap-2 sm:gap-0 pt-2 border-t border-slate-100">
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
              type="submit"
              size="sm"
              disabled={isSubmitting}
              className="text-xs bg-rose-600 hover:bg-rose-700 text-white font-semibold shadow-xs"
            >
              {isSubmitting
                ? t('purchases.cancelDialog.cancelling')
                : t('purchases.cancelDialog.confirm')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
