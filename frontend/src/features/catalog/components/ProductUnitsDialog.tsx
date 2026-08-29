import * as React from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, AlertDescription } from '@/components/ui/alert.tsx'
import { Badge } from '@/components/ui/badge.tsx'
import { Button } from '@/components/ui/button.tsx'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog.tsx'
import { Input } from '@/components/ui/input.tsx'
import { Label } from '@/components/ui/label.tsx'
import { Skeleton } from '@/components/ui/skeleton.tsx'
import {
  useAddProductUnit,
  useDeleteProductUnit,
  useProductUnits,
  useReplaceProductUnit,
} from '../hooks/use-product-units.ts'
import {
  unitRequestSchema,
  type ProductUnitItemResponse,
  type UnitRequestInput,
} from '../schemas/product-unit.schema.ts'
import {
  AlertCircle,
  ArrowRight,
  Boxes,
  Check,
  Edit2,
  Loader2,
  Plus,
  Scale,
  Trash2,
  X,
} from 'lucide-react'

interface ProductUnitsDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  product: {
    externalId: string
    sku: string
    name: string
    baseUnit: string
  } | null
  currentActorRole?: 'ADMIN' | 'BRANCH_MANAGER' | 'OPERATOR'
}

export function ProductUnitsDialog({
  open,
  onOpenChange,
  product,
  currentActorRole = 'OPERATOR',
}: ProductUnitsDialogProps) {
  const isAdmin = currentActorRole === 'ADMIN'
  const productExternalId = product?.externalId ?? ''

  const unitsQuery = useProductUnits(productExternalId)
  const addMutation = useAddProductUnit()
  const replaceMutation = useReplaceProductUnit()
  const deleteMutation = useDeleteProductUnit()

  const [editingUnit, setEditingUnit] =
    React.useState<ProductUnitItemResponse | null>(null)
  const [serverError, setServerError] = React.useState<string | null>(null)
  const [isAdding, setIsAdding] = React.useState(false)

  const form = useForm<UnitRequestInput>({
    resolver: zodResolver(unitRequestSchema),
    defaultValues: {
      unitName: '',
      conversionFactor: 1,
      defaultSaleUnit: false,
    },
  })

  React.useEffect(() => {
    if (open) {
      setServerError(null)
      setIsAdding(false)
      setEditingUnit(null)
      form.reset({
        unitName: '',
        conversionFactor: 1,
        defaultSaleUnit: false,
      })
    }
  }, [open, productExternalId, form])

  const handleStartEdit = (unit: ProductUnitItemResponse) => {
    setServerError(null)
    setIsAdding(false)
    setEditingUnit(unit)
    form.reset({
      unitName: unit.unitName,
      conversionFactor: unit.conversionFactor,
      defaultSaleUnit: unit.defaultSaleUnit,
    })
  }

  const handleCancelForm = () => {
    setServerError(null)
    setIsAdding(false)
    setEditingUnit(null)
    form.reset({
      unitName: '',
      conversionFactor: 1,
      defaultSaleUnit: false,
    })
  }

  const onSubmit = (data: UnitRequestInput) => {
    setServerError(null)
    const payload: UnitRequestInput = {
      unitName: data.unitName.trim().toUpperCase(),
      conversionFactor: Number(data.conversionFactor),
      defaultSaleUnit: Boolean(data.defaultSaleUnit),
    }

    if (editingUnit) {
      replaceMutation.mutate(
        {
          productExternalId,
          unitExternalId: editingUnit.externalId,
          input: payload,
        },
        {
          onSuccess: () => {
            handleCancelForm()
          },
          onError: (error: any) => {
            if (error?.code === 'duplicate_product_unit') {
              setServerError(
                'This unit name or default sale unit configuration is already defined.',
              )
            } else if (error?.code === 'invalid_conversion_factor') {
              setServerError(
                'Conversion factor must be greater than zero. If the unit matches the base unit, factor must be 1.',
              )
            } else {
              setServerError(
                error?.message || 'Failed to update unit of measure.',
              )
            }
          },
        },
      )
    } else {
      addMutation.mutate(
        {
          productExternalId,
          input: payload,
        },
        {
          onSuccess: () => {
            handleCancelForm()
          },
          onError: (error: any) => {
            if (error?.code === 'duplicate_product_unit') {
              setServerError(
                'This unit name is already registered for this product.',
              )
            } else if (error?.code === 'invalid_conversion_factor') {
              setServerError(
                'Conversion factor must be greater than zero. If the unit matches the base unit, factor must be 1.',
              )
            } else {
              setServerError(
                error?.message || 'Failed to add alternative unit.',
              )
            }
          },
        },
      )
    }
  }

  const handleDelete = (unit: ProductUnitItemResponse) => {
    if (
      window.confirm(
        `Are you sure you want to delete unit "${unit.unitName}" from this product?`,
      )
    ) {
      deleteMutation.mutate(
        {
          productExternalId,
          unitExternalId: unit.externalId,
        },
        {
          onError: (error: any) => {
            setServerError(
              error?.message || 'Failed to delete unit of measure.',
            )
          },
        },
      )
    }
  }

  const isFormPending = addMutation.isPending || replaceMutation.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg bg-white border-slate-200">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <div className="h-8 w-8 rounded bg-amber-100 text-amber-800 flex items-center justify-center">
              <Scale className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold text-slate-900">
                Units of Measure & Conversions
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-500">
                Alternative measurement units and conversion factors for this
                SKU.
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {product && (
          <div className="bg-slate-50 rounded-lg p-3 border border-slate-200 flex items-center justify-between text-xs">
            <div>
              <span className="font-mono font-bold text-slate-900">
                {product.sku}
              </span>
              <span className="mx-2 text-slate-300">|</span>
              <span className="text-slate-700 font-medium">{product.name}</span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="text-[11px] text-slate-500 uppercase font-semibold">
                Base Unit:
              </span>
              <Badge
                variant="outline"
                className="bg-white font-mono text-[11px] font-bold text-slate-800 border-slate-300"
              >
                {product.baseUnit}
              </Badge>
            </div>
          </div>
        )}

        {serverError && (
          <Alert variant="destructive" className="text-xs py-2 px-3">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{serverError}</AlertDescription>
          </Alert>
        )}

        {/* Existing Units List */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider">
              Configured Alternative Units
            </h4>
            {isAdmin && !isAdding && !editingUnit && (
              <Button
                variant="outline"
                size="sm"
                className="h-7 text-xs text-slate-700 border-slate-300 cursor-pointer"
                onClick={() => {
                  setServerError(null)
                  setIsAdding(true)
                  setEditingUnit(null)
                  form.reset({
                    unitName: '',
                    conversionFactor: 1,
                    defaultSaleUnit: false,
                  })
                }}
              >
                <Plus className="h-3 w-3 mr-1" />
                Add Unit
              </Button>
            )}
          </div>

          {unitsQuery.isLoading ? (
            <div className="space-y-2 py-2">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : unitsQuery.data?.length === 0 ? (
            <div className="bg-slate-50 rounded-lg p-6 text-center border border-dashed border-slate-200">
              <Boxes className="h-6 w-6 text-slate-400 mx-auto mb-1.5" />
              <p className="text-xs font-semibold text-slate-700">
                No alternative units defined
              </p>
              <p className="text-[11px] text-slate-500 max-w-xs mx-auto mt-0.5">
                This product currently operates only in its base unit (
                {product?.baseUnit}).
              </p>
            </div>
          ) : (
            <div className="space-y-1.5 max-h-56 overflow-y-auto pr-1">
              {unitsQuery.data?.map((unit) => (
                <div
                  key={unit.externalId}
                  className="flex items-center justify-between p-2.5 rounded-md bg-white border border-slate-200 text-xs shadow-2xs"
                >
                  <div className="flex items-center gap-2">
                    <span className="font-mono font-bold text-slate-900 bg-slate-100 px-1.5 py-0.5 rounded border border-slate-200">
                      {unit.unitName}
                    </span>
                    <ArrowRight className="h-3 w-3 text-slate-400" />
                    <span className="text-slate-600 font-medium">
                      1 {unit.unitName} ={' '}
                      <strong className="text-slate-900 font-bold">
                        {unit.conversionFactor}
                      </strong>{' '}
                      {product?.baseUnit}
                    </span>
                    {unit.defaultSaleUnit && (
                      <Badge className="bg-amber-100 text-amber-900 border-amber-300 text-[10px] py-0 px-1.5 font-bold">
                        Default Sale
                      </Badge>
                    )}
                  </div>

                  {isAdmin && (
                    <div className="flex items-center gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-6 w-6 p-0 text-slate-600 hover:text-slate-900 cursor-pointer"
                        onClick={() => handleStartEdit(unit)}
                      >
                        <Edit2 className="h-3 w-3" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-6 w-6 p-0 text-red-600 hover:text-red-700 hover:bg-red-50 cursor-pointer"
                        onClick={() => handleDelete(unit)}
                        disabled={deleteMutation.isPending}
                      >
                        <Trash2 className="h-3 w-3" />
                      </Button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Inline Add / Edit Unit Form for Admin */}
        {isAdmin && (isAdding || editingUnit) && (
          <form
            onSubmit={form.handleSubmit(onSubmit)}
            className="p-3.5 bg-slate-50 border border-slate-200 rounded-lg space-y-3 pt-3"
          >
            <div className="flex items-center justify-between">
              <h5 className="text-xs font-bold text-slate-900">
                {editingUnit
                  ? `Edit Unit: ${editingUnit.unitName}`
                  : 'Define Alternative Unit'}
              </h5>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-6 w-6 p-0 text-slate-400 hover:text-slate-600 cursor-pointer"
                onClick={handleCancelForm}
              >
                <X className="h-3.5 w-3.5" />
              </Button>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label
                  htmlFor="unit-name"
                  className="text-[11px] font-semibold"
                >
                  Unit Name <span className="text-red-500">*</span>
                </Label>
                <Input
                  id="unit-name"
                  placeholder="e.g. CAJA, SACO_50KG"
                  className="text-xs font-mono uppercase bg-white"
                  disabled={isFormPending}
                  {...form.register('unitName', {
                    onChange: (e) => {
                      e.target.value = e.target.value.toUpperCase()
                    },
                  })}
                />
                {form.formState.errors.unitName && (
                  <p className="text-[10px] text-red-600 font-medium">
                    {form.formState.errors.unitName.message}
                  </p>
                )}
              </div>

              <div className="space-y-1">
                <Label
                  htmlFor="conversion-factor"
                  className="text-[11px] font-semibold"
                >
                  Factor in {product?.baseUnit}{' '}
                  <span className="text-red-500">*</span>
                </Label>
                <Input
                  id="conversion-factor"
                  type="number"
                  step="any"
                  min="0.0001"
                  placeholder="e.g. 12, 50, 0.5"
                  className="text-xs bg-white"
                  disabled={isFormPending}
                  {...form.register('conversionFactor', {
                    valueAsNumber: true,
                  })}
                />
                {form.formState.errors.conversionFactor && (
                  <p className="text-[10px] text-red-600 font-medium">
                    {form.formState.errors.conversionFactor.message}
                  </p>
                )}
              </div>
            </div>

            <div className="flex items-center gap-2 pt-1">
              <input
                id="default-sale-unit"
                type="checkbox"
                className="h-3.5 w-3.5 rounded border-slate-300 text-amber-600 focus:ring-amber-500 cursor-pointer"
                disabled={isFormPending}
                {...form.register('defaultSaleUnit')}
              />
              <Label
                htmlFor="default-sale-unit"
                className="text-xs font-normal text-slate-700 cursor-pointer"
              >
                Set as Default Sale Unit for this product
              </Label>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-200">
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="h-7 text-xs cursor-pointer"
                onClick={handleCancelForm}
                disabled={isFormPending}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                size="sm"
                className="h-7 text-xs bg-amber-700 hover:bg-amber-800 text-white cursor-pointer"
                disabled={isFormPending}
              >
                {isFormPending ? (
                  <Loader2 className="h-3 w-3 mr-1 animate-spin" />
                ) : (
                  <Check className="h-3 w-3 mr-1" />
                )}
                {editingUnit ? 'Update Unit' : 'Save Unit'}
              </Button>
            </div>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
