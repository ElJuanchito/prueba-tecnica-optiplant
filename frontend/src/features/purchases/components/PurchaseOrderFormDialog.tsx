import * as React from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { AlertCircle, Info, Package, ShoppingBag, Trash2 } from 'lucide-react'
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
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { useProducts } from '@/features/catalog/hooks/use-products.ts'
import { useProductUnits } from '@/features/catalog/hooks/use-product-units.ts'
import {
  createPurchaseOrderRequestSchema,
  updatePurchaseOrderRequestSchema,
} from '../schemas/purchases.schema.ts'
import type {
  CreatePurchaseOrderRequest,
  PurchaseOrderDetailResponse,
} from '../types/index.ts'
import {
  useCreatePurchaseOrder,
  useSuppliers,
  useUpdatePurchaseOrder,
} from '../hooks/use-purchases.ts'
import { SupplierSearchSelect } from './SupplierSearchSelect.tsx'
import {
  type ProductOption,
  ProductSearchSelect,
} from '@/features/transfers/components/ProductSearchSelect.tsx'
import { ApiError } from '@/lib/api-client.ts'

interface PurchaseOrderFormDialogProps {
  orderToEdit?: PurchaseOrderDetailResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: (order: PurchaseOrderDetailResponse) => void
}

interface FormItem {
  productExternalId: string
  quantity: number
  unitOfMeasureExternalId?: string | null | undefined
  unitCost: number
  discountPercent?: number | null | undefined
}

interface FormValues {
  supplierExternalId: string
  paymentTerms?: string | null | undefined
  notes?: string | null | undefined
  items: FormItem[]
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
      className="h-8 rounded-md border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700 focus:outline-none focus:ring-1 focus:ring-rose-500"
    >
      <option value="">Base Unit</option>
      {units.map((u) => (
        <option key={u.externalId} value={u.externalId}>
          {u.unitName} (×{u.conversionFactor})
        </option>
      ))}
    </select>
  )
}

