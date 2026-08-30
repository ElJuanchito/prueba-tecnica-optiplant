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
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs.tsx'
import {
  useApproveTransfer,
  useRejectTransfer,
} from '../hooks/use-transfers.ts'
import {
  approvalRequestSchema,
  reasonRequestSchema,
} from '../schemas/transfer.schema.ts'
import type {
  ApprovalRequest,
  ReasonRequest,
  TransferDetailResponse,
} from '../types/transfer.types.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  AlertCircle,
  CheckCircle2,
  CheckSquare,
  Loader2,
  XCircle,
} from 'lucide-react'

interface TransferApprovalDialogProps {
  transfer: TransferDetailResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function TransferApprovalDialog({
  transfer,
  open,
  onOpenChange,
}: TransferApprovalDialogProps) {
  const { t } = useTranslation()
  const approveMutation = useApproveTransfer()
  const rejectMutation = useRejectTransfer()

  const [tab, setTab] = React.useState<'approve' | 'reject'>('approve')
  const [serverError, setServerError] = React.useState<string | null>(null)
  const [successMessage, setSuccessMessage] = React.useState<string | null>(
    null,
  )

  // Approval Form
  const {
    register: registerApprove,
    control: controlApprove,
    handleSubmit: handleSubmitApprove,
    reset: resetApprove,
  } = useForm<ApprovalRequest>({
    resolver: zodResolver(approvalRequestSchema),
    defaultValues: {
      items: [],
      notes: '',
    },
  })

  const { fields: approveFields } = useFieldArray({
    control: controlApprove,
    name: 'items',
  })

  // Rejection Form
  const {
    register: registerReject,
    handleSubmit: handleSubmitReject,
    reset: resetReject,
    formState: { errors: errorsReject },
  } = useForm<ReasonRequest>({
    resolver: zodResolver(reasonRequestSchema),
    defaultValues: {
      reason: '',
    },
  })

  React.useEffect(() => {
    if (transfer && open) {
      resetApprove({
        items: transfer.items.map((i) => ({
          itemExternalId: i.externalId,
          approvedQuantity: i.requestedQuantity,
        })),
        notes: '',
      })
      resetReject({ reason: '' })
      setServerError(null)
      setSuccessMessage(null)
      setTab('approve')
    }
  }, [transfer, open, resetApprove, resetReject])

  const onApproveSubmit = (data: ApprovalRequest) => {
    if (!transfer) return
    setServerError(null)

    // Validate quantities are not exceeding requested (R-07)
    for (const item of data.items) {
      const original = transfer.items.find(
        (i) => i.externalId === item.itemExternalId,
      )
      if (original && item.approvedQuantity > original.requestedQuantity) {
        setServerError(
          `Approved quantity (${item.approvedQuantity}) cannot exceed requested quantity (${original.requestedQuantity}) for item ${original.sku} (R-07).`,
        )
        return
      }
    }

    approveMutation.mutate(
      { externalId: transfer.externalId, input: data },
      {
        onSuccess: (res) => {
          setSuccessMessage(
            `Transfer ${res.transferNumber} approved successfully. State changed to IN_PREPARATION.`,
          )
          setTimeout(() => {
            onOpenChange(false)
          }, 1500)
        },
        onError: (err) => {
          setServerError(err.message || 'Failed to approve transfer')
        },
      },
    )
  }

  const onRejectSubmit = (data: ReasonRequest) => {
    if (!transfer) return
    setServerError(null)

    rejectMutation.mutate(
      { externalId: transfer.externalId, input: data },
      {
        onSuccess: (res) => {
          setSuccessMessage(
            `Transfer ${res.transferNumber} rejected. State changed to CANCELLED.`,
          )
          setTimeout(() => {
            onOpenChange(false)
          }, 1500)
        },
        onError: (err) => {
          setServerError(err.message || 'Failed to reject transfer')
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
              <CheckSquare className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold text-slate-900">
                {t('transfers.dialogs.approvalTitle')}
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-500">
                {transfer?.transferNumber} — {t('transfers.originBranch')}:{' '}
                <span className="font-semibold text-slate-700">
                  {transfer?.originBranch?.name}
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

        <Tabs
          value={tab}
          onValueChange={(val) => setTab(val as 'approve' | 'reject')}
          className="w-full"
        >
          <TabsList className="grid w-full grid-cols-2 bg-slate-100 p-1 rounded-lg">
            <TabsTrigger
              value="approve"
              className="text-xs gap-1.5 data-[state=active]:bg-white data-[state=active]:text-emerald-700"
            >
              <CheckCircle2 className="h-3.5 w-3.5" />
              {t('transfers.actions.approve')}
            </TabsTrigger>
            <TabsTrigger
              value="reject"
              className="text-xs gap-1.5 data-[state=active]:bg-white data-[state=active]:text-rose-700"
            >
              <XCircle className="h-3.5 w-3.5" />
              {t('transfers.actions.reject')}
            </TabsTrigger>
          </TabsList>

          {/* APPROVE TAB */}
          <TabsContent value="approve" className="space-y-4 pt-3">
            <p className="text-xs text-slate-500">
              {t('transfers.dialogs.approvalDesc')}
            </p>

            <form
              onSubmit={handleSubmitApprove(onApproveSubmit)}
              className="space-y-4"
            >
              <div className="space-y-2.5 max-h-56 overflow-y-auto pr-1">
                {approveFields.map((field, idx) => {
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
                          {item?.sku} · Solicitado: {item?.requestedQuantity}
                        </p>
                      </div>

                      <div className="w-28 shrink-0">
                        <Label className="text-[10px] text-slate-500 block mb-0.5">
                          {t('transfers.approvedQty')} *
                        </Label>
                        <Input
                          type="number"
                          step="any"
                          min="0.001"
                          max={item?.requestedQuantity}
                          {...registerApprove(`items.${idx}.approvedQuantity`, {
                            valueAsNumber: true,
                          })}
                          className="text-xs font-mono bg-white h-8"
                        />
                      </div>
                    </div>
                  )
                })}
              </div>

              <div className="space-y-1.5">
                <Label
                  htmlFor="approveNotes"
                  className="text-xs font-semibold text-slate-700"
                >
                  {t('transfers.notes')}
                </Label>
                <Input
                  id="approveNotes"
                  type="text"
                  {...registerApprove('notes')}
                  className="text-xs"
                  placeholder={t('transfers.dialogs.notesPrompt')}
                />
              </div>

              <DialogFooter className="pt-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => onOpenChange(false)}
                  disabled={approveMutation.isPending}
                  className="text-xs"
                >
                  {t('common.cancel')}
                </Button>
                <Button
                  type="submit"
                  size="sm"
                  disabled={approveMutation.isPending}
                  className="text-xs bg-emerald-600 hover:bg-emerald-700 text-white"
                >
                  {approveMutation.isPending && (
                    <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
                  )}
                  {t('transfers.actions.approve')}
                </Button>
              </DialogFooter>
            </form>
          </TabsContent>

          {/* REJECT TAB */}
          <TabsContent value="reject" className="space-y-4 pt-3">
            <p className="text-xs text-slate-500">
              {t('transfers.dialogs.rejectDesc')}
            </p>

            <form
              onSubmit={handleSubmitReject(onRejectSubmit)}
              className="space-y-4"
            >
              <div className="space-y-1.5">
                <Label
                  htmlFor="rejectReason"
                  className="text-xs font-semibold text-slate-700"
                >
                  {t('transfers.dialogs.reasonPrompt')} *
                </Label>
                <Input
                  id="rejectReason"
                  type="text"
                  {...registerReject('reason')}
                  className="text-xs"
                  placeholder="e.g. Insufficient long-term stock, prioritized for local branch demand"
                />
                {errorsReject.reason && (
                  <p className="text-[11px] text-rose-600 font-medium">
                    {errorsReject.reason.message}
                  </p>
                )}
              </div>

              <DialogFooter className="pt-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => onOpenChange(false)}
                  disabled={rejectMutation.isPending}
                  className="text-xs"
                >
                  {t('common.cancel')}
                </Button>
                <Button
                  type="submit"
                  size="sm"
                  disabled={rejectMutation.isPending}
                  className="text-xs bg-rose-600 hover:bg-rose-700 text-white"
                >
                  {rejectMutation.isPending && (
                    <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
                  )}
                  {t('transfers.actions.reject')}
                </Button>
              </DialogFooter>
            </form>
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  )
}
