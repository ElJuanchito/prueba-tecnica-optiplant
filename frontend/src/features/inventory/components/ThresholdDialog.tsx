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
import { useSetThreshold } from '../hooks/use-inventory.ts'
import { setThresholdRequestSchema } from '../schemas/stock.schema.ts'
import type { SetThresholdRequest, StockLineResponse } from '../types/index.ts'
import { AlertCircle, AlertTriangle, Bell, CheckCircle2, Loader2 } from 'lucide-react'

interface ThresholdDialogProps {
  product: StockLineResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function ThresholdDialog({
  product,
  open,
  onOpenChange,
}: ThresholdDialogProps) {
  const thresholdMutation = useSetThreshold()
  const [serverError, setServerError] = React.useState<string | null>(null)
  const [successMsg, setSuccessMsg] = React.useState<string | null>(null)

  const {
    register,
    handleSubmit,
    watch,
    reset,
    setValue,
    formState: { errors },
  } = useForm<SetThresholdRequest>({
    resolver: zodResolver(setThresholdRequestSchema),
    defaultValues: {
      minStockThreshold: product?.minStockThreshold ?? 0,
    },
  })

  React.useEffect(() => {
    if (product && open) {
      setValue('minStockThreshold', product.minStockThreshold)
      setServerError(null)
      setSuccessMsg(null)
    }
  }, [product, open, setValue])

  const watchedThreshold = watch('minStockThreshold')
  const currentStock = product?.currentStock ?? 0
  const thresholdNum = Number(watchedThreshold) || 0
  const willTriggerAlert = currentStock <= thresholdNum && thresholdNum > 0

  const onSubmit = (data: SetThresholdRequest) => {
    if (!product) return

    setServerError(null)
    thresholdMutation.mutate(
      {
        productExternalId: product.productExternalId,
        input: data,
      },
      {
        onSuccess: (res) => {
          setSuccessMsg(
            `Minimum stock threshold updated to ${res.minStockThreshold} units.`,
          )
          setTimeout(() => {
            onOpenChange(false)
            reset()
          }, 1200)
        },
        onError: (err) => {
          setServerError(err.message || 'Failed to update minimum threshold')
        },
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md bg-white p-6 sm:rounded-xl">
        <DialogHeader>
          <div className="flex items-center space-x-2">
            <div className="h-8 w-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center border border-indigo-200">
              <Bell className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold text-slate-900">
                Configure Minimum Stock Threshold
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-500">
                Set replenishment safety threshold for this branch (CU-INV-07)
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {product && (
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 py-2">
            <div className="bg-slate-50 p-3 rounded-lg border border-slate-200 space-y-1.5">
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500">Product:</span>
                <span className="font-semibold text-slate-900 truncate max-w-[220px]">
                  {product.name}
                </span>
              </div>
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500">SKU:</span>
                <span className="font-mono text-slate-700">{product.sku}</span>
              </div>
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500">Current Stock:</span>
                <span className="font-bold text-slate-900 font-mono">
                  {product.currentStock} units
                </span>
              </div>
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500">Current Threshold:</span>
                <span className="font-mono text-slate-700">
                  {product.minStockThreshold} units
                </span>
              </div>
            </div>

            {serverError && (
              <Alert variant="destructive" className="py-2.5">
                <AlertCircle className="h-4 w-4" />
                <AlertTitle className="text-xs font-semibold">Error</AlertTitle>
                <AlertDescription className="text-xs">
                  {serverError}
                </AlertDescription>
              </Alert>
            )}

            {successMsg && (
              <Alert className="py-2.5 bg-emerald-50 border-emerald-200 text-emerald-800">
                <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                <AlertTitle className="text-xs font-semibold">Success</AlertTitle>
                <AlertDescription className="text-xs">
                  {successMsg}
                </AlertDescription>
              </Alert>
            )}

            <div className="space-y-1.5">
              <Label htmlFor="minStockThreshold" className="text-xs font-semibold text-slate-700">
                New Minimum Stock Threshold *
              </Label>
              <Input
                id="minStockThreshold"
                type="number"
                step="any"
                min="0"
                {...register('minStockThreshold', { valueAsNumber: true })}
                className="text-xs font-mono"
                placeholder="Enter minimum units threshold"
              />
              {errors.minStockThreshold && (
                <p className="text-[11px] text-rose-600 font-medium">
                  {errors.minStockThreshold.message}
                </p>
              )}
            </div>

            {willTriggerAlert && (
              <div className="p-3 rounded-lg border bg-amber-50 border-amber-200 text-amber-800 text-xs flex items-center gap-2.5">
                <AlertTriangle className="h-4 w-4 shrink-0 text-amber-600" />
                <p className="text-[11px] leading-relaxed">
                  <strong>Notice (R-15):</strong> Setting the threshold to {thresholdNum} is above current stock ({currentStock}). An automatic <code className="font-mono bg-amber-100 px-1 py-0.5 rounded text-[10px]">STOCK_MINIMUM</code> alert will be raised.
                </p>
              </div>
            )}

            <DialogFooter className="pt-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => onOpenChange(false)}
                disabled={thresholdMutation.isPending}
                className="text-xs"
              >
                Cancel
              </Button>
              <Button
                type="submit"
                size="sm"
                disabled={thresholdMutation.isPending || thresholdNum < 0}
                className="text-xs bg-indigo-600 hover:bg-indigo-700 text-white"
              >
                {thresholdMutation.isPending && (
                  <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
                )}
                Save Threshold
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
