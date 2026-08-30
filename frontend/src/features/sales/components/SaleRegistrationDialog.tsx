import * as React from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import {
  AlertCircle,
  Building2,
  Info,
  Package,
  Percent,
  Receipt,
  ShoppingCart,
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
import { usePriceLists } from '@/features/pricing/hooks/use-pricing.ts'
import { useCustomers } from '@/features/customers/hooks/use-customers.ts'
import { CustomerSearchSelect } from '@/features/customers/components/CustomerSearchSelect.tsx'
import type { CustomerResponse } from '@/features/customers/types/index.ts'
import { registerSaleRequestSchema } from '../schemas/sale.schema.ts'
import type {
  RegisterSaleRequest,
  SaleDetailResponse,
} from '../types/index.ts'
import { useRegisterSale } from '../hooks/use-sales.ts'
import {
  type ProductOption,
  ProductSearchSelect,
} from '@/features/transfers/components/ProductSearchSelect.tsx'

interface SaleRegistrationDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: (createdSale: SaleDetailResponse) => void
}

export function SaleRegistrationDialog({
  open,
  onOpenChange,
  onSuccess,
}: SaleRegistrationDialogProps) {
  const { t } = useTranslation()
  const sessionQuery = useSession()
  const session = sessionQuery.data
  const isCorporateAdminWithoutBranch =
    session?.role === 'ADMIN' && !session?.branchId

  const [serverError, setServerError] = React.useState<string | null>(null)

  // Products, Customers and Price lists for selection
  const productsQuery = useProducts({ active: 'true', size: 100 }, open)
  const priceListsQuery = usePriceLists({ active: true, size: 50 }, open)
  const customersQuery = useCustomers({ active: true, size: 100 }, open)

  const registerMutation = useRegisterSale()

  const productOptions = React.useMemo<ProductOption[]>(() => {
    return (productsQuery.data?.content ?? []).map((p) => ({
      externalId: p.externalId,
      sku: p.sku,
      name: p.name,
      baseUnit: p.baseUnit,
      category: p.category,
    }))
  }, [productsQuery.data])

  const priceLists = priceListsQuery.data?.content ?? []
  const customers = customersQuery.data?.content ?? []

  const {
    register,
    control,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<RegisterSaleRequest>({
    resolver: zodResolver(registerSaleRequestSchema),
    defaultValues: {
      customerExternalId: undefined,
      customerName: '',
      customerTaxId: '',
      priceListExternalId: undefined,
      taxPercent: 0,
      notes: '',
      items: [],
    },
  })

  const { fields, append, remove } = useFieldArray({
    control,
    name: 'items',
  })

  const watchedPriceListId = watch('priceListExternalId')
  const watchedCustomerExternalId = watch('customerExternalId')
  const watchedItems = watch('items')

  const selectedPriceList = React.useMemo(() => {
    if (!watchedPriceListId) return undefined
    return priceLists.find((pl) => pl.externalId === watchedPriceListId)
  }, [priceLists, watchedPriceListId])

  const maxDiscountCap = selectedPriceList?.maxDiscountPercent ?? 100

  // Reset when opened
  React.useEffect(() => {
    if (open) {
      reset({
        customerExternalId: undefined,
        customerName: '',
        customerTaxId: '',
        priceListExternalId: undefined,
        taxPercent: 0,
        notes: '',
        items: [],
      })
      setServerError(null)
    }
  }, [open, reset])

  const handleSelectCustomer = (customer: CustomerResponse | null) => {
    if (customer) {
      setValue('customerExternalId', customer.externalId, {
        shouldValidate: true,
      })
      setValue('customerName', customer.name, { shouldValidate: true })
      setValue('customerTaxId', customer.taxId ?? '', { shouldValidate: true })
    } else {
      setValue('customerExternalId', undefined, { shouldValidate: true })
    }
  }

  const handleAddProduct = (product: ProductOption) => {
    setServerError(null)
    const existingIndex = fields.findIndex(
      (f) => f.productExternalId === product.externalId,
    )
    if (existingIndex >= 0) {
      const currentQty = watch(`items.${existingIndex}.quantity`) || 1
      setValue(`items.${existingIndex}.quantity`, currentQty + 1, {
        shouldValidate: true,
      })
    } else {
      append({
        productExternalId: product.externalId,
        quantity: 1,
        unitOfMeasureExternalId: undefined,
        discountPercent: 0,
      })
    }
  }

  const onSubmit = (data: RegisterSaleRequest) => {
    setServerError(null)

    // Sanitize payload
    const payload: RegisterSaleRequest = {
      ...data,
      customerExternalId: data.customerExternalId || null,
      customerTaxId: data.customerTaxId?.trim() || null,
      notes: data.notes?.trim() || null,
      priceListExternalId: data.priceListExternalId || null,
      taxPercent:
        data.taxPercent !== undefined && data.taxPercent !== null
          ? Number(data.taxPercent)
          : 0,
      items: data.items.map((item) => ({
        productExternalId: item.productExternalId,
        quantity: Number(item.quantity),
        unitOfMeasureExternalId: item.unitOfMeasureExternalId || null,
        discountPercent:
          item.discountPercent !== undefined && item.discountPercent !== null
            ? Number(item.discountPercent)
            : 0,
      })),
    }

    registerMutation.mutate(payload, {
      onSuccess: (result) => {
        onOpenChange(false)
        onSuccess?.(result)
      },
      onError: (err) => {
        setServerError(err.message || t('common.error'))
      },
    })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[92vh] overflow-y-auto">
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <DialogHeader>
            <div className="flex items-center gap-2.5 text-teal-700">
              <div className="h-9 w-9 rounded-xl bg-teal-50 border border-teal-200 flex items-center justify-center">
                <ShoppingCart className="h-5 w-5" />
              </div>
              <div>
                <DialogTitle className="text-base font-bold text-slate-900">
                  {t('sales.dialog.registerTitle')}
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-500">
                  {t('sales.dialog.registerDesc')}
                </DialogDescription>
              </div>
            </div>
          </DialogHeader>

          {isCorporateAdminWithoutBranch && (
            <Alert variant="destructive" className="py-2.5 px-3.5 text-xs">
              <Building2 className="h-4 w-4" />
              <AlertTitle className="text-xs font-semibold">
                {t('sales.dialog.branchRequired')}
              </AlertTitle>
              <AlertDescription className="text-[11px] mt-0.5">
                {t('sales.dialog.branchRequiredDesc')}
              </AlertDescription>
            </Alert>
          )}

          {serverError && (
            <Alert variant="destructive" className="py-2 px-3 text-xs">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle className="text-xs font-semibold">
                {t('common.error')}
              </AlertTitle>
              <AlertDescription className="text-[11px]">
                {serverError}
              </AlertDescription>
            </Alert>
          )}

          {/* Customer & Price List Header */}
          <div className="space-y-3 p-3.5 bg-slate-50 border border-slate-200 rounded-xl">
            {/* Customer Search / Select Combobox */}
            <div className="space-y-1">
              <Label className="text-xs font-semibold text-slate-700">
                {t('customers.selectCustomer')}
              </Label>
              <CustomerSearchSelect
                value={watchedCustomerExternalId || undefined}
                customers={customers}
                onSelectCustomer={handleSelectCustomer}
                placeholder={t('customers.searchCustomerPlaceholder')}
                disabled={isCorporateAdminWithoutBranch}
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <div className="space-y-1">
                <Label htmlFor="customer-name" className="text-xs font-semibold">
                  {t('sales.dialog.customerName')}{' '}
                  <span className="text-rose-500">*</span>
                </Label>
                <Input
                  id="customer-name"
                  {...register('customerName')}
                  placeholder={t('sales.dialog.customerNamePlaceholder')}
                  className="text-xs h-8 bg-white"
                  disabled={isCorporateAdminWithoutBranch}
                />
                {errors.customerName && (
                  <p className="text-[11px] font-medium text-rose-600">
                    {errors.customerName.message}
                  </p>
                )}
              </div>

              <div className="space-y-1">
                <Label htmlFor="customer-tax-id" className="text-xs font-semibold">
                  {t('sales.dialog.customerTaxId')}
                </Label>
                <Input
                  id="customer-tax-id"
                  {...register('customerTaxId')}
                  placeholder={t('sales.dialog.customerTaxIdPlaceholder')}
                  className="text-xs h-8 bg-white font-mono"
                  disabled={isCorporateAdminWithoutBranch}
                />
              </div>

              <div className="space-y-1">
                <Label htmlFor="price-list-select" className="text-xs font-semibold">
                  {t('sales.dialog.priceList')}
                </Label>
              <select
                id="price-list-select"
                value={watchedPriceListId || ''}
                onChange={(e: React.ChangeEvent<HTMLSelectElement>) => {
                  const val = e.target.value
                  setValue('priceListExternalId', val ? val : undefined)
                }}
                className="w-full flex h-8 rounded-md border border-slate-200 bg-white px-3 py-1 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-teal-500 disabled:opacity-50"
                disabled={isCorporateAdminWithoutBranch}
              >
                <option value="">
                  {t('sales.dialog.defaultPriceList')}
                </option>
                {priceLists.map((pl) => (
                  <option key={pl.externalId} value={pl.externalId}>
                    {pl.name} ({pl.code}) - {t('pricing.priceLists.maxDiscount')}: {pl.maxDiscountPercent}%
                  </option>
                ))}
              </select>
            </div>
          </div>
          </div>

          {/* Items Section with Central Search Bar */}
          <div className="space-y-3 pt-1">
            <div className="flex items-center justify-between border-b border-slate-100 pb-2">
              <div className="flex items-center gap-2">
                <Label className="text-xs font-bold text-slate-800 uppercase tracking-wider">
                  {t('sales.dialog.items')} *
                </Label>
                <span className="text-[11px] font-semibold bg-slate-100 text-slate-600 px-2 py-0.5 rounded-full">
                  {fields.length}{' '}
                  {fields.length === 1
                    ? t('sales.dialog.productCountSingle')
                    : t('sales.dialog.productCountPlural')}
                </span>
              </div>
              {selectedPriceList && (
                <span className="text-[11px] font-medium text-amber-700 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded-md">
                  {t('pricing.priceLists.maxDiscount')}: {selectedPriceList.maxDiscountPercent}%
                </span>
              )}
            </div>

            {/* Central Search Bar to Add Products */}
            <div className="space-y-1.5 bg-slate-50/80 p-3 rounded-xl border border-slate-200">
              <Label className="text-xs font-semibold text-slate-700 flex items-center justify-between">
                <span>{t('sales.dialog.searchAndAddProduct')}</span>
                <span className="text-[10px] text-slate-400 font-normal">
                  {t('sales.dialog.searchAndAddHelp')}
                </span>
              </Label>
              <ProductSearchSelect
                value=""
                clearOnSelect
                onSelectProduct={handleAddProduct}
                products={productOptions}
                placeholder={t('sales.dialog.searchAndAddPlaceholder')}
                disabled={isCorporateAdminWithoutBranch}
              />
            </div>

            {errors.items?.message && (
              <p className="text-[11px] font-medium text-rose-600">
                {errors.items.message}
              </p>
            )}

            {errors.items?.root?.message && (
              <p className="text-[11px] font-medium text-rose-600">
                {errors.items.root.message}
              </p>
            )}

            {/* Items List / Cards */}
            {fields.length === 0 ? (
              <div className="py-7 px-4 border-2 border-dashed border-slate-200 rounded-xl text-center bg-slate-50/50 space-y-1">
                <Package className="h-7 w-7 mx-auto text-slate-300" />
                <p className="text-xs font-semibold text-slate-700">
                  {t('sales.dialog.noProductsAdded')}
                </p>
                <p className="text-[11px] text-slate-400 max-w-sm mx-auto">
                  {t('sales.dialog.noProductsAddedDesc')}
                </p>
              </div>
            ) : (
              <div className="space-y-2 max-h-64 overflow-y-auto pr-1">
                {fields.map((field, index) => {
                  const itemErrors = errors.items?.[index]
                  const product = productOptions.find(
                    (p) => p.externalId === field.productExternalId,
                  )
                  const discountPercent =
                    watchedItems?.[index]?.discountPercent || 0
                  const isDiscountOverCap =
                    discountPercent > maxDiscountCap

                  return (
                    <SaleItemRow
                      key={field.id}
                      index={index}
                      product={product}
                      productExternalId={field.productExternalId}
                      isDiscountOverCap={isDiscountOverCap}
                      maxDiscountCap={maxDiscountCap}
                      itemErrors={itemErrors}
                      register={register}
                      onRemove={() => remove(index)}
                      disabled={isCorporateAdminWithoutBranch}
                    />
                  )
                })}
              </div>
            )}
          </div>

          {/* Notes and Taxes Row */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-1">
            <div className="md:col-span-2 space-y-1">
              <Label htmlFor="sale-notes" className="text-xs font-semibold">
                {t('sales.dialog.notes')}
              </Label>
              <Input
                id="sale-notes"
                {...register('notes')}
                placeholder={t('sales.dialog.notesPlaceholder')}
                className="text-xs h-8"
                disabled={isCorporateAdminWithoutBranch}
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="tax-percent" className="text-xs font-semibold">
                {t('sales.dialog.taxPercent')}
              </Label>
              <Input
                id="tax-percent"
                type="number"
                step="0.01"
                min="0"
                max="100"
                {...register('taxPercent', { valueAsNumber: true })}
                placeholder="0"
                className="text-xs h-8"
                disabled={isCorporateAdminWithoutBranch}
              />
            </div>
          </div>

          {/* Quick Notice */}
          <div className="p-2.5 bg-slate-50 border border-slate-200 rounded-lg text-slate-600 text-[11px] flex items-center gap-2">
            <Info className="h-4 w-4 text-teal-600 shrink-0" />
            <span>{t('sales.dialog.serverPriceNotice')}</span>
          </div>

          <DialogFooter className="gap-2 pt-2 border-t border-slate-100">
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="text-xs"
              onClick={() => onOpenChange(false)}
              disabled={registerMutation.isPending}
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="submit"
              variant="default"
              size="sm"
              className="text-xs bg-teal-600 hover:bg-teal-700 text-white"
              disabled={
                registerMutation.isPending || isCorporateAdminWithoutBranch
              }
            >
              {registerMutation.isPending ? (
                <span>{t('sales.dialog.submitting')}</span>
              ) : (
                <span className="flex items-center gap-1.5">
                  <Receipt className="h-3.5 w-3.5" />
                  {t('sales.dialog.submitSale')}
                </span>
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

interface SaleItemRowProps {
  index: number
  product?: ProductOption | undefined
  productExternalId: string
  isDiscountOverCap: boolean
  maxDiscountCap: number
  itemErrors?: any
  register: any
  onRemove: () => void
  disabled?: boolean
}

function SaleItemRow({
  index,
  product,
  productExternalId,
  isDiscountOverCap,
  maxDiscountCap,
  itemErrors,
  register,
  onRemove,
  disabled,
}: SaleItemRowProps) {
  const { t } = useTranslation()
  const unitsQuery = useProductUnits(productExternalId)
  const units = unitsQuery.data ?? []

  return (
    <div className="p-3 bg-white border border-slate-200 rounded-xl space-y-2 text-xs shadow-2xs">
      <div className="grid grid-cols-1 md:grid-cols-12 gap-3 items-center">
        {/* Product Info */}
        <div className="md:col-span-5 flex-1 min-w-0">
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="font-mono font-bold text-xs text-teal-700 bg-teal-50 px-1.5 py-0.5 rounded border border-teal-200/60">
              {product?.sku || 'SKU'}
            </span>
            {product?.category?.name && (
              <span className="text-[10px] bg-slate-100 text-slate-500 px-1.5 py-0.5 rounded">
                {product.category.name}
              </span>
            )}
          </div>
          <div className="text-xs font-semibold text-slate-900 truncate mt-1">
            {product?.name || '—'}
            {product?.baseUnit && (
              <span className="text-[11px] font-normal text-slate-400 ml-1">
                • {product.baseUnit}
              </span>
            )}
          </div>
        </div>

        {/* Unit of measure */}
        <div className="md:col-span-3 space-y-0.5">
          <Label className="text-[10px] text-slate-500 font-medium block">
            {t('sales.dialog.unitOfMeasure')}
          </Label>
          {units.length > 0 ? (
            <select
              {...register(`items.${index}.unitOfMeasureExternalId`)}
              className="w-full flex h-8 rounded-md border border-slate-200 bg-white px-2 py-1 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-teal-500 disabled:opacity-50"
              disabled={disabled}
            >
              <option value="">{t('sales.dialog.baseUnit')}</option>
              {units.map((u) => (
                <option key={u.externalId} value={u.externalId}>
                  {u.unitName} (x{u.conversionFactor})
                </option>
              ))}
            </select>
          ) : (
            <div className="h-8 px-2 flex items-center bg-slate-50 border border-slate-200 rounded text-slate-500 text-[11px]">
              {t('sales.dialog.baseUnit')}
            </div>
          )}
        </div>

        {/* Quantity */}
        <div className="md:col-span-2 space-y-0.5">
          <Label className="text-[10px] text-slate-500 font-medium block">
            {t('sales.dialog.quantity')} *
          </Label>
          <Input
            type="number"
            step="1"
            min="1"
            {...register(`items.${index}.quantity`, {
              valueAsNumber: true,
            })}
            placeholder={t('sales.dialog.quantityShort')}
            className="text-xs h-8 text-right font-mono"
            disabled={disabled}
          />
          {itemErrors?.quantity && (
            <p className="text-[10px] text-rose-600">
              {itemErrors.quantity.message}
            </p>
          )}
        </div>

        {/* Discount % */}
        <div className="md:col-span-1 space-y-0.5">
          <Label className="text-[10px] text-slate-500 font-medium block">
            {t('sales.dialog.discountShort')}
          </Label>
          <div className="relative">
            <Input
              type="number"
              step="0.1"
              min="0"
              max="100"
              {...register(`items.${index}.discountPercent`, {
                valueAsNumber: true,
              })}
              placeholder="0"
              className={`text-xs h-8 pr-5 text-right font-mono ${
                isDiscountOverCap
                  ? 'border-rose-400 focus-visible:ring-rose-400'
                  : ''
              }`}
              disabled={disabled}
            />
            <Percent className="absolute right-1.5 top-1/2 -translate-y-1/2 h-2.5 w-2.5 text-slate-400" />
          </div>
          {isDiscountOverCap && (
            <p className="text-[9px] text-rose-600 font-medium leading-none">
              {t('pricing.priceLists.maxDiscount')}: {maxDiscountCap}%
            </p>
          )}
        </div>

        {/* Remove Button */}
        <div className="md:col-span-1 flex justify-center pt-3.5">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="h-8 w-8 p-0 text-slate-400 hover:text-rose-600 hover:bg-rose-50"
            onClick={onRemove}
            disabled={disabled}
            title={t('sales.dialog.removeRow')}
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  )
}
