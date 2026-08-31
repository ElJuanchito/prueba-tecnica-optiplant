import * as React from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { AlertCircle, AlertTriangle, PackageCheck } from 'lucide-react'
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { useProductUnits } from '@/features/catalog/hooks/use-product-units.ts'
import type {
  PurchaseOrderDetailResponse,
  RegisterReceptionRequest,
} from '../types/index.ts'
import {
  usePurchaseOrderDetail,
  useRegisterReception,
} from '../hooks/use-purchases.ts'
import { ApiError } from '@/lib/api-client.ts'

interface PurchaseReceptionDialogProps {
  orderExternalId: string | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: (order: PurchaseOrderDetailResponse) => void
}

interface ReceptionItemFormValue {
  itemExternalId: string
  productExternalId: string
  sku: string
  name: string
  orderedQuantity: number
  receivedQuantity: number
  pendingQuantity: number
  receivedNow: number
  unitOfMeasureExternalId?: string | null | undefined
}

interface FormValues {
  notes?: string | null | undefined
  items: ReceptionItemFormValue[]
}

function ProductUnitSelect({
  productExternalId,
  value,
  onChange,
}: {
  productExternalId: string
  value?: string | null | undefined
  onChange: (unitId: string | undefined) => void
}) {
  const unitsQuery = useProductUnits(productExternalId)
  const units = unitsQuery.data ?? []

  if (units.length === 0) {
    return null
  }

  return (
    <select
      value={value ?? ''}
      onChange={(e) => onChange(e.target.value || undefined)}
      className="h-8 max-w-[140px] truncate rounded-md border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700 focus:outline-none focus:ring-1 focus:ring-emerald-500"
    >
      <option value="">Base</option>
      {units.map((u) => (
        <option key={u.externalId} value={u.externalId}>
          {u.unitName} (×{u.conversionFactor})
        </option>
      ))}
    </select>
  )
}

