import * as React from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert.tsx'
import { Button } from '@/components/ui/button.tsx'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog.tsx'
import { Input } from '@/components/ui/input.tsx'
import { Label } from '@/components/ui/label.tsx'
import { useWriteOffStock } from '../hooks/use-inventory.ts'
import { writeOffRequestSchema } from '../schemas/movement.schema.ts'
import type { StockLineResponse, WriteOffRequest } from '../types/index.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  AlertCircle,
  CheckCircle2,
  DollarSign,
  Loader2,
  Trash2,
} from 'lucide-react'

interface WriteOffDialogProps {
  product: StockLineResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function WriteOffDialog({
  product,
  open,
  onOpenChange,
}: WriteOffDialogProps) {
  const { t } = useTranslation()
  const writeOffMutation = useWriteOffStock()
  const [serverError, setServerError] = React.useState<string | null>(null)
  const [successReceipt, setSuccessReceipt] = React.useState<string | null>(
    null,
  )

  const {
    register,
    handleSubmit,
    watch,
    reset,
    setValue,
    formState: { errors },
  } = useForm<WriteOffRequest>({
    resolver: zodResolver(writeOffRequestSchema),
    defaultValues: {
      productExternalId: product?.productExternalId ?? '',
      quantity: 1,
      reason: '',
    },
  })

  React.useEffect(() => {
    if (product && open) {
      setValue('productExternalId', product.productExternalId)
      setValue('quantity', 1)
      setValue('reason', '')
      setServerError(null)
      setSuccessReceipt(null)
    }
  }, [product, open, setValue])

  const watchedQty = watch('quantity')
  const availableStock = product?.availableStock ?? 0
  const avgCost = product?.averageCost ?? 0
  const qtyNum = Number(watchedQty) || 0
  const isOverAvailable = qtyNum > availableStock
  const estimatedLoss = (qtyNum * avgCost).toFixed(2)

  const onSubmit = (data: WriteOffRequest) => {
    if (data.quantity > availableStock) {
      setServerError(
        `Quantity to write off (${data.quantity}) exceeds available stock (${availableStock}). Negative stock is strictly prohibited (RN-01).`,
      )
      return
    }

    setServerError(null)
    writeOffMutation.mutate(data, {
      onSuccess: (receipt) => {
        setSuccessReceipt(
          `Write-off registered successfully. Movement DAMAGE_WASTE applied for ${receipt.quantity} units. Resulting balance: ${receipt.resultingStock}`,
        )
        setTimeout(() => {
          onOpenChange(false)
          reset()
        }, 1500)
      },
      onError: (err) => {
        setServerError(err.message || 'Failed to register write-off')
      },
    })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md bg-white p-6 sm:rounded-xl">
        <DialogHeader>
          <div className="flex items-center space-x-2">
            <div className="h-8 w-8 rounded-lg bg-rose-50 text-rose-600 flex items-center justify-center border border-rose-200">
              <Trash2 className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold text-slate-900">
                {t('inventory.writeOffTitle')}
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-500">
                {t('inventory.writeOffDesc')}
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {product && (
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 py-2">
            <div className="bg-slate-50 p-3 rounded-lg border border-slate-200 space-y-1.5">
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500">{t('inventory.product')}:</span>
                <span className="font-semibold text-slate-900 truncate max-w-[220px]">
                  {product.name}
                </span>
              </div>
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500">{t('catalog.sku')}:</span>
                <span className="font-mono text-slate-700">{product.sku}</span>
              </div>
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500">{t('inventory.availableStock')}:</span>
                <span className="font-bold text-emerald-700 font-mono">
                  {product.availableStock}
                </span>
              </div>
            </div>

            {serverError && (
              <Alert variant="destructive" className="py-2.5">
                <AlertCircle className="h-4 w-4" />
                <AlertTitle className="text-xs font-semibold">{t('common.error')}</AlertTitle>
                <AlertDescription className="text-xs">
                  {serverError}
                </AlertDescription>
              </Alert>
            )}

            {successReceipt && (
              <Alert className="py-2.5 bg-emerald-50 border-emerald-200 text-emerald-800">
                <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                <AlertTitle className="text-xs font-semibold">
                  {t('common.active')}
                </AlertTitle>
                <AlertDescription className="text-xs">
                  {successReceipt}
                </AlertDescription>
              </Alert>
            )}

            <div className="space-y-1.5">
              <Label
                htmlFor="quantity"
                className="text-xs font-semibold text-slate-700"
              >
                {t('inventory.unitsToWriteOff')} *
              </Label>
              <Input
                id="quantity"
                type="number"
                step="any"
                min="0.0001"
                max={availableStock}
                {...register('quantity', { valueAsNumber: true })}
                className="text-xs font-mono"
                placeholder={t('inventory.unitsToWriteOff')}
              />
              {errors.quantity && (
                <p className="text-[11px] text-rose-600 font-medium">
                  {errors.quantity.message}
                </p>
              )}
            </div>

            {/* Financial Loss Valuation Banner */}
            <div className="p-3 rounded-lg border bg-rose-50/70 border-rose-200 text-xs flex items-center justify-between">
              <div className="flex items-center gap-2">
                <DollarSign className="h-4 w-4 text-rose-600" />
                <div>
                  <p className="font-semibold text-rose-900">
                    {t('inventory.stockValuation')}
                  </p>
                  <p className="text-[10px] text-rose-700">
                    ${avgCost.toFixed(2)} / unit
                  </p>
                </div>
              </div>
              <span className="font-mono font-bold text-sm text-rose-900">
                ${estimatedLoss}
              </span>
            </div>

            <div className="space-y-1.5">
              <Label
                htmlFor="reason"
                className="text-xs font-semibold text-slate-700"
              >
                {t('inventory.writeOffReason')} *
              </Label>
              <Input
                id="reason"
                type="text"
                {...register('reason')}
                className="text-xs"
                placeholder={t('inventory.writeOffReason')}
              />
              {errors.reason && (
                <p className="text-[11px] text-rose-600 font-medium">
                  {errors.reason.message}
                </p>
              )}
            </div>

            <DialogFooter className="pt-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => onOpenChange(false)}
                disabled={writeOffMutation.isPending}
                className="text-xs cursor-pointer"
              >
                {t('common.cancel')}
              </Button>
              <Button
                type="submit"
                size="sm"
                disabled={
                  writeOffMutation.isPending || isOverAvailable || qtyNum <= 0
                }
                className="text-xs bg-rose-600 hover:bg-rose-700 text-white cursor-pointer"
              >
                {writeOffMutation.isPending && (
                  <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
                )}
                {t('inventory.confirmWriteOff')}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
