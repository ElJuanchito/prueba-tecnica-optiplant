import * as React from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert.tsx'
import { Button } from '@/components/ui/button.tsx'
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card.tsx'
import { Input } from '@/components/ui/input.tsx'
import { Label } from '@/components/ui/label.tsx'
import { useLogin } from '../hooks/use-auth.ts'
import { loginRequestSchema } from '../schemas/auth.schema.ts'
import type { LoginRequest } from '../types/auth.types.ts'
import {
  AlertCircle,
  Eye,
  EyeOff,
  Loader2,
  Lock,
  User,
} from 'lucide-react'

interface LoginFormProps {
  onSuccess?: () => void
}

export function LoginForm({ onSuccess }: LoginFormProps) {
  const loginMutation = useLogin()
  const [errorMessage, setErrorMessage] = React.useState<string | null>(null)
  const [showPassword, setShowPassword] = React.useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginRequest>({
    resolver: zodResolver(loginRequestSchema),
    defaultValues: {
      username: '',
      password: '',
    },
  })

  const onSubmit = (data: LoginRequest) => {
    setErrorMessage(null)
    loginMutation.mutate(data, {
      onSuccess: () => {
        onSuccess?.()
      },
      onError: (err) => {
        setErrorMessage(
          err.message || 'Login failed. Please check credentials.',
        )
      },
    })
  }

  return (
    <Card className="w-full max-w-md mx-auto border-slate-200 bg-white shadow-xl rounded-lg overflow-hidden">
      <div className="h-1.5 w-full bg-orange-600" />
      <CardHeader className="space-y-3 text-center pt-8 pb-6">
        {/* OptiPlant Minimalist Brand Mark */}
        <div className="mx-auto flex items-center justify-center gap-2.5">
          <div className="h-9 w-9 rounded-md bg-orange-600 text-white flex items-center justify-center font-black text-lg tracking-tighter shadow-xs">
            OP
          </div>
          <div className="text-left leading-none">
            <CardTitle className="text-2xl font-black tracking-tight text-slate-900">
              OptiPlant
            </CardTitle>
            <span className="text-[10px] font-bold tracking-widest text-orange-600 uppercase">
              CONSULTORES
            </span>
          </div>
        </div>

        <div className="pt-2">
          <span className="inline-block text-[11px] font-semibold tracking-wider text-orange-700 bg-orange-50 border border-orange-200 px-2 py-0.5 rounded uppercase">
            Industry 4.0 Integrator
          </span>
          <CardDescription className="text-xs text-slate-500 mt-2">
            Multi-branch Inventory Management System
          </CardDescription>
        </div>
      </CardHeader>

      <form onSubmit={handleSubmit(onSubmit)}>
        <CardContent className="space-y-4 pt-0">
          {errorMessage && (
            <Alert variant="destructive" data-testid="login-error-alert" className="animate-in fade-in-50 duration-200 py-2.5">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle className="text-xs font-semibold">Authentication Failed</AlertTitle>
              <AlertDescription className="text-xs">{errorMessage}</AlertDescription>
            </Alert>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="username" className="text-xs font-medium text-slate-700">
              Username
            </Label>
            <div className="relative">
              <Input
                id="username"
                type="text"
                placeholder="admin"
                autoComplete="username"
                disabled={loginMutation.isPending}
                className="pl-9 pr-3 h-9 border-slate-300 focus-visible:ring-orange-500 focus-visible:border-orange-500 text-sm"
                {...register('username')}
              />
              <User className="absolute left-3 top-2.5 h-4 w-4 text-slate-400 pointer-events-none" />
            </div>
            {errors.username && (
              <p className="text-xs text-red-600 font-medium" role="alert">
                {errors.username.message}
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="password" className="text-xs font-medium text-slate-700">
                Password
              </Label>
            </div>
            <div className="relative">
              <Input
                id="password"
                type={showPassword ? 'text' : 'password'}
                placeholder="••••••••"
                autoComplete="current-password"
                disabled={loginMutation.isPending}
                className="pl-9 pr-10 h-9 border-slate-300 focus-visible:ring-orange-500 focus-visible:border-orange-500 text-sm font-mono placeholder:font-sans"
                {...register('password')}
              />
              <Lock className="absolute left-3 top-2.5 h-4 w-4 text-slate-400 pointer-events-none" />
              <button
                type="button"
                aria-label={showPassword ? 'Hide secret input' : 'Reveal secret input'}
                onClick={() => setShowPassword(!showPassword)}
                tabIndex={-1}
                className="absolute right-2.5 top-2 p-1 text-slate-400 hover:text-slate-600 rounded focus:outline-none focus:ring-1 focus:ring-orange-500 transition-colors cursor-pointer"
              >
                {showPassword ? (
                  <EyeOff className="h-4 w-4" />
                ) : (
                  <Eye className="h-4 w-4" />
                )}
              </button>
            </div>
            {errors.password && (
              <p className="text-xs text-red-600 font-medium" role="alert">
                {errors.password.message}
              </p>
            )}
          </div>
        </CardContent>

        <CardFooter className="pt-2 pb-6 flex flex-col space-y-3">
          <Button
            type="submit"
            className="w-full h-10 font-semibold bg-orange-600 hover:bg-orange-700 text-white shadow-xs cursor-pointer"
            disabled={loginMutation.isPending}
          >
            {loginMutation.isPending ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin mr-2" />
                Iniciando sesión...
              </>
            ) : (
              'Sign In'
            )}
          </Button>

          <div className="text-center">
            <span className="text-[11px] text-slate-400">
              Plataforma Industrial OptiPlant &bull; IAM v1.0
            </span>
          </div>
        </CardFooter>
      </form>
    </Card>
  )
}


