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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select.tsx'
import { useBranches } from '@/features/iam/hooks/use-branches.ts'
import { useCreateRoute, useUpdateRoute } from '../hooks/use-logistics.ts'
import { createRouteRequestSchema } from '../schemas/route.schema.ts'

import type {
  CreateRouteRequest,
  RouteResponse,
  UpdateRouteRequest,
} from '../types/route.types.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  AlertCircle,
  CheckCircle2,
  Loader2,
  Route as RouteIcon,
} from 'lucide-react'

interface RouteFormDialogProps {
  routeToEdit: RouteResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function RouteFormDialog({
  routeToEdit,
  open,
  onOpenChange,
}: RouteFormDialogProps) {
  const { t } = useTranslation()
  const isEditing = Boolean(routeToEdit)
  const branchesQuery = useBranches({ active: true, size: 100 })
  const createMutation = useCreateRoute()
  const updateMutation = useUpdateRoute()

  const [serverError, setServerError] = React.useState<string | null>(null)
  const [successMessage, setSuccessMessage] = React.useState<string | null>(
    null,
  )

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { errors },
  } = useForm<CreateRouteRequest>({
    resolver: zodResolver(createRouteRequestSchema),
    defaultValues: {
      originBranchExternalId: '',
      destinationBranchExternalId: '',
      estimatedDurationHours: 24,
      transportCost: 0,
      priorityLevel: 'STANDARD',
    },
  })

  React.useEffect(() => {
    if (open) {
      if (routeToEdit) {
        reset({
          originBranchExternalId: routeToEdit.originBranch?.externalId ?? '',
          destinationBranchExternalId:
            routeToEdit.destinationBranch?.externalId ?? '',
          estimatedDurationHours: routeToEdit.estimatedDurationHours,
          transportCost: routeToEdit.transportCost,
          priorityLevel: routeToEdit.priorityLevel,
        })
      } else {
        reset({
          originBranchExternalId: '',
          destinationBranchExternalId: '',
          estimatedDurationHours: 24,
          transportCost: 0,
          priorityLevel: 'STANDARD',
        })
      }
      setServerError(null)
      setSuccessMessage(null)
    }
  }, [open, routeToEdit, reset])

  const watchedOrigin = watch('originBranchExternalId')
  const watchedDestination = watch('destinationBranchExternalId')
  const watchedPriority = watch('priorityLevel')

  const availableBranches = branchesQuery.data?.content ?? []

  const onSubmit = (data: CreateRouteRequest) => {
    setServerError(null)

    if (
      !isEditing &&
      data.originBranchExternalId === data.destinationBranchExternalId
    ) {
      setServerError('Origin and Destination branches cannot be identical.')
      return
    }

    if (isEditing && routeToEdit) {
      const updateData: UpdateRouteRequest = {
        estimatedDurationHours: data.estimatedDurationHours,
        transportCost: data.transportCost,
        priorityLevel: data.priorityLevel,
      }
      updateMutation.mutate(
        { externalId: routeToEdit.externalId, input: updateData },
        {
          onSuccess: () => {
            setSuccessMessage('Logistics route updated successfully.')
            setTimeout(() => {
              onOpenChange(false)
            }, 1200)
          },
          onError: (err) => {
            setServerError(err.message || 'Failed to update route')
          },
        },
      )
    } else {
      createMutation.mutate(data, {
        onSuccess: () => {
          setSuccessMessage('Logistics route created successfully.')
          setTimeout(() => {
            onOpenChange(false)
          }, 1200)
        },
        onError: (err) => {
          setServerError(err.message || 'Failed to create route')
        },
      })
    }
  }

