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
import { useCancelTransfer } from '../hooks/use-transfers.ts'
import { reasonRequestSchema } from '../schemas/transfer.schema.ts'
import type {
  ReasonRequest,
  TransferSummaryResponse,
} from '../types/transfer.types.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { AlertCircle, CheckCircle2, Loader2, XCircle } from 'lucide-react'

interface TransferCancelDialogProps {
  transfer: TransferSummaryResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function TransferCancelDialog({
  transfer,
  open,
  onOpenChange,
}: TransferCancelDialogProps) {
  const { t } = useTranslation()
  const cancelMutation = useCancelTransfer()

  const [serverError, setServerError] = React.useState<string | null>(null)
  const [successMessage, setSuccessMessage] = React.useState<string | null>(
    null,
  )

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ReasonRequest>({
    resolver: zodResolver(reasonRequestSchema),
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

  const onSubmit = (data: ReasonRequest) => {
    if (!transfer) return
    setServerError(null)

    cancelMutation.mutate(
      { externalId: transfer.externalId, input: data },
      {
        onSuccess: (res) => {
          setSuccessMessage(
            `Transfer ${res.transferNumber} cancelled successfully. State changed to CANCELLED.`,
          )
          setTimeout(() => {
            onOpenChange(false)
          }, 1500)
        },
        onError: (err) => {
          setServerError(err.message || 'Failed to cancel transfer')
        },
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md bg-white p-6 sm:rounded-xl">
        <DialogHeader>
          <div className="flex items-center space-x-2">
            <div className="h-8 w-8 rounded-lg bg-rose-50 text-rose-600 flex items-center justify-center border border-rose-200">
              <XCircle className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold text-slate-900">
                {t('transfers.dialogs.cancelTitle')}
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-500">
                {transfer?.transferNumber} · {t('transfers.dialogs.cancelDesc')}
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
          <div className="space-y-1.5">
            <Label
              htmlFor="cancelReason"
              className="text-xs font-semibold text-slate-700"
            >
              {t('transfers.dialogs.reasonPrompt')} *
            </Label>
            <Input
              id="cancelReason"
              type="text"
              {...register('reason')}
              className="text-xs"
              placeholder="e.g. Requester withdrew need, stock sourced locally"
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
              disabled={cancelMutation.isPending}
              className="text-xs"
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={cancelMutation.isPending}
              className="text-xs bg-rose-600 hover:bg-rose-700 text-white"
            >
              {cancelMutation.isPending && (
                <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
              )}
              {t('transfers.actions.cancel')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
