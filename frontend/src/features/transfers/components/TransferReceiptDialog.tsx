import * as React from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useFieldArray, useForm } from 'react-hook-form'
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
import { useReceiveTransfer } from '../hooks/use-transfers.ts'
import { receiptRequestSchema } from '../schemas/transfer.schema.ts'
import type {
  ReceiptRequest,
  TransferDetailResponse,
} from '../types/transfer.types.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  Inbox,
  Loader2,
} from 'lucide-react'

interface TransferReceiptDialogProps {
  transfer: TransferDetailResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function TransferReceiptDialog({
  transfer,
  open,
  onOpenChange,
}: TransferReceiptDialogProps) {
  const { t } = useTranslation()
  const receiveMutation = useReceiveTransfer()

  const [serverError, setServerError] = React.useState<string | null>(null)
  const [successMessage, setSuccessMessage] = React.useState<string | null>(
    null,
  )

  const { register, control, handleSubmit, watch, reset } =
    useForm<ReceiptRequest>({
      resolver: zodResolver(receiptRequestSchema),
      defaultValues: {
        items: [],
      },
    })

  const { fields } = useFieldArray({
    control,
    name: 'items',
  })

  React.useEffect(() => {
    if (transfer && open) {
      reset({
        items: transfer.items.map((i) => ({
          itemExternalId: i.externalId,
          receivedQuantity: i.dispatchedQuantity ?? i.requestedQuantity,
          discrepancyReason: '',
        })),
      })
      setServerError(null)
      setSuccessMessage(null)
    }
  }, [transfer, open, reset])

  const watchedItems = watch('items')

  // Calculate live discrepancies
  let hasAnyDiscrepancy = false
  let totalDiscrepancy = 0

  if (transfer && watchedItems) {
    watchedItems.forEach((wItem) => {
      const original = transfer.items.find(
        (i) => i.externalId === wItem.itemExternalId,
      )
      const dispatched =
        original?.dispatchedQuantity ?? original?.requestedQuantity ?? 0
      const received = Number(wItem.receivedQuantity) || 0
      if (received < dispatched) {
        hasAnyDiscrepancy = true
        totalDiscrepancy += dispatched - received
      }
    })
  }

  const onSubmit = (data: ReceiptRequest) => {
    if (!transfer) return
    setServerError(null)

    // Verify discrepancy reasons are provided when received < dispatched (R-18)
    for (const item of data.items) {
      const original = transfer.items.find(
        (i) => i.externalId === item.itemExternalId,
      )
      const dispatched =
        original?.dispatchedQuantity ?? original?.requestedQuantity ?? 0
      if (
        item.receivedQuantity < dispatched &&
        (!item.discrepancyReason || item.discrepancyReason.trim() === '')
      ) {
        setServerError(
          `Discrepancy reason is required for item ${original?.sku} because received quantity (${item.receivedQuantity}) is less than dispatched (${dispatched}).`,
        )
        return
      }
      if (item.receivedQuantity > dispatched) {
        setServerError(
          `Over-receipt is refused. Received quantity (${item.receivedQuantity}) cannot exceed dispatched quantity (${dispatched}) for item ${original?.sku} (R-19).`,
        )
        return
      }
    }

    receiveMutation.mutate(
      { externalId: transfer.externalId, input: data },
      {
        onSuccess: (res) => {
          setSuccessMessage(
            `Transfer ${res.transferNumber} received successfully. Outcome: ${res.status}.`,
          )
          setTimeout(() => {
            onOpenChange(false)
          }, 1500)
        },
        onError: (err) => {
          setServerError(err.message || 'Failed to confirm transfer receipt')
        },
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl bg-white p-6 sm:rounded-xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <div className="flex items-center space-x-2">
            <div className="h-8 w-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center border border-emerald-200">
              <Inbox className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold text-slate-900">
                {t('transfers.dialogs.receiptTitle')}
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-500">
                {transfer?.transferNumber} · {t('transfers.destinationBranch')}:{' '}
                <span className="font-semibold text-slate-700">
                  {transfer?.destinationBranch?.name}
                </span>
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {serverError && (
          <Alert variant="destructive" className="py-2.5 my-2">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle className="text-xs font-semibold">Error</AlertTitle>
            <AlertDescription className="text-xs">
              {serverError}
            </AlertDescription>
          </Alert>
        )}

        {successMessage && (
          <Alert className="py-2.5 my-2 bg-emerald-50 border-emerald-200 text-emerald-800">
            <CheckCircle2 className="h-4 w-4 text-emerald-600" />
            <AlertTitle className="text-xs font-semibold">Success</AlertTitle>
            <AlertDescription className="text-xs">
              {successMessage}
            </AlertDescription>
          </Alert>
        )}

        {/* Live Outcome Indicator */}
        <div
          className={`p-3 rounded-lg border text-xs flex items-center justify-between ${
            hasAnyDiscrepancy
              ? 'bg-rose-50 border-rose-200 text-rose-800'
              : 'bg-emerald-50 border-emerald-200 text-emerald-800'
          }`}
        >
          <div className="flex items-center gap-2">
            {hasAnyDiscrepancy ? (
              <AlertTriangle className="h-4 w-4 text-rose-600 shrink-0" />
            ) : (
              <CheckCircle2 className="h-4 w-4 text-emerald-600 shrink-0" />
            )}
            <div>
              <p className="font-semibold">
                {hasAnyDiscrepancy
                  ? `Recepción con Discrepancia (Faltante total: ${totalDiscrepancy} unid.)`
                  : 'Recepción Conforme (100% Recibido)'}
              </p>
              <p className="text-[10px] opacity-80">
                {hasAnyDiscrepancy
                  ? 'Se registrará como RECEIVED_WITH_DISCREPANCY y se emitirá alerta crítica.'
                  : 'Todas las cantidades despachadas coinciden con el ingreso físico.'}
              </p>
            </div>
          </div>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 py-2">
          {/* Items Received Quantities */}
          <div className="space-y-3">
            <Label className="text-xs font-bold text-slate-800 block">
              {t('transfers.items')} ({fields.length})
            </Label>

            <div className="space-y-3 max-h-60 overflow-y-auto pr-1">
              {fields.map((field, idx) => {
                const item = transfer?.items.find(
                  (i) => i.externalId === field.itemExternalId,
                )
                const dispatched =
                  item?.dispatchedQuantity ?? item?.requestedQuantity ?? 0
                const currentRecv =
                  watchedItems?.[idx]?.receivedQuantity ?? dispatched
                const isShort = Number(currentRecv) < dispatched

                return (
                  <div
                    key={field.id}
                    className={`p-3 rounded-lg border text-xs space-y-2 transition-colors ${
                      isShort
                        ? 'bg-amber-50/50 border-amber-200'
                        : 'bg-slate-50 border-slate-200'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <p className="font-semibold text-slate-900 truncate">
                          {item?.name}
                        </p>
                        <p className="text-[10px] font-mono text-slate-500">
                          {item?.sku} · Despachado:{' '}
                          <span className="font-bold">{dispatched}</span>
                        </p>
                      </div>

                      <div className="w-28 shrink-0">
                        <Label className="text-[10px] text-slate-500 block mb-0.5">
                          {t('transfers.receivedQty')} *
                        </Label>
                        <Input
                          type="number"
                          step="any"
                          min="0"
                          max={dispatched}
                          {...register(`items.${idx}.receivedQuantity`, {
                            valueAsNumber: true,
                          })}
                          className="text-xs font-mono bg-white h-8"
                        />
                      </div>
                    </div>

                    {isShort && (
                      <div className="space-y-1 pt-1 border-t border-amber-200/60">
                        <Label className="text-[10px] font-semibold text-amber-900 block">
                          {t('transfers.discrepancyReason')} (Faltante:{' '}
                          {dispatched - Number(currentRecv)}) *
                        </Label>
                        <Input
                          type="text"
                          {...register(`items.${idx}.discrepancyReason`)}
                          className="text-xs bg-white h-8"
                          placeholder="e.g. Broken box in transit, missing 2 units"
                        />
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          </div>

          <DialogFooter className="pt-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onOpenChange(false)}
              disabled={receiveMutation.isPending}
              className="text-xs"
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={receiveMutation.isPending}
              className="text-xs bg-emerald-600 hover:bg-emerald-700 text-white"
            >
              {receiveMutation.isPending && (
                <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
              )}
              {t('transfers.actions.receive')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
