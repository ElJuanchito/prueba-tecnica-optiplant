import * as React from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Alert, AlertDescription } from '@/components/ui/alert.tsx'
import { Badge } from '@/components/ui/badge.tsx'
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
import { useCategories } from '../hooks/use-categories.ts'
import { useCreateProduct, useEditProduct } from '../hooks/use-products.ts'
import type {
  ProductDetailResponse,
  ProductListItemResponse,
} from '../types/product.types.ts'
import { AlertCircle, Boxes, Info, Loader2 } from 'lucide-react'

const productFormSchema = z.object({
  sku: z
    .string()
    .trim()
    .min(1, 'SKU is required')
    .max(50, 'SKU must be at most 50 characters'),
  name: z
    .string()
    .trim()
    .min(1, 'Product name is required')
    .max(150, 'Product name must be at most 150 characters'),
  description: z.string().trim().optional(),
  categoryExternalId: z.string().min(1, 'Category is required'),
  baseUnit: z.string().trim().optional(),
})

type ProductFormValues = z.infer<typeof productFormSchema>

interface ProductFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  productToEdit?:
    ProductDetailResponse | ProductListItemResponse | null | undefined
}

export function ProductFormDialog({
  open,
  onOpenChange,
  productToEdit,
}: ProductFormDialogProps) {
  const isEdit = Boolean(productToEdit)
  const categoriesQuery = useCategories({ active: 'true', size: 100 })
  const createMutation = useCreateProduct()
  const editMutation = useEditProduct()
  const [serverError, setServerError] = React.useState<string | null>(null)

  const form = useForm<ProductFormValues>({
    resolver: zodResolver(productFormSchema),
    defaultValues: {
      sku: '',
      name: '',
      description: '',
      categoryExternalId: '',
      baseUnit: '',
    },
  })

  React.useEffect(() => {
    if (open) {
      setServerError(null)
      if (productToEdit) {
        form.reset({
          sku: productToEdit.sku,
          name: productToEdit.name,
          description:
            ('description' in productToEdit ? productToEdit.description : '') ??
            '',
          categoryExternalId: productToEdit.category?.externalId ?? '',
          baseUnit: productToEdit.baseUnit,
        })
      } else {
        form.reset({
          sku: '',
          name: '',
          description: '',
          categoryExternalId: '',
          baseUnit: 'UNIDAD',
        })
      }
    }
  }, [open, productToEdit, form])

  const onSubmit = (data: ProductFormValues) => {
    setServerError(null)

    if (isEdit && productToEdit) {
      const editPayload = {
        sku: data.sku.trim().toUpperCase(),
        name: data.name.trim(),
        description: data.description?.trim() ? data.description.trim() : null,
        categoryExternalId: data.categoryExternalId,
      }

      editMutation.mutate(
        { externalId: productToEdit.externalId, input: editPayload },
        {
          onSuccess: () => {
            onOpenChange(false)
            form.reset()
          },
          onError: (error: any) => {
            if (error?.code === 'duplicate_sku') {
              form.setError('sku', {
                message: 'SKU is already registered by another product.',
              })
            } else if (error?.code === 'category_inactive') {
              form.setError('categoryExternalId', {
                message: 'Selected category is inactive.',
              })
            } else {
              setServerError(
                error?.message ||
                  'Failed to update product. Please verify the entered data.',
              )
            }
          },
        },
      )
    } else {
      const baseUnit = (data.baseUnit || 'UNIDAD').trim().toUpperCase()
      if (!/^[A-Z0-9_]{1,20}$/.test(baseUnit)) {
        form.setError('baseUnit', {
          message:
            'Base unit must be 1-20 uppercase letters, numbers or underscores (e.g. KG, UNIDAD, LITRO).',
        })
        return
      }

      const createPayload = {
        sku: data.sku.trim().toUpperCase(),
        name: data.name.trim(),
        description: data.description?.trim() ? data.description.trim() : null,
        categoryExternalId: data.categoryExternalId,
        baseUnit,
      }

      createMutation.mutate(createPayload, {
        onSuccess: () => {
          onOpenChange(false)
          form.reset()
        },
        onError: (error: any) => {
          if (error?.code === 'duplicate_sku') {
            form.setError('sku', {
              message: 'SKU is already in use (case-insensitive).',
            })
          } else if (error?.code === 'category_inactive') {
            form.setError('categoryExternalId', {
              message: 'Selected category is inactive.',
            })
          } else {
            setServerError(
              error?.message ||
                'Failed to create product. Please verify the entered data.',
            )
          }
        },
      })
    }
  }

  const isPending = createMutation.isPending || editMutation.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg bg-white border-slate-200">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <div className="h-8 w-8 rounded bg-indigo-100 text-indigo-800 flex items-center justify-center">
              <Boxes className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold text-slate-900">
                {isEdit ? 'Edit Product' : 'Register New Product'}
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-500">
                {isEdit
                  ? 'Update product catalog master details.'
                  : 'Define new SKU, name, category, and base measurement unit.'}
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {serverError && (
          <Alert variant="destructive" className="text-xs py-2 px-3">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{serverError}</AlertDescription>
          </Alert>
        )}

        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="product-sku" className="text-xs font-semibold">
                SKU <span className="text-red-500">*</span>
              </Label>
              <Input
                id="product-sku"
                placeholder="e.g. FERT-NPK-151515"
                className="text-xs font-mono uppercase"
                disabled={isPending}
                {...form.register('sku', {
                  onChange: (e) => {
                    e.target.value = e.target.value.toUpperCase()
                  },
                })}
              />
              {form.formState.errors.sku && (
                <p className="text-[11px] text-red-600 font-medium">
                  {form.formState.errors.sku.message}
                </p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label
                htmlFor="product-category"
                className="text-xs font-semibold"
              >
                Category <span className="text-red-500">*</span>
              </Label>
              <Select
                value={form.watch('categoryExternalId')}
                onValueChange={(val) =>
                  form.setValue('categoryExternalId', val, {
                    shouldValidate: true,
                  })
                }
                disabled={isPending || categoriesQuery.isLoading}
              >
                <SelectTrigger id="product-category" className="text-xs">
                  <SelectValue placeholder="Select category..." />
                </SelectTrigger>
                <SelectContent>
                  {categoriesQuery.data?.content?.map((cat) => (
                    <SelectItem
                      key={cat.externalId}
                      value={cat.externalId}
                      className="text-xs"
                    >
                      {cat.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {form.formState.errors.categoryExternalId && (
                <p className="text-[11px] text-red-600 font-medium">
                  {form.formState.errors.categoryExternalId.message}
                </p>
              )}
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="product-name" className="text-xs font-semibold">
              Product Name <span className="text-red-500">*</span>
            </Label>
            <Input
              id="product-name"
              placeholder="e.g. Fertilizante Triple 15 - 50kg"
              className="text-xs"
              disabled={isPending}
              {...form.register('name')}
            />
            {form.formState.errors.name && (
              <p className="text-[11px] text-red-600 font-medium">
                {form.formState.errors.name.message}
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label
              htmlFor="product-description"
              className="text-xs font-semibold"
            >
              Description{' '}
              <span className="text-slate-400 font-normal">(Optional)</span>
            </Label>
            <Input
              id="product-description"
              placeholder="Technical specifications, formulation, or notes"
              className="text-xs"
              disabled={isPending}
              {...form.register('description')}
            />
          </div>

          {/* Base Unit Field */}
          <div className="space-y-1.5 pt-1">
            <Label
              htmlFor="product-base-unit"
              className="text-xs font-semibold"
            >
              Base Unit of Measure <span className="text-red-500">*</span>
            </Label>
            {isEdit ? (
              <div className="flex items-center gap-2 p-2 rounded-md bg-slate-50 border border-slate-200">
                <Badge
                  variant="outline"
                  className="bg-white font-mono text-xs font-bold border-slate-300"
                >
                  {productToEdit?.baseUnit}
                </Badge>
                <span className="text-[11px] text-slate-500 flex items-center gap-1">
                  <Info className="h-3 w-3 text-slate-400" />
                  Base unit is fixed at creation and cannot be edited directly.
                </span>
              </div>
            ) : (
              <div>
                <Input
                  id="product-base-unit"
                  placeholder="e.g. UNIDAD, KG, LITRO, ROLLO"
                  className="text-xs font-mono uppercase"
                  disabled={isPending}
                  {...form.register('baseUnit', {
                    onChange: (e) => {
                      e.target.value = e.target.value.toUpperCase()
                    },
                  })}
                />
                <p className="text-[11px] text-slate-500 mt-1">
                  Stock balances and Kardex movements will be tracked in this
                  unit.
                </p>
                {form.formState.errors.baseUnit && (
                  <p className="text-[11px] text-red-600 font-medium">
                    {form.formState.errors.baseUnit.message}
                  </p>
                )}
              </div>
            )}
          </div>

          <DialogFooter className="pt-2 gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="text-xs cursor-pointer"
              onClick={() => onOpenChange(false)}
              disabled={isPending}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              size="sm"
              className="text-xs bg-indigo-700 hover:bg-indigo-800 text-white cursor-pointer"
              disabled={isPending}
            >
              {isPending && (
                <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
              )}
              {isEdit ? 'Save Changes' : 'Register Product'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
