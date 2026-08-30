import * as React from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { AlertCircle, BadgePercent, CheckCircle2 } from 'lucide-react'
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
import {
  createPriceListRequestSchema,
} from '../schemas/pricing.schema.ts'
import type {
  CreatePriceListRequest,
  PriceListResponse,
  UpdatePriceListRequest,
} from '../types/index.ts'
import {
  useCreatePriceList,
  useUpdatePriceList,
} from '../hooks/use-pricing.ts'

interface PriceListFormDialogProps {
  priceListToEdit?: PriceListResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: () => void
}

export function PriceListFormDialog({
  priceListToEdit,
  open,
  onOpenChange,
  onSuccess,
}: PriceListFormDialogProps) {
  const { t } = useTranslation()
  const isEditing = Boolean(priceListToEdit)
  const [serverError, setServerError] = React.useState<string | null>(null)
  const [successMessage, setSuccessMessage] = React.useState<string | null>(null)

  const createMutation = useCreatePriceList()
  const updateMutation = useUpdatePriceList()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CreatePriceListRequest>({
    resolver: zodResolver(createPriceListRequestSchema),
    defaultValues: {
      code: '',
      name: '',
      description: '',
      maxDiscountPercent: 20,
    },
  })

  React.useEffect(() => {
    if (open) {
      if (priceListToEdit) {
        reset({
          code: priceListToEdit.code,
          name: priceListToEdit.name,
          description: priceListToEdit.description || '',
          maxDiscountPercent: priceListToEdit.maxDiscountPercent,
        })
      } else {
        reset({
          code: '',
          name: '',
          description: '',
          maxDiscountPercent: 20,
        })
      }
      setServerError(null)
      setSuccessMessage(null)
    }
  }, [open, priceListToEdit, reset])

  const onSubmit = (data: CreatePriceListRequest) => {
    setServerError(null)

    if (isEditing && priceListToEdit) {
      const updateData: UpdatePriceListRequest = {
        name: data.name,
        description: data.description?.trim() || null,
        maxDiscountPercent: Number(data.maxDiscountPercent),
      }
      updateMutation.mutate(
        { externalId: priceListToEdit.externalId, input: updateData },
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
    } else {
      const createData: CreatePriceListRequest = {
        code: data.code.trim().toUpperCase(),
        name: data.name.trim(),
        description: data.description?.trim() || null,
        maxDiscountPercent: Number(data.maxDiscountPercent),
      }
      createMutation.mutate(createData, {
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
      })
    }
  }

  const isPending = createMutation.isPending || updateMutation.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <DialogHeader>
            <div className="flex items-center gap-2 text-violet-700">
              <div className="h-8 w-8 rounded-lg bg-violet-50 border border-violet-200 flex items-center justify-center">
                <BadgePercent className="h-4 w-4" />
              </div>
              <DialogTitle className="text-base font-bold text-slate-900">
                {isEditing
                  ? t('pricing.priceLists.edit')
                  : t('pricing.priceLists.create')}
              </DialogTitle>
            </div>
            <DialogDescription className="text-xs text-slate-500 pt-1">
              {t('pricing.priceLists.dialogDesc')}
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

          {/* Code */}
          <div className="space-y-1.5">
            <Label htmlFor="price-list-code" className="text-xs font-semibold">
              {t('pricing.priceLists.code')}{' '}
              <span className="text-rose-500">*</span>
            </Label>
            <Input
              id="price-list-code"
              {...register('code')}
              placeholder={t('pricing.priceLists.codePlaceholder')}
              className="text-xs h-9 uppercase font-mono"
              disabled={isEditing || isPending}
            />
            {errors.code && (
              <p className="text-[11px] font-medium text-rose-600">
                {errors.code.message}
              </p>
            )}
          </div>

          {/* Name */}
          <div className="space-y-1.5">
            <Label htmlFor="price-list-name" className="text-xs font-semibold">
              {t('pricing.priceLists.name')}{' '}
              <span className="text-rose-500">*</span>
            </Label>
            <Input
              id="price-list-name"
              {...register('name')}
              placeholder={t('pricing.priceLists.namePlaceholder')}
              className="text-xs h-9"
              disabled={isPending}
            />
            {errors.name && (
              <p className="text-[11px] font-medium text-rose-600">
                {errors.name.message}
              </p>
            )}
          </div>

          {/* Description */}
          <div className="space-y-1.5">
            <Label
              htmlFor="price-list-description"
              className="text-xs font-semibold"
            >
              {t('pricing.priceLists.description')}
            </Label>
            <Input
              id="price-list-description"
              {...register('description')}
              placeholder={t('pricing.priceLists.descriptionPlaceholder')}
              className="text-xs h-9"
              disabled={isPending}
            />
          </div>

          {/* Max Discount % */}
          <div className="space-y-1.5">
            <Label
              htmlFor="price-list-max-discount"
              className="text-xs font-semibold"
            >
              {t('pricing.priceLists.maxDiscount')}{' '}
              <span className="text-rose-500">*</span>
            </Label>
            <Input
              id="price-list-max-discount"
              type="number"
              step="0.1"
              min="0"
              max="100"
              {...register('maxDiscountPercent', { valueAsNumber: true })}
              placeholder="20"
              className="text-xs h-9 font-mono"
              disabled={isPending}
            />
            {errors.maxDiscountPercent && (
              <p className="text-[11px] font-medium text-rose-600">
                {errors.maxDiscountPercent.message}
              </p>
            )}
          </div>

          <DialogFooter className="gap-2 pt-2 border-t border-slate-100">
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="text-xs"
              onClick={() => onOpenChange(false)}
              disabled={isPending}
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="submit"
              variant="default"
              size="sm"
              className="text-xs bg-violet-600 hover:bg-violet-700 text-white"
              disabled={isPending}
            >
              {isPending
                ? t('common.saving')
                : isEditing
                  ? t('common.save')
                  : t('common.create')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
