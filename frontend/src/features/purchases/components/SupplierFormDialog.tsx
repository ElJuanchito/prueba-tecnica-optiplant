import * as React from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Building2 } from 'lucide-react'
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
import type {
  CreateSupplierRequest,
  SupplierResponse,
  UpdateSupplierRequest,
} from '../types/index.ts'
import { useCreateSupplier, useUpdateSupplier } from '../hooks/use-purchases.ts'
import { ApiError } from '@/lib/api-client.ts'

interface SupplierFormDialogProps {
  supplierToEdit?: SupplierResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: (supplier: SupplierResponse) => void
}

const supplierFormSchema = z.object({
  taxId: z.string().trim().min(1, 'Tax ID is required'),
  name: z.string().trim().min(1, 'Name is required'),
  contactName: z.string().trim().optional(),
  email: z
    .string()
    .trim()
    .email('Invalid email address')
    .optional()
    .or(z.literal('')),
  phone: z.string().trim().optional(),
  address: z.string().trim().optional(),
})

type FormValues = z.infer<typeof supplierFormSchema>

export function SupplierFormDialog({
  supplierToEdit,
  open,
  onOpenChange,
  onSuccess,
}: SupplierFormDialogProps) {
  const { t } = useTranslation()
  const isEditing = Boolean(supplierToEdit)

  const [serverError, setServerError] = React.useState<string | null>(null)

  const createMutation = useCreateSupplier()
  const updateMutation = useUpdateSupplier()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(supplierFormSchema),
    defaultValues: {
      taxId: '',
      name: '',
      contactName: '',
      email: '',
      phone: '',
      address: '',
    },
  })

  React.useEffect(() => {
    if (open) {
      if (supplierToEdit) {
        reset({
          taxId: supplierToEdit.taxId,
          name: supplierToEdit.name,
          contactName: supplierToEdit.contactName ?? '',
          email: supplierToEdit.email ?? '',
          phone: supplierToEdit.phone ?? '',
          address: supplierToEdit.address ?? '',
        })
      } else {
        reset({
          taxId: '',
          name: '',
          contactName: '',
          email: '',
          phone: '',
          address: '',
        })
      }
      setServerError(null)
    }
  }, [open, supplierToEdit, reset])

  const onSubmit = (data: FormValues) => {
    setServerError(null)

    const sanitized = {
      name: data.name.trim(),
      contactName: data.contactName?.trim() || null,
      email: data.email?.trim() || null,
      phone: data.phone?.trim() || null,
      address: data.address?.trim() || null,
    }

    if (isEditing && supplierToEdit) {
      const updatePayload: UpdateSupplierRequest = sanitized
      updateMutation.mutate(
        { externalId: supplierToEdit.externalId, input: updatePayload },
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
      const createPayload: CreateSupplierRequest = {
        taxId: data.taxId.trim(),
        ...sanitized,
      }
      createMutation.mutate(createPayload, {
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
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogHeader>
            <div className="flex items-center gap-2 text-rose-600 mb-1">
              <Building2 className="h-5 w-5" />
              <DialogTitle className="text-base font-bold text-slate-900">
                {isEditing
                  ? t('purchases.suppliers.editDialogTitle')
                  : t('purchases.suppliers.createDialogTitle')}
              </DialogTitle>
            </div>
            <DialogDescription className="text-xs text-slate-500">
              {isEditing
                ? t('purchases.suppliers.editDialogDesc')
                : t('purchases.suppliers.createDialogDesc')}
            </DialogDescription>
          </DialogHeader>

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

          <div className="space-y-3.5 py-4">
            {/* Tax ID */}
            <div className="space-y-1">
              <Label
                htmlFor="taxId"
                className="text-xs font-semibold text-slate-700"
              >
                {t('purchases.suppliers.taxId')}{' '}
                <span className="text-rose-500">*</span>
              </Label>
              <Input
                id="taxId"
                disabled={isEditing}
                placeholder={t('purchases.suppliers.taxIdPlaceholder')}
                {...register('taxId')}
                className="text-xs h-9 bg-slate-50 border-slate-200 font-mono disabled:opacity-60"
              />
              {errors.taxId && (
                <p className="text-[11px] text-rose-600 font-medium">
                  {errors.taxId.message}
                </p>
              )}
            </div>

            {/* Name */}
            <div className="space-y-1">
              <Label
                htmlFor="name"
                className="text-xs font-semibold text-slate-700"
              >
                {t('purchases.suppliers.name')}{' '}
                <span className="text-rose-500">*</span>
              </Label>
              <Input
                id="name"
                placeholder={t('purchases.suppliers.namePlaceholder')}
                {...register('name')}
                className="text-xs h-9 bg-slate-50 border-slate-200"
              />
              {errors.name && (
                <p className="text-[11px] text-rose-600 font-medium">
                  {errors.name.message}
                </p>
              )}
            </div>

            {/* Contact Person & Phone */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label
                  htmlFor="contactName"
                  className="text-xs font-semibold text-slate-700"
                >
                  {t('purchases.suppliers.contactName')}
                </Label>
                <Input
                  id="contactName"
                  placeholder={t('purchases.suppliers.contactNamePlaceholder')}
                  {...register('contactName')}
                  className="text-xs h-9 bg-slate-50 border-slate-200"
                />
              </div>

              <div className="space-y-1">
                <Label
                  htmlFor="phone"
                  className="text-xs font-semibold text-slate-700"
                >
                  {t('purchases.suppliers.phone')}
                </Label>
                <Input
                  id="phone"
                  placeholder={t('purchases.suppliers.phonePlaceholder')}
                  {...register('phone')}
                  className="text-xs h-9 bg-slate-50 border-slate-200"
                />
              </div>
            </div>

            {/* Email & Address */}
            <div className="space-y-1">
              <Label
                htmlFor="email"
                className="text-xs font-semibold text-slate-700"
              >
                {t('purchases.suppliers.email')}
              </Label>
              <Input
                id="email"
                type="email"
                placeholder={t('purchases.suppliers.emailPlaceholder')}
                {...register('email')}
                className="text-xs h-9 bg-slate-50 border-slate-200"
              />
              {errors.email && (
                <p className="text-[11px] text-rose-600 font-medium">
                  {errors.email.message}
                </p>
              )}
            </div>

            <div className="space-y-1">
              <Label
                htmlFor="address"
                className="text-xs font-semibold text-slate-700"
              >
                {t('purchases.suppliers.address')}
              </Label>
              <Input
                id="address"
                placeholder={t('purchases.suppliers.addressPlaceholder')}
                {...register('address')}
                className="text-xs h-9 bg-slate-50 border-slate-200"
              />
            </div>
          </div>

          <DialogFooter className="gap-2 sm:gap-0">
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
              disabled={isSubmitting}
              className="text-xs bg-rose-600 hover:bg-rose-700 text-white font-semibold shadow-xs"
            >
              {isSubmitting
                ? t('purchases.suppliers.submitting')
                : t('common.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
