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
import { useCreateBranch, useEditBranch } from '../hooks/use-branches.ts'
import {
  createBranchSchema,
  editBranchSchema,
} from '../schemas/branch.schema.ts'
import type {
  BranchResponse,
  CreateBranchInput,
  EditBranchInput,
} from '../types/branch.types.ts'
import {
  AlertCircle,
  Building2,
  Hash,
  Loader2,
  MapPin,
  Navigation,
  Phone,
} from 'lucide-react'

const branchFormValuesSchema = z.object({
  code: z.string().optional(),
  name: z.string().trim().min(1, 'Branch name is required'),
  address: z.string().trim().min(1, 'Address is required'),
  city: z.string().trim().min(1, 'City is required'),
  phone: z.string().optional(),
})

type BranchFormValues = z.infer<typeof branchFormValuesSchema>

interface BranchFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  branchToEdit?: BranchResponse | null | undefined
}

export function BranchFormDialog({
  open,
  onOpenChange,
  branchToEdit,
}: BranchFormDialogProps) {
  const isEdit = Boolean(branchToEdit)
  const createMutation = useCreateBranch()
  const editMutation = useEditBranch()
  const [serverError, setServerError] = React.useState<string | null>(null)

  const form = useForm<BranchFormValues>({
    resolver: zodResolver(branchFormValuesSchema),
    defaultValues: {
      code: '',
      name: '',
      address: '',
      city: '',
      phone: '',
    },
  })

  React.useEffect(() => {
    if (open) {
      if (branchToEdit) {
        form.reset({
          code: branchToEdit.code,
          name: branchToEdit.name,
          address: branchToEdit.address,
          city: branchToEdit.city,
          phone: branchToEdit.phone ?? '',
        })
      } else {
        form.reset({
          code: '',
          name: '',
          address: '',
          city: '',
          phone: '',
        })
      }
    }
  }, [open, branchToEdit, form])

  const onSubmit = (data: BranchFormValues) => {
    setServerError(null)
    const phonePayload = data.phone?.trim() ? data.phone.trim() : null

    if (isEdit && branchToEdit) {
      try {
        const editInput: EditBranchInput = editBranchSchema.parse({
          name: data.name,
          address: data.address,
          city: data.city,
          phone: phonePayload,
        })
        editMutation.mutate(
          { externalId: branchToEdit.externalId, input: editInput },
          {
            onSuccess: () => {
              onOpenChange(false)
            },
            onError: (err) => {
              setServerError(err.message)
            },
          },
        )
      } catch (err) {
        if (err instanceof z.ZodError) {
          setServerError(err.issues[0]?.message ?? 'Invalid form data')
        }
      }
    } else {
      try {
        const createInput: CreateBranchInput = createBranchSchema.parse({
          code: data.code ?? '',
          name: data.name,
          address: data.address,
          city: data.city,
          phone: phonePayload,
        })
        createMutation.mutate(createInput, {
          onSuccess: () => {
            onOpenChange(false)
          },
          onError: (err) => {
            setServerError(err.message)
          },
        })
      } catch (err) {
        if (err instanceof z.ZodError) {
          setServerError(err.issues[0]?.message ?? 'Invalid form data')
        }
      }
    }
  }

  const isPending = createMutation.isPending || editMutation.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px] p-6">
        <DialogHeader className="space-y-1">
          <div className="flex items-center gap-2">
            <div className="h-8 w-8 rounded bg-orange-600 text-white flex items-center justify-center font-bold text-sm">
              <Building2 className="h-4 w-4" />
            </div>
            <DialogTitle className="text-xl font-bold text-slate-900">
              {isEdit ? 'Edit Branch' : 'Create New Branch'}
            </DialogTitle>
          </div>
          <DialogDescription className="text-xs text-slate-500">
            {isEdit
              ? 'Update branch name and physical location details.'
              : 'Add a new warehouse or retail distribution branch location.'}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-2">
          {serverError && (
            <Alert
              variant="destructive"
              className="animate-in fade-in-50 duration-200"
            >
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{serverError}</AlertDescription>
            </Alert>
          )}

          {/* Identification Section */}
          <div className="space-y-3 bg-slate-50 p-3.5 rounded-lg border border-slate-200">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-700 flex items-center gap-1.5">
              <Hash className="h-3.5 w-3.5 text-orange-600" />
              Branch Identification
            </h4>

            {!isEdit && (
              <div className="space-y-1">
                <Label
                  htmlFor="branch-code"
                  className="text-xs font-semibold text-slate-700"
                >
                  Branch Code
                </Label>
                <Input
                  id="branch-code"
                  placeholder="e.g. BOG-01, MED-01"
                  disabled={isPending}
                  className="bg-white h-9 text-sm font-mono uppercase"
                  {...form.register('code')}
                />
                {form.formState.errors.code && (
                  <p className="text-xs text-red-600 font-medium">
                    {form.formState.errors.code.message}
                  </p>
                )}
              </div>
            )}

            <div className="space-y-1">
              <Label
                htmlFor="branch-name"
                className="text-xs font-semibold text-slate-700"
              >
                Branch Name
              </Label>
              <Input
                id="branch-name"
                placeholder="e.g. Sede Principal Bogotá"
                disabled={isPending}
                className="bg-white h-9 text-sm"
                {...form.register('name')}
              />
              {form.formState.errors.name && (
                <p className="text-xs text-red-600 font-medium">
                  {form.formState.errors.name.message}
                </p>
              )}
            </div>
          </div>

          {/* Location & Contact Section */}
          <div className="space-y-3 bg-slate-50 p-3.5 rounded-lg border border-slate-200">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-700 flex items-center gap-1.5">
              <MapPin className="h-3.5 w-3.5 text-orange-600" />
              Physical Location & Contact
            </h4>

            <div className="space-y-1">
              <Label
                htmlFor="branch-address"
                className="text-xs font-semibold text-slate-700"
              >
                Address
              </Label>
              <Input
                id="branch-address"
                placeholder="e.g. Calle 100 # 15-20"
                disabled={isPending}
                className="bg-white h-9 text-sm"
                {...form.register('address')}
              />
              {form.formState.errors.address && (
                <p className="text-xs text-red-600 font-medium">
                  {form.formState.errors.address.message}
                </p>
              )}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label
                  htmlFor="branch-city"
                  className="text-xs font-semibold text-slate-700"
                >
                  City
                </Label>
                <div className="relative">
                  <Input
                    id="branch-city"
                    placeholder="e.g. Bogotá"
                    disabled={isPending}
                    className="bg-white h-9 text-sm pr-8"
                    {...form.register('city')}
                  />
                  <Navigation className="absolute right-2.5 top-2.5 h-4 w-4 text-slate-400 pointer-events-none" />
                </div>
                {form.formState.errors.city && (
                  <p className="text-xs text-red-600 font-medium">
                    {form.formState.errors.city.message}
                  </p>
                )}
              </div>

              <div className="space-y-1">
                <Label
                  htmlFor="branch-phone"
                  className="text-xs font-semibold text-slate-700"
                >
                  Phone (Optional)
                </Label>
                <div className="relative">
                  <Input
                    id="branch-phone"
                    placeholder="e.g. +57 601 1234567"
                    disabled={isPending}
                    className="bg-white h-9 text-sm pr-8 font-mono"
                    {...form.register('phone')}
                  />
                  <Phone className="absolute right-2.5 top-2.5 h-4 w-4 text-slate-400 pointer-events-none" />
                </div>
              </div>
            </div>
          </div>

          <DialogFooter className="pt-2 gap-2 sm:gap-0">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isPending}
              className="cursor-pointer"
            >
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={isPending}
              className="bg-orange-600 hover:bg-orange-700 text-white cursor-pointer"
            >
              {isPending ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin mr-1.5" />
                  Saving...
                </>
              ) : isEdit ? (
                'Save Changes'
              ) : (
                'Create Branch'
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