  const isPending = createMutation.isPending || updateMutation.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md bg-white p-6 sm:rounded-xl">
        <DialogHeader>
          <div className="flex items-center space-x-2">
            <div className="h-8 w-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center border border-emerald-200">
              <RouteIcon className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold text-slate-900">
                {isEditing
                  ? t('logistics.routes.editRoute')
                  : t('logistics.routes.createRoute')}
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-500">
                {t('logistics.routes.dialogDesc')}
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
          {!isEditing ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {/* Origin Branch */}
              <div className="space-y-1.5">
                <Label className="text-xs font-semibold text-slate-700">
                  {t('logistics.routes.origin')} *
                </Label>
                <Select
                  value={watchedOrigin}
                  onValueChange={(val) =>
                    setValue('originBranchExternalId', val, {
                      shouldValidate: true,
                    })
                  }
                >
                  <SelectTrigger className="text-xs">
                    <SelectValue placeholder="Select Origin" />
                  </SelectTrigger>
                  <SelectContent>
                    {availableBranches.map((b) => (
                      <SelectItem
                        key={b.externalId}
                        value={b.externalId}
                        className="text-xs"
                      >
                        {b.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.originBranchExternalId && (
                  <p className="text-[11px] text-rose-600 font-medium">
                    {errors.originBranchExternalId.message}
                  </p>
                )}
              </div>

              {/* Destination Branch */}
              <div className="space-y-1.5">
                <Label className="text-xs font-semibold text-slate-700">
                  {t('logistics.routes.destination')} *
                </Label>
                <Select
                  value={watchedDestination}
                  onValueChange={(val) =>
                    setValue('destinationBranchExternalId', val, {
                      shouldValidate: true,
                    })
                  }
                >
                  <SelectTrigger className="text-xs">
                    <SelectValue placeholder="Select Destination" />
                  </SelectTrigger>
                  <SelectContent>
                    {availableBranches
                      .filter((b) => b.externalId !== watchedOrigin)
                      .map((b) => (
                        <SelectItem
                          key={b.externalId}
                          value={b.externalId}
                          className="text-xs"
                        >
                          {b.name}
                        </SelectItem>
                      ))}
                  </SelectContent>
                </Select>
                {errors.destinationBranchExternalId && (
                  <p className="text-[11px] text-rose-600 font-medium">
                    {errors.destinationBranchExternalId.message}
                  </p>
                )}
              </div>
            </div>
          ) : (
            <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg text-xs">
              <span className="text-slate-500 block mb-1">
                Ruta Configurada:
              </span>
              <p className="font-bold text-slate-900">
                {routeToEdit?.originBranch?.name} →{' '}
                {routeToEdit?.destinationBranch?.name}
              </p>
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {/* Duration (Hours) */}
            <div className="space-y-1.5">
              <Label
                htmlFor="duration"
                className="text-xs font-semibold text-slate-700"
              >
                {t('logistics.routes.durationHours')} *
              </Label>
              <Input
                id="duration"
                type="number"
                step="any"
                min="0.1"
                {...register('estimatedDurationHours', { valueAsNumber: true })}
                className="text-xs font-mono"
                placeholder="24"
              />
              {errors.estimatedDurationHours && (
                <p className="text-[11px] text-rose-600 font-medium">
                  {errors.estimatedDurationHours.message}
                </p>
              )}
            </div>

            {/* Transport Cost */}
            <div className="space-y-1.5">
              <Label
                htmlFor="cost"
                className="text-xs font-semibold text-slate-700"
              >
                {t('logistics.routes.cost')} *
              </Label>
              <Input
                id="cost"
                type="number"
                step="any"
                min="0"
                {...register('transportCost', { valueAsNumber: true })}
                className="text-xs font-mono"
                placeholder="0.00"
              />
              {errors.transportCost && (
                <p className="text-[11px] text-rose-600 font-medium">
                  {errors.transportCost.message}
                </p>
              )}
            </div>
          </div>

          {/* Priority Level */}
          <div className="space-y-1.5">
            <Label className="text-xs font-semibold text-slate-700">
              {t('logistics.routes.priority')} *
            </Label>
            <Select
              value={watchedPriority}
              onValueChange={(val) =>
                setValue(
                  'priorityLevel',
                  val as 'LOW' | 'STANDARD' | 'URGENT',
                  { shouldValidate: true },
                )
              }
            >
              <SelectTrigger className="text-xs">
                <SelectValue placeholder="Select Priority" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="LOW" className="text-xs">
                  {t('transfers.priorities.LOW')}
                </SelectItem>
                <SelectItem value="STANDARD" className="text-xs">
                  {t('transfers.priorities.STANDARD')}
                </SelectItem>
                <SelectItem
                  value="URGENT"
                  className="text-xs font-semibold text-amber-600"
                >
                  {t('transfers.priorities.URGENT')}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>

          <DialogFooter className="pt-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onOpenChange(false)}
              disabled={isPending}
              className="text-xs"
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={isPending}
              className="text-xs bg-emerald-600 hover:bg-emerald-700 text-white"
            >
              {isPending && (
                <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
              )}
              {isEditing ? t('common.save') : t('common.create')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