export function PurchaseOrderFormDialog({
  orderToEdit,
  open,
  onOpenChange,
  onSuccess,
}: PurchaseOrderFormDialogProps) {
  const { t } = useTranslation()
  const sessionQuery = useSession()
  const session = sessionQuery.data
  const isCorporateAdminWithoutBranch =
    session?.role === 'ADMIN' && !session?.branchId
  const isEditing = Boolean(orderToEdit)

  const [serverError, setServerError] = React.useState<string | null>(null)

  // Fetch active suppliers and active products for selector
  const suppliersQuery = useSuppliers({ active: true, size: 100 }, open)
  const productsQuery = useProducts({ active: 'true', size: 100 }, open)

  const suppliers = suppliersQuery.data?.content ?? []
  const productOptions = React.useMemo<ProductOption[]>(() => {
    return (productsQuery.data?.content ?? []).map((p) => ({
      externalId: p.externalId,
      sku: p.sku,
      name: p.name,
      baseUnit: p.baseUnit,
      category: p.category,
    }))
  }, [productsQuery.data])

  const createMutation = useCreatePurchaseOrder()
  const updateMutation = useUpdatePurchaseOrder()

  const {
    register,
    control,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(
      isEditing
        ? updatePurchaseOrderRequestSchema
        : createPurchaseOrderRequestSchema,
    ),
    defaultValues: {
      supplierExternalId: '',
      paymentTerms: '',
      notes: '',
      items: [],
    },
  })

  const { fields, append, remove } = useFieldArray({
    control,
    name: 'items',
  })

  const watchedItems = watch('items')
  const watchedSupplierId = watch('supplierExternalId')

  // Preview estimated subtotal and total sum
  const previewTotal = React.useMemo(() => {
    return (watchedItems || []).reduce((sum, item) => {
      const qty = Number(item.quantity) || 0
      const cost = Number(item.unitCost) || 0
      const disc = Number(item.discountPercent) || 0
      const effectiveCost = cost * (1 - disc / 100)
      return sum + qty * effectiveCost
    }, 0)
  }, [watchedItems])

  React.useEffect(() => {
    if (open) {
      if (orderToEdit) {
        reset({
          supplierExternalId: orderToEdit.supplier.externalId,
          paymentTerms: orderToEdit.paymentTerms ?? '',
          notes: orderToEdit.notes ?? '',
          items: orderToEdit.items.map((item) => ({
            productExternalId: item.productExternalId,
            quantity: item.orderedQuantity,
            unitOfMeasureExternalId: undefined,
            unitCost: item.unitCost,
            discountPercent: item.discountPercent,
          })),
        })
      } else {
        reset({
          supplierExternalId: '',
          paymentTerms: '',
          notes: '',
          items: [],
        })
      }
      setServerError(null)
    }
  }, [open, orderToEdit, reset])

  const handleAddProduct = (product: ProductOption) => {
    setServerError(null)
    const existingIndex = fields.findIndex(
      (f) => f.productExternalId === product.externalId,
    )
    if (existingIndex >= 0) {
      const currentQty = watch(`items.${existingIndex}.quantity`) || 1
      setValue(`items.${existingIndex}.quantity`, Number(currentQty) + 1, {
        shouldValidate: true,
      })
    } else {
      append({
        productExternalId: product.externalId,
        quantity: 1,
        unitOfMeasureExternalId: undefined,
        unitCost: 0,
        discountPercent: 0,
      })
    }
  }

  const onSubmit = (data: FormValues) => {
    setServerError(null)

    if (data.items.length === 0) {
      setServerError(t('purchases.orderDialog.noItemsAdded'))
      return
    }

    const payload: CreatePurchaseOrderRequest = {
      supplierExternalId: data.supplierExternalId,
      paymentTerms: data.paymentTerms?.trim() || null,
      notes: data.notes?.trim() || null,
      items: data.items.map((item) => ({
        productExternalId: item.productExternalId,
        quantity: Number(item.quantity),
        unitOfMeasureExternalId: item.unitOfMeasureExternalId || null,
        unitCost: Number(item.unitCost),
        discountPercent:
          item.discountPercent !== undefined && item.discountPercent !== null
            ? Number(item.discountPercent)
            : 0,
      })),
    }

    if (isEditing && orderToEdit) {
      updateMutation.mutate(
        { externalId: orderToEdit.externalId, input: payload },
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
    } else {
      createMutation.mutate(payload, {
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
      })
    }
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl max-h-[90vh] overflow-y-auto">
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogHeader>
            <div className="flex items-center gap-2 text-rose-600 mb-1">
              <ShoppingBag className="h-5 w-5" />
              <DialogTitle className="text-base font-bold text-slate-900">
                {isEditing
                  ? t('purchases.orderDialog.editTitle')
                  : t('purchases.orderDialog.createTitle')}
              </DialogTitle>
            </div>
            <DialogDescription className="text-xs text-slate-500">
              {isEditing
                ? t('purchases.orderDialog.editDesc')
                : t('purchases.orderDialog.createDesc')}
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

          <div className="space-y-4 py-3">
            {/* Supplier & Payment Terms */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
              <div className="space-y-1">
                <Label className="text-xs font-semibold text-slate-700">
                  {t('purchases.orders.supplier')}{' '}
                  <span className="text-rose-500">*</span>
                </Label>
                <SupplierSearchSelect
                  suppliers={suppliers}
                  value={watchedSupplierId}
                  onChange={(val) =>
                    setValue('supplierExternalId', val, {
                      shouldValidate: true,
                    })
                  }
                  error={errors.supplierExternalId?.message}
                  placeholder={t('purchases.orderDialog.selectSupplier')}
                />
              </div>

              <div className="space-y-1">
                <Label
                  htmlFor="paymentTerms"
                  className="text-xs font-semibold text-slate-700"
                >
                  {t('purchases.orderDialog.paymentTerms')}
                </Label>
                <Input
                  id="paymentTerms"
                  placeholder={t(
                    'purchases.orderDialog.paymentTermsPlaceholder',
                  )}
                  {...register('paymentTerms')}
                  className="text-xs h-9 bg-slate-50 border-slate-200"
                />
              </div>
            </div>

            {/* Notes */}
            <div className="space-y-1">
              <Label
                htmlFor="notes"
                className="text-xs font-semibold text-slate-700"
              >
                {t('purchases.orderDialog.notes')}
              </Label>
              <Input
                id="notes"
                placeholder={t('purchases.orderDialog.notesPlaceholder')}
                {...register('notes')}
                className="text-xs h-9 bg-slate-50 border-slate-200"
              />
            </div>

            {/* Line Items Section */}
            <div className="space-y-2.5 pt-2 border-t border-slate-200">
              <div className="flex items-center justify-between">
                <Label className="text-xs font-bold uppercase tracking-wider text-slate-700">
                  {t('purchases.orderDialog.itemsTitle')} ({fields.length})
                </Label>
              </div>

              {/* Product search and add bar */}
              <div className="p-2.5 bg-slate-50 rounded-xl border border-slate-200">
                <Label className="text-[11px] font-semibold text-slate-600 block mb-1">
                  {t('purchases.orderDialog.addItem')}
                </Label>
                <ProductSearchSelect
                  products={productOptions}
                  clearOnSelect
                  onSelectProduct={handleAddProduct}
                  placeholder={t(
                    'purchases.costHistory.searchProductPlaceholder',
                  )}
                />
              </div>

              {/* Lines Table / List */}
              {fields.length === 0 ? (
                <div className="rounded-xl border border-dashed border-slate-200 p-6 text-center">
                  <Package className="mx-auto h-8 w-8 text-slate-300 mb-1" />
                  <p className="text-xs font-medium text-slate-500">
                    {t('purchases.orderDialog.noItemsAdded')}
                  </p>
                  <p className="text-[11px] text-slate-400">
                    {t('purchases.orderDialog.noItemsAddedDesc')}
                  </p>
                </div>
              ) : (
                <div className="space-y-2">
                  <div className="hidden sm:grid sm:grid-cols-12 gap-2 text-[11px] font-bold text-slate-500 px-2 uppercase">
                    <span className="col-span-4">
                      {t('purchases.orderDialog.product')}
                    </span>
                    <span className="col-span-2">
                      {t('purchases.orderDialog.quantity')}
                    </span>
                    <span className="col-span-2">
                      {t('purchases.orderDialog.unitCost')}
                    </span>
                    <span className="col-span-2">
                      {t('purchases.orderDialog.discountPercent')}
                    </span>
                    <span className="col-span-2 text-right">
                      {t('purchases.orderDialog.subtotal')}
                    </span>
                  </div>

                  {fields.map((field, index) => {
                    const product = productOptions.find(
                      (p) => p.externalId === field.productExternalId,
                    )
                    const qty = Number(watch(`items.${index}.quantity`)) || 0
                    const cost = Number(watch(`items.${index}.unitCost`)) || 0
                    const disc =
                      Number(watch(`items.${index}.discountPercent`)) || 0
                    const linePreview = qty * cost * (1 - disc / 100)

                    return (
                      <div
                        key={field.id}
                        className="p-2.5 bg-white border border-slate-200 rounded-lg shadow-2xs grid grid-cols-1 sm:grid-cols-12 gap-2 items-center text-xs"
                      >
                        {/* Product Info */}
                        <div className="sm:col-span-4 min-w-0">
                          <div className="font-semibold text-slate-900 truncate">
                            {product?.name || 'Product'}
                          </div>
                          <div className="flex items-center gap-1 font-mono text-[10px] text-slate-500">
                            <span className="text-rose-700 bg-rose-50 px-1 rounded">
                              {product?.sku}
                            </span>
                            {product?.baseUnit && (
                              <span>({product.baseUnit})</span>
                            )}
                          </div>
                        </div>

                        {/* Quantity & Unit of Measure */}
                        <div className="sm:col-span-2 flex items-center gap-1">
                          <Input
                            type="number"
                            min="0.01"
                            step="any"
                            {...register(`items.${index}.quantity`, {
                              valueAsNumber: true,
                            })}
                            className="h-8 text-xs bg-slate-50 border-slate-200"
                            placeholder="Cant."
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

                        {/* Unit Cost */}
                        <div className="sm:col-span-2">
                          <Input
                            type="number"
                            min="0"
                            step="0.01"
                            {...register(`items.${index}.unitCost`, {
                              valueAsNumber: true,
                            })}
                            className="h-8 text-xs bg-slate-50 border-slate-200"
                            placeholder="$ Costo"
                          />
                        </div>

                        {/* Discount Percent */}
                        <div className="sm:col-span-2">
                          <Input
                            type="number"
                            min="0"
                            max="100"
                            step="0.1"
                            {...register(`items.${index}.discountPercent`, {
                              valueAsNumber: true,
                            })}
                            className="h-8 text-xs bg-slate-50 border-slate-200"
                            placeholder="Desc %"
                          />
                        </div>

                        {/* Line subtotal & Remove button */}
                        <div className="sm:col-span-2 flex items-center justify-between sm:justify-end gap-2">
                          <span className="font-mono font-bold text-slate-900 text-xs">
                            ${linePreview.toFixed(2)}
                          </span>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            className="h-7 w-7 p-0 text-slate-400 hover:text-rose-600 hover:bg-rose-50"
                            onClick={() => remove(index)}
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>

            {/* Preview Total & Notice Banner */}
            <div className="p-3 bg-slate-50 rounded-xl border border-slate-200 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div className="flex items-center gap-2 text-slate-600">
                <Info className="h-4 w-4 text-slate-400 shrink-0" />
                <p className="text-[11px] leading-tight">
                  {t('purchases.orderDialog.serverCalculatedNotice')}
                </p>
              </div>

              <div className="text-right shrink-0">
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">
                  {t('purchases.orderDialog.previewTotal')}
                </span>
                <span className="text-lg font-black font-mono text-slate-900">
                  ${previewTotal.toFixed(2)}
                </span>
              </div>
            </div>
          </div>

          <DialogFooter className="gap-2 sm:gap-0 pt-3 border-t border-slate-200">
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
              disabled={
                isSubmitting ||
                isCorporateAdminWithoutBranch ||
                fields.length === 0
              }
              className="text-xs bg-rose-600 hover:bg-rose-700 text-white font-semibold shadow-xs"
            >
              {isSubmitting
                ? t('purchases.orderDialog.submitting')
                : t('common.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
