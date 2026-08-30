import * as React from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { AlertCircle, CheckCircle2, DollarSign, Tag } from 'lucide-react'
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
import { useProducts } from '@/features/catalog/hooks/use-products.ts'
import { useBranches } from '@/features/iam/hooks/use-branches.ts'
import {
  type ProductOption,
  ProductSearchSelect,
} from '@/features/transfers/components/ProductSearchSelect.tsx'
import { setPriceRequestSchema } from '../schemas/pricing.schema.ts'
import type { PriceListResponse, SetPriceRequest } from '../types/index.ts'
import { useSetPrice } from '../hooks/use-pricing.ts'

interface SetPriceDialogProps {
  priceList: PriceListResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: () => void
}

export function SetPriceDialog({
  priceList,
  open,
  onOpenChange,
  onSuccess,
}: SetPriceDialogProps) {
  const { t } = useTranslation()
  const [serverError, setServerError] = React.useState<string | null>(null)
  const [successMessage, setSuccessMessage] = React.useState<string | null>(null)

  const productsQuery = useProducts({ active: 'true', size: 100 }, open)
  const branchesQuery = useBranches({ active: true, size: 50 }, open)

  const productOptions = React.useMemo<ProductOption[]>(() => {
    return (productsQuery.data?.content ?? []).map((p) => ({
      externalId: p.externalId,
      sku: p.sku,
      name: p.name,
      baseUnit: p.baseUnit,
      category: p.category,
    }))
  }, [productsQuery.data])

  const branches = branchesQuery.data?.content ?? []

  const setPriceMutation = useSetPrice()

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { errors },
  } = useForm<SetPriceRequest>({
    resolver: zodResolver(setPriceRequestSchema),
    defaultValues: {
      productExternalId: '',
      branchExternalId: undefined,
      unitPrice: 0,
      validFrom: '',
    },
  })

  const watchedProductId = watch('productExternalId')
  const watchedBranchId = watch('branchExternalId')

  React.useEffect(() => {
    if (open) {
      reset({
        productExternalId: '',
        branchExternalId: undefined,
        unitPrice: 0,
        validFrom: '',
      })
      setServerError(null)
      setSuccessMessage(null)
    }
  }, [open, reset])

  const onSubmit = (data: SetPriceRequest) => {
    if (!priceList) return
    setServerError(null)

    const payload: SetPriceRequest = {
      productExternalId: data.productExternalId,
      branchExternalId: data.branchExternalId || null,
      unitPrice: Number(data.unitPrice),
      validFrom: data.validFrom || null,
    }

    setPriceMutation.mutate(
      { priceListExternalId: priceList.externalId, input: payload },
      {
        onSuccess: () => {
          setSuccessMessage(t('common.success'))
          setTimeout(() => {
            onOpenChange(false)
            onSuccess?.()
          }, 600)
        },
        onError: (err) => {
          setServerError(err.message || t('common.error'))
        },
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <DialogHeader>
            <div className="flex items-center gap-2 text-violet-700">
              <div className="h-8 w-8 rounded-lg bg-violet-50 border border-violet-200 flex items-center justify-center">
                <Tag className="h-4 w-4" />
              </div>
              <div>
                <DialogTitle className="text-base font-bold text-slate-900">
                  {t('pricing.rates.setDialogTitle')}
                </DialogTitle>
                {priceList && (
                  <p className="text-xs font-semibold text-violet-700">
                    {priceList.name} ({priceList.code})
                  </p>
                )}
              </div>
            </div>
            <DialogDescription className="text-xs text-slate-500 pt-1">
              {t('pricing.rates.setDialogDesc')}
            </DialogDescription>
          </DialogHeader>

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

          {successMessage && (
            <Alert className="py-2 px-3 text-xs border-emerald-300 bg-emerald-50 text-emerald-900">
              <CheckCircle2 className="h-4 w-4 text-emerald-600" />
              <AlertTitle className="text-xs font-semibold">
                {t('common.success')}
              </AlertTitle>
              <AlertDescription className="text-[11px]">
                {successMessage}
              </AlertDescription>
            </Alert>
          )}

          {/* Product Select */}
          <div className="space-y-1.5">
            <Label className="text-xs font-semibold">
              {t('pricing.rates.product')}{' '}
              <span className="text-rose-500">*</span>
            </Label>
            <ProductSearchSelect
              products={productOptions}
              value={watchedProductId}
              onSelectProduct={(p) =>
                setValue('productExternalId', p.externalId, {
                  shouldValidate: true,
                })
              }
              error={errors.productExternalId?.message}
              placeholder={t('pricing.rates.searchProductPlaceholder')}
            />
          </div>

          {/* Scope Select (Corporate vs Branch Override) */}
          <div className="space-y-1.5">
            <Label htmlFor="price-branch-scope" className="text-xs font-semibold">
              {t('pricing.rates.scope')}
            </Label>
            <select
              id="price-branch-scope"
              value={watchedBranchId || ''}
              onChange={(e: React.ChangeEvent<HTMLSelectElement>) => {
                const val = e.target.value
                setValue('branchExternalId', val ? val : undefined)
              }}
              className="w-full flex h-9 rounded-md border border-slate-200 bg-white px-3 py-1 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-violet-500"
            >
              <option value="">
                🌐 {t('pricing.rates.corporate')}
              </option>
              {branches.map((b) => (
                <option key={b.externalId} value={b.externalId}>
                  🏢 {b.name} ({b.code}) - {t('pricing.rates.override')}
                </option>
              ))}
            </select>
            <p className="text-[10px] text-slate-400">
              {t('pricing.rates.scopeHelp')}
            </p>
          </div>

          {/* Unit Price */}
          <div className="space-y-1.5">
            <Label htmlFor="set-unit-price" className="text-xs font-semibold">
              {t('pricing.rates.unitPrice')}{' '}
              <span className="text-rose-500">*</span>
            </Label>
            <div className="relative">
              <Input
                id="set-unit-price"
                type="number"
                step="0.01"
                min="0.01"
                {...register('unitPrice', { valueAsNumber: true })}
                placeholder="0.00"
                className="text-xs h-9 pl-7 font-mono font-bold"
              />
              <DollarSign className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
            </div>
            {errors.unitPrice && (
              <p className="text-[11px] font-medium text-rose-600">
                {errors.unitPrice.message}
              </p>
            )}
          </div>

          {/* Valid From */}
          <div className="space-y-1.5">
            <Label htmlFor="set-valid-from" className="text-xs font-semibold">
              {t('pricing.rates.validFrom')}
            </Label>
            <Input
              id="set-valid-from"
              type="date"
              {...register('validFrom')}
              className="text-xs h-9"
            />
            <p className="text-[10px] text-slate-400">
              {t('pricing.rates.validFromHelp')}
            </p>
          </div>

          <DialogFooter className="gap-2 pt-2 border-t border-slate-100">
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="text-xs"
              onClick={() => onOpenChange(false)}
              disabled={setPriceMutation.isPending}
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="submit"
              variant="default"
              size="sm"
              className="text-xs bg-violet-600 hover:bg-violet-700 text-white"
              disabled={setPriceMutation.isPending}
            >
              {setPriceMutation.isPending ? (
                <span>{t('common.saving')}</span>
              ) : (
                <span>{t('pricing.rates.setPrice')}</span>
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
