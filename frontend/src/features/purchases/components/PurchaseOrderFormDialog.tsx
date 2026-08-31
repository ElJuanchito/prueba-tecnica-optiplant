import * as React from 'react'
import { useFieldArray, useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import {
  AlertCircle,
  Info,
  Package,
  Percent,
  ShoppingBag,
  Trash2,
} from 'lucide-react'
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
  baseUnit,
  value,
  onChange,
  disabled,
}: {
  productExternalId: string
  baseUnit?: string | null | undefined
  value?: string | null | undefined
  onChange: (unitId: string | undefined) => void
  disabled?: boolean
}) {
  const { t } = useTranslation()
  const unitsQuery = useProductUnits(productExternalId)
  const units = unitsQuery.data ?? []

  if (units.length === 0) {
    return (
      <div className="h-9 px-3 flex items-center bg-slate-50 border border-slate-200 rounded-md text-slate-600 text-xs truncate">
        {baseUnit ? `${baseUnit} (${t('purchases.orderDialog.baseUnit')})` : t('purchases.orderDialog.baseUnit')}
      </div>
    )
  }

  return (
    <select
      value={value ?? ''}
      onChange={(e) => onChange(e.target.value || undefined)}
      disabled={disabled}
      className="w-full h-9 rounded-md border border-slate-200 bg-white px-3 py-1 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-rose-500 disabled:opacity-50"
    >
      <option value="">
        {baseUnit ? `${baseUnit} (${t('purchases.orderDialog.baseUnit')})` : t('purchases.orderDialog.baseUnit')}
      </option>
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

  const watchedItems = useWatch({
    control,
    name: 'items',
    defaultValue: [],
  })
  const watchedSupplierId = useWatch({
    control,
    name: 'supplierExternalId',
    defaultValue: '',
  })

  // Preview estimated subtotal and total sum
  const previewTotal = React.useMemo(() => {
    if (!watchedItems || !Array.isArray(watchedItems)) return 0
    return watchedItems.reduce((sum, item) => {
      if (!item) return sum
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
      <DialogContent className="sm:max-w-4xl max-h-[90vh] overflow-y-auto">
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogHeader>
            <div className="flex items-center gap-2.5 text-rose-600 mb-1">
              <div className="h-9 w-9 rounded-xl bg-rose-50 border border-rose-200 flex items-center justify-center text-rose-600 shrink-0">
                <ShoppingBag className="h-5 w-5" />
              </div>
              <div>
                <DialogTitle className="text-base font-bold text-slate-900">
                  {isEditing
                    ? t('purchases.orderDialog.editTitle')
                    : t('purchases.orderDialog.createTitle')}
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-500">
                  {isEditing
                    ? t('purchases.orderDialog.editDesc')
                    : t('purchases.orderDialog.createDesc')}
                </DialogDescription>
              </div>
            </div>
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
                <div className="flex items-center gap-2">
                  <Label className="text-xs font-bold uppercase tracking-wider text-slate-700">
                    {t('purchases.orderDialog.itemsTitle')}
                  </Label>
                  <span className="text-[11px] font-semibold bg-rose-50 text-rose-700 border border-rose-200/60 px-2 py-0.5 rounded-full">
                    {fields.length}
                  </span>
                </div>
              </div>

              {/* Product search and add bar */}
              <div className="p-3 bg-slate-50 rounded-xl border border-slate-200 space-y-1">
                <Label className="text-[11px] font-semibold text-slate-600 block">
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
                <div className="space-y-3">
                  {fields.map((field, index) => {
                    const product = productOptions.find(
                      (p) => p.externalId === field.productExternalId,
                    )
                    const itemValues = watchedItems?.[index]
                    const qty =
                      Number(itemValues?.quantity ?? field.quantity) || 0
                    const cost =
                      Number(itemValues?.unitCost ?? field.unitCost) || 0
                    const disc =
                      Number(
                        itemValues?.discountPercent ?? field.discountPercent,
                      ) || 0
                    const linePreview = qty * cost * (1 - disc / 100)

                    return (
                      <div
                        key={field.id}
                        className="p-3.5 bg-white border border-slate-200 rounded-xl shadow-2xs space-y-3"
                      >
                        {/* Header: Product info + Subtotal & Trash */}
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-100 pb-2.5">
                          <div className="flex items-center gap-2 min-w-0">
                            <span className="font-mono font-bold text-xs text-rose-700 bg-rose-50 border border-rose-200/60 px-2 py-0.5 rounded shrink-0">
                              {product?.sku || 'SKU'}
                            </span>
                            <span
                              className="font-semibold text-xs text-slate-900 truncate"
                              title={product?.name}
                            >
                              {product?.name || 'Product'}
                            </span>
                            {product?.baseUnit && (
                              <span className="text-[11px] text-slate-400 font-normal shrink-0">
                                • {product.baseUnit}
                              </span>
                            )}
                          </div>

                          <div className="flex items-center justify-between sm:justify-end gap-3 shrink-0">
                            <div className="flex items-center gap-1.5">
                              <span className="text-[10px] uppercase font-bold text-slate-400">
                                {t('purchases.orderDialog.subtotal')}:
                              </span>
                              <span className="font-mono font-bold text-slate-900 text-sm">
                                ${linePreview.toFixed(2)}
                              </span>
                            </div>
                            <Button
                              type="button"
                              variant="ghost"
                              size="sm"
                              className="h-7 w-7 p-0 text-slate-400 hover:text-rose-600 hover:bg-rose-50"
                              onClick={() => remove(index)}
                              title="Eliminar producto"
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </div>
                        </div>

                        {/* 4 Equal-Width Sized Inputs */}
                        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3">
                          {/* Unit of Measure */}
                          <div className="space-y-1">
                            <Label className="text-[11px] font-semibold text-slate-600">
                              {t('purchases.orderDialog.unitOfMeasure')}
                            </Label>
                            <ProductUnitSelect
                              productExternalId={field.productExternalId}
                              baseUnit={product?.baseUnit}
                              value={
                                itemValues?.unitOfMeasureExternalId ??
                                field.unitOfMeasureExternalId
                              }
                              onChange={(unitId) =>
                                setValue(
                                  `items.${index}.unitOfMeasureExternalId`,
                                  unitId,
                                )
                              }
                            />
                          </div>

                          {/* Quantity */}
                          <div className="space-y-1">
                            <Label className="text-[11px] font-semibold text-slate-600">
                              {t('purchases.orderDialog.quantity')} *
                            </Label>
                            <Input
                              type="number"
                              min="0.01"
                              step="any"
                              {...register(`items.${index}.quantity`, {
                                valueAsNumber: true,
                              })}
                              className="h-9 text-xs font-mono bg-white border-slate-200"
                              placeholder="1"
                            />
                            {errors.items?.[index]?.quantity && (
                              <p className="text-[10px] text-rose-600 font-medium">
                                {errors.items[index]?.quantity?.message}
                              </p>
                            )}
                          </div>

                          {/* Unit Cost */}
                          <div className="space-y-1">
                            <Label className="text-[11px] font-semibold text-slate-600">
                              {t('purchases.orderDialog.unitCost')} *
                            </Label>
                            <div className="relative">
                              <Input
                                type="number"
                                min="0"
                                step="0.01"
                                {...register(`items.${index}.unitCost`, {
                                  valueAsNumber: true,
                                })}
                                className="h-9 text-xs font-mono pl-6 bg-white border-slate-200"
                                placeholder="0.00"
                              />
                              <span className="absolute left-2.5 top-1/2 -translate-y-1/2 text-xs font-bold text-slate-400 pointer-events-none">
                                $
                              </span>
                            </div>
                            {errors.items?.[index]?.unitCost && (
                              <p className="text-[10px] text-rose-600 font-medium">
                                {errors.items[index]?.unitCost?.message}
                              </p>
                            )}
                          </div>

                          {/* Discount Percent */}
                          <div className="space-y-1">
                            <Label className="text-[11px] font-semibold text-slate-600">
                              {t('purchases.orderDialog.discountPercent')}
                            </Label>
                            <div className="relative">
                              <Input
                                type="number"
                                min="0"
                                max="100"
                                step="0.1"
                                {...register(`items.${index}.discountPercent`, {
                                  valueAsNumber: true,
                                })}
                                className="h-9 text-xs font-mono pr-7 bg-white border-slate-200"
                                placeholder="0"
                              />
                              <Percent className="absolute right-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400 pointer-events-none" />
                            </div>
                          </div>
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