export function PurchaseReceptionDialog({
  orderExternalId,
  open,
  onOpenChange,
  onSuccess,
}: PurchaseReceptionDialogProps) {
  const { t } = useTranslation()
  const sessionQuery = useSession()
  const session = sessionQuery.data
  const isCorporateAdminWithoutBranch =
    session?.role === 'ADMIN' && !session?.branchId

  const [serverError, setServerError] = React.useState<string | null>(null)

  const orderQuery = usePurchaseOrderDetail(
    orderExternalId ?? '',
    Boolean(orderExternalId) && open,
  )
  const order = orderQuery.data

  const receptionMutation = useRegisterReception()

  const { register, control, handleSubmit, watch, setValue, reset } =
    useForm<FormValues>({
      defaultValues: {
        notes: '',
        items: [],
      },
    })

  const { fields } = useFieldArray({
    control,
    name: 'items',
  })

  const watchedItems = watch('items')

  React.useEffect(() => {
    if (open && order) {
      reset({
        notes: '',
        items: order.items.map((item) => ({
          itemExternalId: item.externalId,
          productExternalId: item.productExternalId,
          sku: item.sku,
          name: item.name,
          orderedQuantity: item.orderedQuantity,
          receivedQuantity: item.receivedQuantity,
          pendingQuantity: item.pendingQuantity,
          receivedNow: item.pendingQuantity > 0 ? item.pendingQuantity : 0,
          unitOfMeasureExternalId: undefined,
        })),
      })
      setServerError(null)
    }
  }, [open, order, reset])

  // Check if any line has an over-receipt
  const hasOverReceipt = React.useMemo(() => {
    return (watchedItems || []).some((item) => {
      const now = Number(item.receivedNow) || 0
      const pending = Number(item.pendingQuantity) || 0
      return now > pending
    })
  }, [watchedItems])

  const onSubmit = (data: FormValues) => {
    if (!order) return
    setServerError(null)

    // Filter items with receivedNow > 0
    const activeItems = data.items
      .filter((item) => Number(item.receivedNow) > 0)
      .map((item) => ({
        itemExternalId: item.itemExternalId,
        receivedQuantity: Number(item.receivedNow),
        unitOfMeasureExternalId: item.unitOfMeasureExternalId || null,
      }))

    if (activeItems.length === 0) {
      setServerError('Debe ingresar al menos una cantidad recibida mayor a 0')
      return
    }

    const payload: RegisterReceptionRequest = {
      notes: data.notes?.trim() || null,
      items: activeItems,
    }

    receptionMutation.mutate(
      { externalId: order.externalId, input: payload },
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

  const isSubmitting = receptionMutation.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-4xl max-h-[90vh] overflow-y-auto">
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogHeader>
            <div className="flex items-center gap-2 text-emerald-600 mb-1">
              <PackageCheck className="h-5 w-5" />
              <DialogTitle className="text-base font-bold text-slate-900">
                {t('purchases.receptionDialog.title')}
              </DialogTitle>
            </div>
            <DialogDescription className="text-xs text-slate-500">
              {t('purchases.receptionDialog.desc')}
            </DialogDescription>
          </DialogHeader>

          {isCorporateAdminWithoutBranch && (
            <div className="my-3">
              <Alert variant="destructive">
                <AlertCircle className="h-4 w-4" />
                <AlertTitle className="text-xs font-bold">
                  {t('purchases.orderDialog.branchRequired')}
                </AlertTitle>
                <AlertDescription className="text-xs">
                  {t('purchases.errors.branch_context_required')}
                </AlertDescription>
              </Alert>
            </div>
          )}

          {serverError && (
            <div className="my-3">
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

          {order && (
            <div className="my-3 p-3 bg-slate-50 rounded-xl border border-slate-200 grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs">
              <div>
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                  {t('purchases.orders.orderNumber')}
                </span>
                <span className="font-mono font-bold text-rose-700">
                  {order.orderNumber}
                </span>
              </div>
              <div>
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                  {t('purchases.orders.supplier')}
                </span>
                <span className="font-semibold text-slate-800">
                  {order.supplier.name}
                </span>
              </div>
              <div>
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                  {t('purchases.detailDialog.branch')}
                </span>
                <span className="text-slate-700 font-medium">
                  {order.branch?.name || '—'}
                </span>
              </div>
            </div>
          )}

          <div className="space-y-4 py-2">
            {/* Notes / Delivery reference */}
            <div className="space-y-1">
              <Label
                htmlFor="receptionNotes"
                className="text-xs font-semibold text-slate-700"
              >
                {t('purchases.receptionDialog.receptionNotes')}
              </Label>
              <Input
                id="receptionNotes"
                placeholder={t(
                  'purchases.receptionDialog.receptionNotesPlaceholder',
                )}
                {...register('notes')}
                className="text-xs h-9 bg-slate-50 border-slate-200"
              />
            </div>

            {/* Over-receipt alert */}
            {hasOverReceipt && (
              <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl flex items-start gap-2.5 text-amber-900 text-xs">
                <AlertTriangle className="h-4 w-4 text-amber-600 shrink-0 mt-0.5" />
                <span>
                  {t('purchases.receptionDialog.overReceiptWarning', {
                    received: '>',
                    pending: 'saldo',
                  })}
                </span>
              </div>
            )}

            {/* Reception Lines Table */}
            <div className="space-y-2">
              <Label className="text-xs font-bold uppercase tracking-wider text-slate-700 block">
                {t('purchases.receptionDialog.itemsTitle')}
              </Label>

              <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xs">
                <Table>
                  <TableHeader>
                    <TableRow className="bg-slate-50/75 hover:bg-slate-50/75">
                      <TableHead className="text-xs font-bold text-slate-700">
                        {t('purchases.orderDialog.product')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('purchases.receptionDialog.ordered')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('purchases.receptionDialog.alreadyReceived')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700">
                        {t('purchases.receptionDialog.pending')}
                      </TableHead>
                      <TableHead className="text-right text-xs font-bold text-slate-700 w-60">
                        {t('purchases.receptionDialog.receiveNow')}{' '}
                        <span className="text-rose-500">*</span>
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {fields.map((field, index) => {
                      const item = watchedItems?.[index]
                      const isOver =
                        Number(item?.receivedNow) >
                        Number(item?.pendingQuantity)

                      return (
                        <TableRow key={field.id}>
                          <TableCell className="text-xs">
                            <div className="font-semibold text-slate-900">
                              {field.name}
                            </div>
                            <div className="font-mono text-[10px] text-rose-700">
                              {field.sku}
                            </div>
                          </TableCell>
                          <TableCell className="text-right font-mono font-bold text-xs text-slate-900">
                            {field.orderedQuantity}
                          </TableCell>
                          <TableCell className="text-right font-mono text-xs text-emerald-700 font-semibold">
                            {field.receivedQuantity}
                          </TableCell>
                          <TableCell className="text-right font-mono text-xs text-amber-700 font-semibold">
                            {field.pendingQuantity}
                          </TableCell>
                          <TableCell className="text-right">
                            <div className="flex items-center justify-end gap-1.5">
                              <Input
                                type="number"
                                min="0"
                                step="any"
                                {...register(`items.${index}.receivedNow`, {
                                  valueAsNumber: true,
                                })}
                                className={`h-8 w-24 text-right font-mono font-bold text-xs ${
                                  isOver
                                    ? 'bg-amber-50 border-amber-400 text-amber-900'
                                    : 'bg-slate-50 border-slate-200 text-slate-900'
                                }`}
                              />
                              <ProductUnitSelect
                                productExternalId={field.productExternalId}
                                value={watch(
                                  `items.${index}.unitOfMeasureExternalId`,
                                )}
                                onChange={(unitId) =>
                                  setValue(
                                    `items.${index}.unitOfMeasureExternalId`,
                                    unitId,
                                  )
                                }
                              />
                            </div>
                          </TableCell>
                        </TableRow>
                      )
                    })}
                  </TableBody>
                </Table>
              </div>
            </div>
          </div>

          <DialogFooter className="gap-2 sm:gap-0 pt-3 border-t border-slate-100">
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
              disabled={isSubmitting || isCorporateAdminWithoutBranch}
              className="text-xs bg-emerald-600 hover:bg-emerald-700 text-white font-semibold shadow-xs"
            >
              {isSubmitting
                ? t('purchases.receptionDialog.submitting')
                : t('purchases.receptionDialog.submit')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
