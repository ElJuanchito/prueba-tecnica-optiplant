import * as React from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { AlertCircle, UserPlus, Users } from 'lucide-react'
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
  createCustomerRequestSchema,
  editCustomerRequestSchema,
} from '../schemas/customer.schema.ts'
import type {
  CreateCustomerRequest,
  CustomerResponse,
} from '../types/index.ts'
import {
  useCreateCustomer,
  useUpdateCustomer,
} from '../hooks/use-customers.ts'

interface CustomerFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  customerToEdit?: CustomerResponse | null | undefined
  onSuccess?: ((customer: CustomerResponse) => void) | undefined
}

export function CustomerFormDialog({
  open,
  onOpenChange,
  customerToEdit,
  onSuccess,
}: CustomerFormDialogProps) {
  const { t } = useTranslation()
  const isEditing = Boolean(customerToEdit)
  const [serverError, setServerError] = React.useState<string | null>(null)

  const createMutation = useCreateCustomer()
  const updateMutation = useUpdateCustomer()
  const isPending = createMutation.isPending || updateMutation.isPending

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CreateCustomerRequest>({
    resolver: zodResolver(
      isEditing ? editCustomerRequestSchema : createCustomerRequestSchema,
    ),
    defaultValues: {
      name: '',
      taxId: '',
      email: '',
      phone: '',
      address: '',
    },
  })

  React.useEffect(() => {
    if (open) {
      if (customerToEdit) {
        reset({
          name: customerToEdit.name,
          taxId: customerToEdit.taxId ?? '',
          email: customerToEdit.email ?? '',
          phone: customerToEdit.phone ?? '',
          address: customerToEdit.address ?? '',
        })
      } else {
        reset({
          name: '',
          taxId: '',
          email: '',
          phone: '',
          address: '',
        })
      }
      setServerError(null)
    }
  }, [open, customerToEdit, reset])

  const onSubmit = (data: CreateCustomerRequest) => {
    setServerError(null)

    const payload: CreateCustomerRequest = {
      name: data.name.trim(),
      taxId: data.taxId?.trim() ? data.taxId.trim() : null,
      email: data.email?.trim() ? data.email.trim() : null,
      phone: data.phone?.trim() ? data.phone.trim() : null,
      address: data.address?.trim() ? data.address.trim() : null,
    }

    if (isEditing && customerToEdit) {
      updateMutation.mutate(
        {
          externalId: customerToEdit.externalId,
          input: payload,
        },
        {
          onSuccess: (result) => {
            onOpenChange(false)
            onSuccess?.(result)
          },
          onError: (err) => {
            setServerError(err.message || t('common.error'))
          },
        },
      )
    } else {
      createMutation.mutate(payload, {
        onSuccess: (result) => {
          onOpenChange(false)
          onSuccess?.(result)
        },
        onError: (err) => {
          setServerError(err.message || t('common.error'))
        },
      })
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <DialogHeader>
            <div className="flex items-center gap-2.5 text-sky-700">
              <div className="h-9 w-9 rounded-xl bg-sky-50 border border-sky-200 flex items-center justify-center">
                {isEditing ? (
                  <Users className="h-5 w-5" />
                ) : (
                  <UserPlus className="h-5 w-5" />
                )}
              </div>
              <div>
                <DialogTitle className="text-base font-bold text-slate-900">
                  {isEditing
                    ? t('customers.dialog.editTitle')
                    : t('customers.dialog.createTitle')}
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-500">
                  {isEditing
                    ? t('customers.dialog.editDesc')
                    : t('customers.dialog.createDesc')}
                </DialogDescription>
              </div>
            </div>
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

          <div className="space-y-3 py-1">
            {/* Customer Name */}
            <div className="space-y-1">
              <Label htmlFor="customer-name" className="text-xs font-semibold">
                {t('customers.dialog.name')}{' '}
                <span className="text-rose-500">*</span>
              </Label>
              <Input
                id="customer-name"
                {...register('name')}
                placeholder={t('customers.dialog.namePlaceholder')}
                className="text-xs h-8 bg-white"
                maxLength={150}
              />
              {errors.name && (
                <p className="text-[11px] font-medium text-rose-600">
                  {errors.name.message}
                </p>
              )}
            </div>

            {/* Tax ID */}
            <div className="space-y-1">
              <Label htmlFor="customer-tax-id" className="text-xs font-semibold">
                {t('customers.dialog.taxId')}
              </Label>
              <Input
                id="customer-tax-id"
                {...register('taxId')}
                placeholder={t('customers.dialog.taxIdPlaceholder')}
                className="text-xs h-8 bg-white font-mono"
                maxLength={30}
              />
              <p className="text-[10px] text-slate-400">
                {t('customers.dialog.taxIdUniqueNotice')}
              </p>
              {errors.taxId && (
                <p className="text-[11px] font-medium text-rose-600">
                  {errors.taxId.message}
                </p>
              )}
            </div>

            {/* Contact row: Email and Phone */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label htmlFor="customer-email" className="text-xs font-semibold">
                  {t('customers.dialog.email')}
                </Label>
                <Input
                  id="customer-email"
                  type="email"
                  {...register('email')}
                  placeholder={t('customers.dialog.emailPlaceholder')}
                  className="text-xs h-8 bg-white"
                  maxLength={100}
                />
                {errors.email && (
                  <p className="text-[11px] font-medium text-rose-600">
                    {errors.email.message}
                  </p>
                )}
              </div>

              <div className="space-y-1">
                <Label htmlFor="customer-phone" className="text-xs font-semibold">
                  {t('customers.dialog.phone')}
                </Label>
                <Input
                  id="customer-phone"
                  type="tel"
                  {...register('phone')}
                  placeholder={t('customers.dialog.phonePlaceholder')}
                  className="text-xs h-8 bg-white"
                  maxLength={50}
                />
                {errors.phone && (
                  <p className="text-[11px] font-medium text-rose-600">
                    {errors.phone.message}
                  </p>
                )}
              </div>
            </div>

            {/* Address */}
            <div className="space-y-1">
              <Label htmlFor="customer-address" className="text-xs font-semibold">
                {t('customers.dialog.address')}
              </Label>
              <Input
                id="customer-address"
                {...register('address')}
                placeholder={t('customers.dialog.addressPlaceholder')}
                className="text-xs h-8 bg-white"
                maxLength={255}
              />
              {errors.address && (
                <p className="text-[11px] font-medium text-rose-600">
                  {errors.address.message}
                </p>
              )}
            </div>
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
              className="text-xs bg-sky-600 hover:bg-sky-700 text-white font-semibold"
              disabled={isPending}
            >
              {isPending ? (
                <span>{t('customers.dialog.submitting')}</span>
              ) : isEditing ? (
                <span>{t('common.save')}</span>
              ) : (
                <span className="flex items-center gap-1.5">
                  <UserPlus className="h-3.5 w-3.5" />
                  {t('common.create')}
                </span>
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
