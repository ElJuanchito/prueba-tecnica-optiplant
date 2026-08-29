import * as React from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Alert, AlertDescription } from '@/components/ui/alert.tsx'
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
import { useCreateCategory, useEditCategory } from '../hooks/use-categories.ts'
import type { CategoryResponse } from '../types/category.types.ts'
import { AlertCircle, FolderTree, Loader2 } from 'lucide-react'

const categoryFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Category name is required')
    .max(100, 'Category name must be at most 100 characters'),
  description: z.string().trim().optional(),
})

type CategoryFormValues = z.infer<typeof categoryFormSchema>

interface CategoryFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  categoryToEdit?: CategoryResponse | null | undefined
}

export function CategoryFormDialog({
  open,
  onOpenChange,
  categoryToEdit,
}: CategoryFormDialogProps) {
  const isEdit = Boolean(categoryToEdit)
  const createMutation = useCreateCategory()
  const editMutation = useEditCategory()
  const [serverError, setServerError] = React.useState<string | null>(null)

  const form = useForm<CategoryFormValues>({
    resolver: zodResolver(categoryFormSchema),
    defaultValues: {
      name: '',
      description: '',
    },
  })

  React.useEffect(() => {
    if (open) {
      setServerError(null)
      if (categoryToEdit) {
        form.reset({
          name: categoryToEdit.name,
          description: categoryToEdit.description ?? '',
        })
      } else {
        form.reset({
          name: '',
          description: '',
        })
      }
    }
  }, [open, categoryToEdit, form])

  const onSubmit = (data: CategoryFormValues) => {
    setServerError(null)
    const payload = {
      name: data.name.trim(),
      description: data.description?.trim() ? data.description.trim() : null,
    }

    if (isEdit && categoryToEdit) {
      editMutation.mutate(
        { externalId: categoryToEdit.externalId, input: payload },
        {
          onSuccess: () => {
            onOpenChange(false)
            form.reset()
          },
          onError: (error: any) => {
            if (error?.code === 'duplicate_category_name') {
              form.setError('name', {
                message:
                  'A category with this name already exists (case-insensitive).',
              })
            } else {
              setServerError(
                error?.message ||
                  'Failed to update category. Please verify your inputs.',
              )
            }
          },
        },
      )
    } else {
      createMutation.mutate(payload, {
        onSuccess: () => {
          onOpenChange(false)
          form.reset()
        },
        onError: (error: any) => {
          if (error?.code === 'duplicate_category_name') {
            form.setError('name', {
              message:
                'A category with this name already exists (case-insensitive).',
            })
          } else {
            setServerError(
              error?.message ||
                'Failed to create category. Please verify your inputs.',
            )
          }
        },
      })
    }
  }

  const isPending = createMutation.isPending || editMutation.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md bg-white border-slate-200">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <div className="h-8 w-8 rounded bg-emerald-100 text-emerald-800 flex items-center justify-center">
              <FolderTree className="h-4 w-4" />
            </div>
            <div>
              <DialogTitle className="text-base font-bold text-slate-900">
                {isEdit ? 'Edit Category' : 'New Product Category'}
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-500">
                {isEdit
                  ? 'Update category master data. Products linked to this category will reflect the changes.'
                  : 'Add a new product grouping category to corporate master data.'}
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
          <div className="space-y-1.5">
            <Label htmlFor="category-name" className="text-xs font-semibold">
              Category Name <span className="text-red-500">*</span>
            </Label>
            <Input
              id="category-name"
              placeholder="e.g. Fertilizantes, Herbicidas, Semillas"
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
              htmlFor="category-description"
              className="text-xs font-semibold"
            >
              Description{' '}
              <span className="text-slate-400 font-normal">(Optional)</span>
            </Label>
            <Input
              id="category-description"
              placeholder="Brief description of product category scope"
              className="text-xs"
              disabled={isPending}
              {...form.register('description')}
            />
            {form.formState.errors.description && (
              <p className="text-[11px] text-red-600 font-medium">
                {form.formState.errors.description.message}
              </p>
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
              className="text-xs bg-emerald-700 hover:bg-emerald-800 text-white cursor-pointer"
              disabled={isPending}
            >
              {isPending && (
                <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
              )}
              {isEdit ? 'Save Changes' : 'Create Category'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
