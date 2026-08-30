import * as React from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { AlertCircle, Ban, CheckCircle2 } from 'lucide-react'
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
import { cancellationRequestSchema } from '../schemas/sale.schema.ts'
import type { CancellationRequest, SaleSummaryResponse } from '../types/index.ts'
import { useCancelSale } from '../hooks/use-sales.ts'

interface SaleCancelDialogProps {
  sale: SaleSummaryResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: () => void
}

export function SaleCancelDialog({
  sale,
  open,
  onOpenChange,
  onSuccess,
}: SaleCancelDialogProps) {
  const { t } = useTranslation()
  const [serverError, setServerError] = React.useState<string | null>(null)
  const [successMessage, setSuccessMessage] = React.useState<string | null>(null)

  const cancelMutation = useCancelSale()

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
      setSuccessMessage(null)
    }
  }, [open, reset])

  const onSubmit = (data: CancellationRequest) => {
    if (!sale) return
    setServerError(null)

    cancelMutation.mutate(
      { externalId: sale.externalId, input: data },
      {
        onSuccess: () => {
          setSuccessMessage(t('sales.cancel.success'))
          setTimeout(() => {
            onOpenChange(false)
            onSuccess?.()
          }, 800)
        },
        onError: (err) => {
          setServerError(err.message || t('common.error'))
        },
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <DialogHeader>
            <div className="flex items-center gap-2 text-rose-600">
              <div className="h-8 w-8 rounded-lg bg-rose-50 border border-rose-200 flex items-center justify-center">
                <Ban className="h-4 w-4" />
              </div>
              <DialogTitle className="text-base font-bold text-slate-900">
                {t('sales.cancel.title')}
              </DialogTitle>
            </div>
            <DialogDescription className="text-xs text-slate-500 pt-1">
              {t('sales.cancel.desc')}
            </DialogDescription>
          </DialogHeader>

          {sale && (
            <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg text-xs space-y-1">
              <div className="flex justify-between">
                <span className="text-slate-500">{t('sales.table.invoiceNumber')}:</span>
                <span className="font-mono font-bold text-slate-900">
                  {sale.invoiceNumber}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">{t('sales.table.customer')}:</span>
                <span className="font-semibold text-slate-800">{sale.customerName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">{t('sales.table.total')}:</span>
                <span className="font-mono font-bold text-slate-900">
                  ${sale.totalAmount.toFixed(2)}
                </span>
              </div>
            </div>
          )}

          {serverError && (
            <Alert variant="destructive" className="py-2 px-3 text-xs">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle className="text-xs font-semibold">
                {t('common.error')}
              </AlertTitle>
              <AlertDescription className="text-[11px]">
                {serverError}
              </AlertDescription>
            </Alert>
          )}

          {successMessage && (
            <Alert className="py-2 px-3 text-xs border-emerald-300 bg-emerald-50 text-emerald-900">
              <CheckCircle2 className="h-4 w-4 text-emerald-600" />
              <AlertTitle className="text-xs font-semibold">
                {t('common.success')}
              </AlertTitle>
              <AlertDescription className="text-[11px]">
                {successMessage}
              </AlertDescription>
            </Alert>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="cancellation-reason" className="text-xs font-semibold">
              {t('sales.cancel.reasonPrompt')}{' '}
              <span className="text-rose-500">*</span>
            </Label>
            <Input
              id="cancellation-reason"
              {...register('reason')}
              placeholder={t('sales.cancel.reasonPlaceholder')}
              className="text-xs h-9"
              autoFocus
            />
            {errors.reason && (
              <p className="text-[11px] font-medium text-rose-600">
                {errors.reason.message}
              </p>
            )}
          </div>

          <DialogFooter className="gap-2 pt-2 border-t border-slate-100">
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="text-xs"
              onClick={() => onOpenChange(false)}
              disabled={cancelMutation.isPending}
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="submit"
              variant="destructive"
              size="sm"
              className="text-xs bg-rose-600 hover:bg-rose-700 text-white"
              disabled={cancelMutation.isPending}
            >
              {cancelMutation.isPending ? (
                <span>{t('sales.cancel.confirming')}</span>
              ) : (
                <span className="flex items-center gap-1.5">
                  <Ban className="h-3.5 w-3.5" />
                  {t('sales.cancel.confirm')}
                </span>
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
