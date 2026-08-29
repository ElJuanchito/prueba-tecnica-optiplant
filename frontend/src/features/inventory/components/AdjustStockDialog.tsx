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
import { useAdjustStock } from '../hooks/use-inventory.ts'
import { adjustStockRequestSchema } from '../schemas/movement.schema.ts'
import type { AdjustStockRequest, StockLineResponse } from '../types/index.ts'
import { AlertCircle, ArrowDownRight, ArrowUpRight, CheckCircle2, Loader2, Sliders } from 'lucide-react'

interface AdjustStockDialogProps {
  product: StockLineResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function AdjustStockDialog({
  product,
  open,
  onOpenChange,
}: AdjustStockDialogProps) {
  const adjustMutation = useAdjustStock()
  const [serverError, setServerError] = React.useState<string | null>(null)
  const [successReceipt, setSuccessReceipt] = React.useState<string | null>(null)

  const {
    register,
    handleSubmit,
    watch,
    reset,
    setValue,
    formState: { errors },
  } = useForm<AdjustStockRequest>({
    resolver: zodResolver(adjustStockRequestSchema),
    defaultValues: {
      productExternalId: product?.productExternalId ?? '',
      countedQuantity: product?.currentStock ?? 0,
      reason: '',
    },
  })

  React.useEffect(() => {
    if (product && open) {
      setValue('productExternalId', product.productExternalId)
      setValue('countedQuantity', product.currentStock)
      setValue('reason', '')
      setServerError(null)
      setSuccessReceipt(null)
    }
  }, [product, open, setValue])

  const watchedCounted = watch('countedQuantity')
  const currentStock = product?.currentStock ?? 0
  const countNum = Number(watchedCounted) || 0
  const diff = countNum - currentStock
  const isNoDifference = countNum === currentStock

  const onSubmit = (data: AdjustStockRequest) => {
    if (data.countedQuantity === currentStock) {
      setServerError(
        'Adjustment requires a physical discrepancy. Count cannot equal current theoretical stock (R-08).',
      )
      return
    }

    setServerError(null)
    adjustMutation.mutate(data, {
      onSuccess: (receipt) => {
        setSuccessReceipt(
          `Adjustment recorded successfully. Movement ${receipt.movementType} applied. Resulting balance: ${receipt.resultingStock}`,
        )
        setTimeout(() => {
          onOpenChange(false)
          reset()
        }, 1500)
      },
      onError: (err) => {
        setServerError(err.message || 'Failed to register stock adjustment')
      },
    })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md bg-white p-6 sm:rounded-xl">
        <DialogHeader>
          <div className="flex items-center space-x-2">
            <div className="h-8 w-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center border border-amber-200">
              <Sliders className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold text-slate-900">
                Physical Inventory Adjustment
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-500">
                Reconcile theoretical balance with verified physical count (CU-INV-05)
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
                <span className="text-slate-500">Current Theoretical Balance:</span>
                <span className="font-bold text-slate-900 font-mono">
                  {product.currentStock} units
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

            {successReceipt && (
              <Alert className="py-2.5 bg-emerald-50 border-emerald-200 text-emerald-800">
                <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                <AlertTitle className="text-xs font-semibold">Success</AlertTitle>
                <AlertDescription className="text-xs">
                  {successReceipt}
                </AlertDescription>
              </Alert>
            )}

            <div className="space-y-1.5">
              <Label htmlFor="countedQuantity" className="text-xs font-semibold text-slate-700">
                Counted Physical Quantity *
              </Label>
              <Input
                id="countedQuantity"
                type="number"
                step="any"
                min="0"
                {...register('countedQuantity', { valueAsNumber: true })}
                className="text-xs font-mono"
                placeholder="Enter verified physical count"
              />
              {errors.countedQuantity && (
                <p className="text-[11px] text-rose-600 font-medium">
                  {errors.countedQuantity.message}
                </p>
              )}
            </div>

            {/* Live Difference Calculation Banner */}
            <div
              className={`p-3 rounded-lg border text-xs flex items-center justify-between ${
                isNoDifference
                  ? 'bg-slate-50 border-slate-200 text-slate-600'
                  : diff > 0
                    ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
                    : 'bg-amber-50 border-amber-200 text-amber-800'
              }`}
            >
              <div className="flex items-center gap-2">
                {isNoDifference ? (
                  <Sliders className="h-4 w-4 text-slate-400" />
                ) : diff > 0 ? (
                  <ArrowUpRight className="h-4 w-4 text-emerald-600" />
                ) : (
                  <ArrowDownRight className="h-4 w-4 text-amber-600" />
                )}
                <div>
                  <p className="font-semibold">
                    {isNoDifference
                      ? 'No difference detected'
                      : diff > 0
                        ? `Positive Adjustment (+${diff})`
                        : `Negative Adjustment (${diff})`}
                  </p>
                  <p className="text-[10px] opacity-80">
                    {isNoDifference
                      ? 'Enter a count different from current stock'
                      : diff > 0
                        ? 'ADJUSTMENT_POS will increment stock'
                        : 'ADJUSTMENT_NEG will decrement stock'}
                  </p>
                </div>
              </div>
              <span className="font-mono font-bold text-sm">
                {diff > 0 ? `+${diff}` : diff}
              </span>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="reason" className="text-xs font-semibold text-slate-700">
                Justification Reason (Mandatory) *
              </Label>
              <Input
                id="reason"
                type="text"
                {...register('reason')}
                className="text-xs"
                placeholder="e.g. Annual physical count variance, missing item found"
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
                disabled={adjustMutation.isPending}
                className="text-xs"
              >
                Cancel
              </Button>
              <Button
                type="submit"
                size="sm"
                disabled={adjustMutation.isPending || isNoDifference}
                className="text-xs bg-amber-600 hover:bg-amber-700 text-white"
              >
                {adjustMutation.isPending && (
                  <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
                )}
                Confirm Adjustment
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
