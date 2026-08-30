import * as React from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { AlertCircle, CheckCircle2, Clock } from 'lucide-react'
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
import { closePriceRequestSchema } from '../schemas/pricing.schema.ts'
import type { ClosePriceRequest, PriceResponse } from '../types/index.ts'
import { useClosePrice } from '../hooks/use-pricing.ts'

interface ClosePriceDialogProps {
  price: PriceResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: () => void
}

export function ClosePriceDialog({
  price,
  open,
  onOpenChange,
  onSuccess,
}: ClosePriceDialogProps) {
  const { t } = useTranslation()
  const [serverError, setServerError] = React.useState<string | null>(null)
  const [successMessage, setSuccessMessage] = React.useState<string | null>(null)

  const closePriceMutation = useClosePrice()

  const { register, handleSubmit, reset } = useForm<ClosePriceRequest>({
    resolver: zodResolver(closePriceRequestSchema),
    defaultValues: {
      validTo: '',
    },
  })

  React.useEffect(() => {
    if (open) {
      reset({ validTo: '' })
      setServerError(null)
      setSuccessMessage(null)
    }
  }, [open, reset])

  const onSubmit = (data: ClosePriceRequest) => {
    if (!price) return
    setServerError(null)

    const payload: ClosePriceRequest = {
      validTo: data.validTo || null,
    }

    closePriceMutation.mutate(
      { priceExternalId: price.externalId, input: payload },
      {
        onSuccess: () => {
          setSuccessMessage(t('common.success'))
          setTimeout(() => {
            onOpenChange(false)
            onSuccess?.()
          }, 600)
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
                <Clock className="h-4 w-4" />
              </div>
              <DialogTitle className="text-base font-bold text-slate-900">
                {t('pricing.rates.closeDialogTitle')}
              </DialogTitle>
            </div>
            <DialogDescription className="text-xs text-slate-500 pt-1">
              {t('pricing.rates.closeDialogDesc')}
            </DialogDescription>
          </DialogHeader>

          {price && (
            <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg text-xs space-y-1">
              <div className="flex justify-between">
                <span className="text-slate-500">{t('pricing.rates.currentRate')}:</span>
                <span className="font-mono font-bold text-slate-900">
                  ${price.unitPrice.toFixed(2)}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">{t('pricing.rates.validFrom')}:</span>
                <span className="text-slate-700">{price.validFrom ?? t('pricing.rates.creation')}</span>
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
            <Label htmlFor="close-valid-to" className="text-xs font-semibold">
              {t('pricing.rates.validTo')}
            </Label>
            <Input
              id="close-valid-to"
              type="date"
              {...register('validTo')}
              className="text-xs h-9"
            />
            <p className="text-[10px] text-slate-400">
              {t('pricing.rates.closeHelp')}
            </p>
          </div>

          <DialogFooter className="gap-2 pt-2 border-t border-slate-100">
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="text-xs"
              onClick={() => onOpenChange(false)}
              disabled={closePriceMutation.isPending}
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="submit"
              variant="destructive"
              size="sm"
              className="text-xs bg-rose-600 hover:bg-rose-700 text-white"
              disabled={closePriceMutation.isPending}
            >
              {closePriceMutation.isPending ? (
                <span>{t('common.saving')}</span>
              ) : (
                <span>{t('pricing.rates.closePrice')}</span>
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
