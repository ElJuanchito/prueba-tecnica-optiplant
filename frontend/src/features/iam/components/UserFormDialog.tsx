import * as React from 'react'
import { Controller, useForm } from 'react-hook-form'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select.tsx'
import { useBranches } from '../hooks/use-branches.ts'
import { useCreateUser, useEditUser } from '../hooks/use-users.ts'
import { roleSchema, uuidSchema } from '../schemas/common.schema.ts'
import {
  createUserSchema,
  editUserSchema,
} from '../schemas/user.schema.ts'
import type {
  CreateUserInput,
  EditUserInput,
  UserResponse,
} from '../types/user.types.ts'
import type { Role } from '../types/auth.types.ts'
import {
  AlertCircle,
  Info,
  KeyRound,
  Loader2,
  Mail,
  Shield,
  User,
} from 'lucide-react'

const userFormValuesSchema = z.object({
  username: z.string().optional(),
  email: z.string().trim().email('Invalid email address'),
  fullName: z.string().trim().min(1, 'Full name is required'),
  password: z.string().optional(),
  role: roleSchema,
  branchId: uuidSchema.nullable().optional(),
})

type UserFormValues = z.infer<typeof userFormValuesSchema>

interface UserFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  userToEdit?: UserResponse | null | undefined
  currentActorRole?: Role | undefined
  currentActorBranchId?: string | null | undefined
}

