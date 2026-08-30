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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select.tsx'
import { useBranches } from '@/features/iam/hooks/use-branches.ts'
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { useProducts } from '@/features/catalog/hooks/use-products.ts'
import { useNetworkAvailability } from '@/features/inventory/hooks/use-inventory.ts'
import {
  ProductSearchSelect,
  type ProductOption,
} from './ProductSearchSelect.tsx'
import { useRequestTransfer } from '../hooks/use-transfers.ts'
import { requestTransferRequestSchema } from '../schemas/transfer.schema.ts'
import type { RequestTransferRequest } from '../types/transfer.types.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  AlertCircle,
  ArrowLeftRight,
  CheckCircle2,
  Loader2,
  Package,
  Trash2,
} from 'lucide-react'

interface TransferRequestDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function TransferRequestDialog({
  open,
  onOpenChange,
}: TransferRequestDialogProps) {
  const { t } = useTranslation()
  const sessionQuery = useSession()
  const session = sessionQuery.data
  const userBranchId = session?.branchId
  const isUserAdmin = session?.role === 'ADMIN'

  const [step, setStep] = React.useState<1 | 2>(1)
  const [serverError, setServerError] = React.useState<string | null>(null)
  const [successMessage, setSuccessMessage] = React.useState<string | null>(
    null,
  )

  const branchesQuery = useBranches({ active: true, size: 100 }, isUserAdmin)
  const productsQuery = useProducts({ active: 'true', size: 100 })
  const requestMutation = useRequestTransfer()

  const {
    register,
    control,
    handleSubmit,
    setValue,
    watch,
    reset,
    trigger,
    formState: { errors },
  } = useForm<RequestTransferRequest>({
    resolver: zodResolver(requestTransferRequestSchema),
    defaultValues: {
      originBranchExternalId: '',
      priority: 'STANDARD',
      notes: '',
      items: [],
    },
  })

  const { fields, append, remove } = useFieldArray({
    control,
    name: 'items',
  })

  React.useEffect(() => {
    if (open) {
      reset({
        originBranchExternalId: '',
        priority: 'STANDARD',
        notes: '',
        items: [],
      })
      setStep(1)
      setServerError(null)
      setSuccessMessage(null)
    }
  }, [open, reset])

  const handleAddProduct = (product: ProductOption) => {
    setServerError(null)
    const existingIndex = fields.findIndex(
      (f) => f.productExternalId === product.externalId,
    )
    if (existingIndex >= 0) {
      const currentQty = watch(`items.${existingIndex}.requestedQuantity`) || 1
      setValue(`items.${existingIndex}.requestedQuantity`, currentQty + 1, {
        shouldValidate: true,
      })
    } else {
      append({ productExternalId: product.externalId, requestedQuantity: 1 })
    }
  }

  const watchedItems = watch('items')
  const watchedOrigin = watch('originBranchExternalId')
  const watchedPriority = watch('priority')

  const firstProductExternalId =
    watchedItems?.[0]?.productExternalId ||
    productsQuery.data?.content[0]?.externalId ||
    ''

  const networkQuery = useNetworkAvailability(
    firstProductExternalId,
    Boolean(firstProductExternalId && open),
  )

  const availableBranches: Array<{
    externalId: string
    name: string
    code?: string | undefined
  }> = React.useMemo(() => {
    if (branchesQuery.data?.content && branchesQuery.data.content.length > 0) {
      return branchesQuery.data.content
        .filter((b) => b.externalId !== userBranchId && b.active)
        .map((b) => ({
          externalId: b.externalId,
          name: b.name,
          code: b.code,
        }))
    }
    if (networkQuery.data?.branches && networkQuery.data.branches.length > 0) {
      return networkQuery.data.branches
        .filter((b) => b.branchExternalId !== userBranchId)
        .map((b) => ({
          externalId: b.branchExternalId,
          name: b.branchName,
          code: undefined,
        }))
    }
    return []
  }, [branchesQuery.data, networkQuery.data, userBranchId])

  const selectedOriginBranch = React.useMemo(
    () => availableBranches.find((b) => b.externalId === watchedOrigin),
    [availableBranches, watchedOrigin],
  )

  const availableProducts =
    productsQuery.data?.content.filter((p) => p.active) ?? []

  const handleNextStep = async () => {
    setServerError(null)
    const isValid = await trigger(['originBranchExternalId', 'priority'])
    if (isValid) {
      setStep(2)
    }
  }

  const handlePrevStep = () => {
    setServerError(null)
    setStep(1)
  }

  const onSubmit = (data: RequestTransferRequest) => {
    setServerError(null)

    // Check duplicate products
    const productIds = data.items.map((i) => i.productExternalId)
    const hasDuplicates = new Set(productIds).size !== productIds.length
    if (hasDuplicates) {
      setServerError(
        'Duplicate items are not allowed in the same transfer request.',
      )
      return
    }

    requestMutation.mutate(data, {
      onSuccess: (res) => {
        setSuccessMessage(
          `Transfer request ${res.transferNumber} created successfully in state REQUESTED.`,
        )
        setTimeout(() => {
          onOpenChange(false)
          reset()
          setStep(1)
        }, 1500)
      },
      onError: (err) => {
        setServerError(err.message || 'Failed to create transfer request')
      },
    })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl bg-white p-6 sm:rounded-xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <div className="flex items-center space-x-2">
            <div className="h-8 w-8 rounded-lg bg-cyan-50 text-cyan-600 flex items-center justify-center border border-cyan-200">
              <ArrowLeftRight className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold text-slate-900">
                {t('transfers.dialogs.requestTitle')}
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-500">
                {step === 1
                  ? 'Paso 1 de 2: Seleccione la sucursal de origen y el nivel de prioridad.'
                  : 'Paso 2 de 2: Especifique los productos a solicitar y observaciones.'}
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {/* Step Indicator */}
        <div className="flex items-center justify-between gap-2 border-b border-slate-100 pt-1 pb-2">
          <button
            type="button"
            onClick={handlePrevStep}
            className={`flex-1 flex items-center gap-2 pb-1 text-xs font-semibold border-b-2 transition-colors text-left ${
              step === 1
                ? 'border-cyan-600 text-cyan-700'
                : 'border-transparent text-slate-400 hover:text-slate-600'
            }`}
          >
            <span
              className={`w-5 h-5 rounded-full flex items-center justify-center text-[11px] font-bold shrink-0 ${
                step === 1
                  ? 'bg-cyan-600 text-white'
                  : watchedOrigin
                    ? 'bg-emerald-100 text-emerald-700'
                    : 'bg-slate-100 text-slate-500'
              }`}
            >
              1
            </span>
            <span className="truncate">
              1. {t('transfers.originBranch')} & {t('transfers.priority')}
            </span>
          </button>

          <button
            type="button"
            onClick={handleNextStep}
            className={`flex-1 flex items-center gap-2 pb-1 text-xs font-semibold border-b-2 transition-colors text-left ${
              step === 2
                ? 'border-cyan-600 text-cyan-700'
                : 'border-transparent text-slate-400 hover:text-slate-600'
            }`}
          >
            <span
              className={`w-5 h-5 rounded-full flex items-center justify-center text-[11px] font-bold shrink-0 ${
                step === 2
                  ? 'bg-cyan-600 text-white'
                  : 'bg-slate-100 text-slate-500'
              }`}
            >
              2
            </span>
            <span className="truncate">
              2. {t('transfers.items')} & {t('transfers.notes')}
            </span>
          </button>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 py-2">
          {serverError && (
            <Alert variant="destructive" className="py-2.5">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle className="text-xs font-semibold">Error</AlertTitle>
              <AlertDescription className="text-xs">
                {serverError}
              </AlertDescription>
            </Alert>
          )}

          {successMessage && (
            <Alert className="py-2.5 bg-emerald-50 border-emerald-200 text-emerald-800">
              <CheckCircle2 className="h-4 w-4 text-emerald-600" />
              <AlertTitle className="text-xs font-semibold">Success</AlertTitle>
              <AlertDescription className="text-xs">
                {successMessage}
              </AlertDescription>
            </Alert>
          )}

          {/* FASE 1: Sucursal de Origen y Prioridad */}
          {step === 1 && (
            <div className="space-y-4 animate-in fade-in-50 duration-150">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {/* Origin Branch Select */}
                <div className="space-y-1.5">
                  <Label className="text-xs font-semibold text-slate-700">
                    {t('transfers.originBranch')} *
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
                      <SelectValue placeholder="Select Origin Branch" />
                    </SelectTrigger>
                    <SelectContent>
                      {availableBranches.length === 0 ? (
                        <div className="py-2 px-3 text-xs text-slate-400 text-center">
                          {branchesQuery.isLoading || networkQuery.isLoading
                            ? 'Cargando sucursales...'
                            : 'No hay sucursales disponibles'}
                        </div>
                      ) : (
                        availableBranches.map((b) => (
                          <SelectItem
                            key={b.externalId}
                            value={b.externalId}
                            className="text-xs"
                          >
                            {b.name}
                            {b.code ? ` (${b.code})` : ''}
                          </SelectItem>
                        ))
                      )}
                    </SelectContent>
                  </Select>
                  {errors.originBranchExternalId && (
                    <p className="text-[11px] text-rose-600 font-medium">
                      {errors.originBranchExternalId.message}
                    </p>
                  )}
                </div>

                {/* Priority Select */}
                <div className="space-y-1.5">
                  <Label className="text-xs font-semibold text-slate-700">
                    {t('transfers.priority')} *
                  </Label>
                  <Select
                    value={watchedPriority}
                    onValueChange={(val) =>
                      setValue(
                        'priority',
                        val as 'LOW' | 'STANDARD' | 'URGENT',
                        {
                          shouldValidate: true,
                        },
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
                  {errors.priority && (
                    <p className="text-[11px] text-rose-600 font-medium">
                      {errors.priority.message}
                    </p>
                  )}
                </div>
              </div>

              <div className="p-3 bg-cyan-50/50 rounded-lg border border-cyan-100 text-xs text-cyan-800 space-y-1">
                <p className="font-semibold text-cyan-900">
                  ℹ️ Proceso de Solicitud en Dos Fases:
                </p>
                <p className="text-cyan-700">
                  Defina la sucursal de donde se extraerá el stock y la
                  prioridad. En el siguiente paso podrá buscar y seleccionar los
                  productos requeridos.
                </p>
              </div>
            </div>
          )}

          {/* FASE 2: Items de Transferencia y Observaciones */}
          {step === 2 && (
            <div className="space-y-4 animate-in fade-in-50 duration-150">
              {/* Origin & Priority Summary Card */}
              <div className="flex items-center justify-between p-2.5 rounded-lg bg-slate-50 border border-slate-200">
                <div className="flex items-center gap-3">
                  <div>
                    <span className="text-[10px] uppercase font-bold text-slate-400 block tracking-wider">
                      {t('transfers.originBranch')}
                    </span>
                    <span className="text-xs font-semibold text-slate-800">
                      {selectedOriginBranch?.name ||
                        (selectedOriginBranch?.code
                          ? `Sucursal ${selectedOriginBranch.code}`
                          : 'Sucursal Seleccionada')}
                    </span>
                  </div>
                  <div className="h-6 w-px bg-slate-200" />
                  <div>
                    <span className="text-[10px] uppercase font-bold text-slate-400 block tracking-wider">
                      {t('transfers.priority')}
                    </span>
                    <span
                      className={`text-[11px] font-bold px-1.5 py-0.5 rounded ${
                        watchedPriority === 'URGENT'
                          ? 'bg-amber-100 text-amber-700'
                          : watchedPriority === 'LOW'
                            ? 'bg-slate-100 text-slate-600'
                            : 'bg-blue-100 text-blue-700'
                      }`}
                    >
                      {t(`transfers.priorities.${watchedPriority}`)}
                    </span>
                  </div>
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={handlePrevStep}
                  className="text-xs h-7 text-cyan-700 hover:text-cyan-800 hover:bg-cyan-50"
                >
                  Cambiar
                </Button>
              </div>

              {/* Dynamic Items List with Central Search Bar */}
              <div className="space-y-3 pt-1">
                <div className="flex justify-between items-center border-b pb-2">
                  <div className="flex items-center gap-2">
                    <Label className="text-xs font-bold text-slate-800">
                      {t('transfers.items')} *
                    </Label>
                    <span className="text-[11px] font-semibold bg-slate-100 text-slate-600 px-2 py-0.5 rounded-full">
                      {fields.length} {fields.length === 1 ? 'producto' : 'productos'}
                    </span>
                  </div>
                </div>

                {/* Central Search Bar to Add Products */}
                <div className="space-y-1.5 bg-slate-50/80 p-3 rounded-lg border border-slate-200">
                  <Label className="text-xs font-semibold text-slate-700 flex items-center justify-between">
                    <span>Buscar y agregar producto</span>
                    <span className="text-[10px] text-slate-400 font-normal">
                      Por SKU, nombre o categoría
                    </span>
                  </Label>
                  <ProductSearchSelect
                    value=""
                    clearOnSelect
                    onSelectProduct={handleAddProduct}
                    products={availableProducts}
                    placeholder="Escriba para buscar por SKU o nombre y agregar..."
                  />
                </div>

                {errors.items?.message && (
                  <p className="text-[11px] text-rose-600 font-medium">
                    {errors.items.message}
                  </p>
                )}

                {/* Items Table / Cards */}
                {fields.length === 0 ? (
                  <div className="py-7 px-4 border-2 border-dashed border-slate-200 rounded-lg text-center bg-slate-50/50 space-y-1">
                    <Package className="h-7 w-7 mx-auto text-slate-300" />
                    <p className="text-xs font-semibold text-slate-700">
                      No hay productos agregados
                    </p>
                    <p className="text-[11px] text-slate-400 max-w-sm mx-auto">
                      Use la barra de búsqueda superior para seleccionar y agregar productos a la solicitud.
                    </p>
                  </div>
                ) : (
                  <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
                    {fields.map((field, idx) => {
                      const prod = availableProducts.find(
                        (p) => p.externalId === field.productExternalId,
                      )
                      return (
                        <div
                          key={field.id}
                          className="flex items-center justify-between gap-3 p-2.5 rounded-lg bg-white border border-slate-200 hover:border-slate-300 transition-colors shadow-2xs"
                        >
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-1.5 flex-wrap">
                              <span className="font-mono font-bold text-xs text-emerald-700 bg-emerald-50 px-1.5 py-0.5 rounded border border-emerald-200/60">
                                {prod?.sku || 'SKU'}
                              </span>
                              {prod?.category?.name && (
                                <span className="text-[10px] bg-slate-100 text-slate-500 px-1.5 py-0.5 rounded">
                                  {prod.category.name}
                                </span>
                              )}
                            </div>
                            <div className="text-xs font-medium text-slate-800 truncate mt-1">
                              {prod?.name || 'Producto'}
                              {prod?.baseUnit && (
                                <span className="text-[11px] font-normal text-slate-400 ml-1">
                                  • {prod.baseUnit}
                                </span>
                              )}
                            </div>
                          </div>

                          <div className="flex items-end gap-2 shrink-0">
                            <div className="space-y-0.5">
                              <Label className="text-[10px] text-slate-500 font-medium block">
                                Cantidad *
                              </Label>
                              <Input
                                type="number"
                                step="any"
                                min="0.001"
                                {...register(`items.${idx}.requestedQuantity`, {
                                  valueAsNumber: true,
                                })}
                                className="text-xs font-mono bg-white h-8 w-24 text-right"
                                placeholder="1"
                              />
                            </div>

                            <Button
                              type="button"
                              variant="ghost"
                              size="sm"
                              onClick={() => remove(idx)}
                              className="h-8 w-8 p-0 text-slate-400 hover:text-rose-600 hover:bg-rose-50 shrink-0"
                              title={t('transfers.dialogs.removeItem')}
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>

              {/* Notes */}
              <div className="space-y-1.5 pt-1">
                <Label
                  htmlFor="notes"
                  className="text-xs font-semibold text-slate-700"
                >
                  {t('transfers.notes')}
                </Label>
                <Input
                  id="notes"
                  type="text"
                  {...register('notes')}
                  className="text-xs"
                  placeholder={t('transfers.dialogs.notesPrompt')}
                />
              </div>
            </div>
          )}

          {/* Footer Controls based on Step */}
          <DialogFooter className="pt-3 flex justify-between sm:justify-between items-center gap-2">
            {step === 1 ? (
              <>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => onOpenChange(false)}
                  disabled={requestMutation.isPending}
                  className="text-xs"
                >
                  {t('common.cancel')}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  onClick={handleNextStep}
                  className="text-xs bg-cyan-600 hover:bg-cyan-700 text-white"
                >
                  Siguiente: Productos →
                </Button>
              </>
            ) : (
              <>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handlePrevStep}
                  disabled={requestMutation.isPending}
                  className="text-xs"
                >
                  ← Volver a Origen
                </Button>
                <div className="flex items-center gap-2">
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => onOpenChange(false)}
                    disabled={requestMutation.isPending}
                    className="text-xs"
                  >
                    {t('common.cancel')}
                  </Button>
                  <Button
                    type="submit"
                    size="sm"
                    disabled={requestMutation.isPending}
                    className="text-xs bg-cyan-600 hover:bg-cyan-700 text-white"
                  >
                    {requestMutation.isPending && (
                      <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
                    )}
                    {t('transfers.requestTransfer')}
                  </Button>
                </div>
              </>
            )}
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
