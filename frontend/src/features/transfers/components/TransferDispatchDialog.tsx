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
import { useDispatchTransfer } from '../hooks/use-transfers.ts'
import { dispatchRequestSchema } from '../schemas/transfer.schema.ts'
import type {
  DispatchRequest,
  TransferDetailResponse,
} from '../types/transfer.types.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { AlertCircle, CheckCircle2, Loader2, Truck } from 'lucide-react'

interface TransferDispatchDialogProps {
  transfer: TransferDetailResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function TransferDispatchDialog({
  transfer,
  open,
  onOpenChange,
}: TransferDispatchDialogProps) {
  const { t } = useTranslation()
  const dispatchMutation = useDispatchTransfer()

  const [serverError, setServerError] = React.useState<string | null>(null)
  const [successMessage, setSuccessMessage] = React.useState<string | null>(
    null,
  )

  const {
    register,
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<DispatchRequest>({
    resolver: zodResolver(dispatchRequestSchema),
    defaultValues: {
      carrierName: '',
      trackingNumber: '',
      estimatedArrivalAt: '',
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
        carrierName: '',
        trackingNumber: '',
        estimatedArrivalAt: '',
        items: transfer.items.map((i) => ({
          itemExternalId: i.externalId,
          dispatchedQuantity: i.requestedQuantity,
        })),
      })
      setServerError(null)
      setSuccessMessage(null)
    }
  }, [transfer, open, reset])

  const onSubmit = (data: DispatchRequest) => {
    if (!transfer) return
    setServerError(null)

    // Formatted ISO date for estimatedArrivalAt if provided
    let isoArrival: string | null = null
    if (data.estimatedArrivalAt && data.estimatedArrivalAt.trim() !== '') {
      try {
        isoArrival = new Date(data.estimatedArrivalAt).toISOString()
      } catch {
        isoArrival = null
      }
    }

    const payload: DispatchRequest = {
      carrierName: data.carrierName,
      trackingNumber: data.trackingNumber || null,
      estimatedArrivalAt: isoArrival,
      items: data.items,
    }

    dispatchMutation.mutate(
      { externalId: transfer.externalId, input: payload },
      {
        onSuccess: (res) => {
          setSuccessMessage(
            `Transfer ${res.transferNumber} successfully dispatched. State changed to IN_TRANSIT.`,
          )
          setTimeout(() => {
            onOpenChange(false)
          }, 1500)
        },
        onError: (err) => {
          setServerError(err.message || 'Failed to dispatch transfer')
        },
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl bg-white p-6 sm:rounded-xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <div className="flex items-center space-x-2">
            <div className="h-8 w-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center border border-indigo-200">
              <Truck className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold text-slate-900">
                {t('transfers.dialogs.dispatchTitle')}
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-500">
                {transfer?.transferNumber} · {t('transfers.originBranch')}:{' '}
                <span className="font-semibold text-slate-700">
                  {transfer?.originBranch?.name}
                </span>{' '}
                → {t('transfers.destinationBranch')}:{' '}
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

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 py-2">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Carrier Name */}
            <div className="space-y-1.5">
              <Label
                htmlFor="carrierName"
                className="text-xs font-semibold text-slate-700"
              >
                {t('transfers.carrier')} *
              </Label>
              <Input
                id="carrierName"
                type="text"
                {...register('carrierName')}
                className="text-xs"
                placeholder="e.g. Servientrega, DHL, Flota Interna"
              />
              {errors.carrierName && (
                <p className="text-[11px] text-rose-600 font-medium">
                  {errors.carrierName.message}
                </p>
              )}
            </div>

            {/* Tracking Number */}
            <div className="space-y-1.5">
              <Label
                htmlFor="trackingNumber"
                className="text-xs font-semibold text-slate-700"
              >
                {t('transfers.trackingNumber')}
              </Label>
              <Input
                id="trackingNumber"
                type="text"
                {...register('trackingNumber')}
                className="text-xs font-mono"
                placeholder="e.g. TRK-98234-X"
              />
            </div>
          </div>

          {/* Estimated Arrival Date */}
          <div className="space-y-1.5">
            <Label
              htmlFor="estimatedArrivalAt"
              className="text-xs font-semibold text-slate-700"
            >
              {t('transfers.estimatedArrival')} (ETA)
            </Label>
            <Input
              id="estimatedArrivalAt"
              type="datetime-local"
              {...register('estimatedArrivalAt')}
              className="text-xs"
            />
          </div>

          {/* Items Dispatched Quantities */}
          <div className="space-y-2 pt-1">
            <Label className="text-xs font-bold text-slate-800 block">
              {t('transfers.items')} ({fields.length})
            </Label>
            <div className="space-y-2 max-h-52 overflow-y-auto pr-1">
              {fields.map((field, idx) => {
                const item = transfer?.items.find(
                  (i) => i.externalId === field.itemExternalId,
                )
                return (
                  <div
                    key={field.id}
                    className="flex items-center justify-between gap-3 p-2.5 rounded-lg bg-slate-50 border border-slate-200 text-xs"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="font-semibold text-slate-900 truncate">
                        {item?.name}
                      </p>
                      <p className="text-[10px] font-mono text-slate-500">
                        {item?.sku} · Acordado: {item?.requestedQuantity}
                      </p>
                    </div>

                    <div className="w-28 shrink-0">
                      <Label className="text-[10px] text-slate-500 block mb-0.5">
                        {t('transfers.dispatchedQty')} *
                      </Label>
                      <Input
                        type="number"
                        step="any"
                        min="0.001"
                        max={item?.requestedQuantity}
                        {...register(`items.${idx}.dispatchedQuantity`, {
                          valueAsNumber: true,
                        })}
                        className="text-xs font-mono bg-white h-8"
                      />
                    </div>
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
              disabled={dispatchMutation.isPending}
              className="text-xs"
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={dispatchMutation.isPending}
              className="text-xs bg-indigo-600 hover:bg-indigo-700 text-white"
            >
              {dispatchMutation.isPending && (
                <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
              )}
              {t('transfers.actions.dispatch')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