export function UserFormDialog({
  open,
  onOpenChange,
  userToEdit,
  currentActorRole = 'ADMIN',
  currentActorBranchId = null,
}: UserFormDialogProps) {
  const isEdit = Boolean(userToEdit)
  const isBranchManager = currentActorRole === 'BRANCH_MANAGER'
  const createMutation = useCreateUser()
  const editMutation = useEditUser()
  const [serverError, setServerError] = React.useState<string | null>(null)

  const branchesQuery = useBranches({ active: true })
  const branches = branchesQuery.data?.content ?? []

  const form = useForm<UserFormValues>({
    resolver: zodResolver(userFormValuesSchema),
    defaultValues: {
      username: '',
      email: '',
      fullName: '',
      password: '',
      role: 'OPERATOR',
      branchId: isBranchManager ? currentActorBranchId : null,
    },
  })

  React.useEffect(() => {
    if (open) {
      if (userToEdit) {
        form.reset({
          username: userToEdit.username,
          email: userToEdit.email,
          fullName: userToEdit.fullName,
          password: '',
          role: userToEdit.role,
          branchId: userToEdit.branchId,
        })
      } else {
        form.reset({
          username: '',
          email: '',
          fullName: '',
          password: '',
          role: 'OPERATOR',
          branchId: isBranchManager ? currentActorBranchId : null,
        })
      }
    }
  }, [open, userToEdit, form, isBranchManager, currentActorBranchId])

  const selectedRole = form.watch('role')

  const onSubmit = (data: UserFormValues) => {
    setServerError(null)
    const branchIdPayload =
      data.role === 'ADMIN' ? null : data.branchId ? data.branchId : null

    if (data.role !== 'ADMIN' && !branchIdPayload) {
      setServerError('A branch must be selected for non-ADMIN users.')
      return
    }

    if (isEdit && userToEdit) {
      try {
        const editPayload: EditUserInput = editUserSchema.parse({
          email: data.email,
          fullName: data.fullName,
          role: data.role,
          branchId: branchIdPayload,
        })
        editMutation.mutate(
          { externalId: userToEdit.externalId, input: editPayload },
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
        const createPayload: CreateUserInput = createUserSchema.parse({
          username: data.username ?? '',
          email: data.email,
          fullName: data.fullName,
          password: data.password ?? '',
          role: data.role,
          branchId: branchIdPayload,
        })
        createMutation.mutate(createPayload, {
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

  if (currentActorRole === 'OPERATOR') {
    return null
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[520px] p-6">
        <DialogHeader className="space-y-1">
          <div className="flex items-center gap-2">
            <div className="h-8 w-8 rounded bg-orange-600 text-white flex items-center justify-center font-bold text-sm">
              <User className="h-4 w-4" />
            </div>
            <DialogTitle className="text-xl font-bold text-slate-900">
              {isEdit ? 'Edit User' : 'Create New User'}
            </DialogTitle>
          </div>
          <DialogDescription className="text-xs text-slate-500">
            {isEdit
              ? 'Update user account credentials, enterprise role, and branch assignment.'
              : 'Add a new user to the multi-branch inventory management system.'}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-2">
          {serverError && (
            <Alert variant="destructive" className="animate-in fade-in-50 duration-200">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{serverError}</AlertDescription>
            </Alert>
          )}

          {/* Account Profile Section */}
          <div className="space-y-3 bg-slate-50 p-3.5 rounded-lg border border-slate-200">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-700 flex items-center gap-1.5">
              <User className="h-3.5 w-3.5 text-orange-600" />
              Profile Details
            </h4>

            {!isEdit && (
              <div className="space-y-1">
                <Label htmlFor="create-username" className="text-xs font-semibold text-slate-700">
                  Username
                </Label>
                <Input
                  id="create-username"
                  placeholder="e.g. jdoe"
                  disabled={isPending}
                  className="bg-white h-9 text-sm"
                  {...form.register('username')}
                />
                {form.formState.errors.username && (
                  <p className="text-xs text-red-600 font-medium">
                    {form.formState.errors.username.message}
                  </p>
                )}
              </div>
            )}

            <div className="space-y-1">
              <Label htmlFor="user-fullname" className="text-xs font-semibold text-slate-700">
                Full Name
              </Label>
              <Input
                id="user-fullname"
                placeholder="e.g. John Doe"
                disabled={isPending}
                className="bg-white h-9 text-sm"
                {...form.register('fullName')}
              />
              {form.formState.errors.fullName && (
                <p className="text-xs text-red-600 font-medium">
                  {form.formState.errors.fullName.message}
                </p>
              )}
            </div>

            <div className="space-y-1">
              <Label htmlFor="user-email" className="text-xs font-semibold text-slate-700">
                Email
              </Label>
              <div className="relative">
                <Input
                  id="user-email"
                  type="email"
                  placeholder="e.g. jdoe@optiplant.com"
                  disabled={isPending}
                  className="bg-white h-9 text-sm pr-8"
                  {...form.register('email')}
                />
                <Mail className="absolute right-2.5 top-2.5 h-4 w-4 text-slate-400 pointer-events-none" />
              </div>
              {form.formState.errors.email && (
                <p className="text-xs text-red-600 font-medium">
                  {form.formState.errors.email.message}
                </p>
              )}
            </div>

            {!isEdit && (
              <div className="space-y-1">
                <Label htmlFor="create-password" className="text-xs font-semibold text-slate-700">
                  Password
                </Label>
                <div className="relative">
                  <Input
                    id="create-password"
                    type="password"
                    placeholder="Minimum 8 characters"
                    disabled={isPending}
                    className="bg-white h-9 text-sm pr-8 font-mono placeholder:font-sans"
                    {...form.register('password')}
                  />
                  <KeyRound className="absolute right-2.5 top-2.5 h-4 w-4 text-slate-400 pointer-events-none" />
                </div>
                {form.formState.errors.password && (
                  <p className="text-xs text-red-600 font-medium">
                    {form.formState.errors.password.message}
                  </p>
                )}
              </div>
            )}
          </div>

          {/* Access & Role Section */}
          <div className="space-y-3 bg-slate-50 p-3.5 rounded-lg border border-slate-200">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-700 flex items-center gap-1.5">
              <Shield className="h-3.5 w-3.5 text-orange-600" />
              Permissions & Location
            </h4>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label htmlFor="user-role" className="text-xs font-semibold text-slate-700">
                  Role
                </Label>
                <Controller
                  control={form.control}
                  name="role"
                  render={({ field }) => (
                    <Select
                      value={field.value}
                      onValueChange={(val) => field.onChange(val as Role)}
                      disabled={isPending || isBranchManager}
                    >
                      <SelectTrigger id="user-role" className="bg-white h-9 text-xs">
                        <SelectValue placeholder="Select role" />
                      </SelectTrigger>
                      <SelectContent>
                        {!isBranchManager && (
                          <>
                            <SelectItem value="ADMIN">ADMIN (Global)</SelectItem>
                            <SelectItem value="BRANCH_MANAGER">
                              BRANCH_MANAGER
                            </SelectItem>
                          </>
                        )}
                        <SelectItem value="OPERATOR">OPERATOR</SelectItem>
                      </SelectContent>
                    </Select>
                  )}
                />
                {form.formState.errors.role && (
                  <p className="text-xs text-red-600 font-medium">
                    {form.formState.errors.role.message}
                  </p>
                )}
              </div>

              <div className="space-y-1">
                <Label htmlFor="user-branch" className="text-xs font-semibold text-slate-700">
                  Branch
                </Label>
                <Controller
                  control={form.control}
                  name="branchId"
                  render={({ field }) => (
                    <Select
                      value={field.value ?? 'none'}
                      onValueChange={(val) =>
                        field.onChange(val === 'none' ? null : val)
                      }
                      disabled={
                        isPending ||
                        isBranchManager ||
                        selectedRole === 'ADMIN'
                      }
                    >
                      <SelectTrigger id="user-branch" className="bg-white h-9 text-xs">
                        <SelectValue
                          placeholder={
                            selectedRole === 'ADMIN'
                              ? 'None (Corporate)'
                              : 'Select branch'
                          }
                        />
                      </SelectTrigger>
                      <SelectContent>
                        {selectedRole === 'ADMIN' && (
                          <SelectItem value="none">None (Corporate)</SelectItem>
                        )}
                        {branches.map((b) => (
                          <SelectItem key={b.externalId} value={b.externalId}>
                            {b.name} ({b.code})
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
            </div>

            <div className="flex items-center gap-1.5 text-[11px] text-slate-500 pt-1">
              <Info className="h-3.5 w-3.5 text-slate-400 shrink-0" />
              <span>
                {selectedRole === 'ADMIN'
                  ? 'Administrators have unrestricted multi-branch management authority.'
                  : 'Branch managers and operators are scoped to their assigned branch.'}
              </span>
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
            <Button type="submit" disabled={isPending} className="bg-orange-600 hover:bg-orange-700 text-white cursor-pointer">
              {isPending ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin mr-1.5" />
                  Saving...
                </>
              ) : isEdit ? (
                'Save Changes'
              ) : (
                'Create User'
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

