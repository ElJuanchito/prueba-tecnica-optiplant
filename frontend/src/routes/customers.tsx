import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { CustomersDashboard } from '@/features/customers/components/CustomersDashboard.tsx'
import { LoginForm } from '@/features/iam/components/LoginForm.tsx'
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { Skeleton } from '@/components/ui/skeleton.tsx'

export const Route = createFileRoute('/customers')({
  component: function CustomersPage() {
    const sessionQuery = useSession()
    const navigate = useNavigate()

    if (sessionQuery.isLoading) {
      return (
        <div className="min-h-screen flex items-center justify-center p-4 bg-slate-50">
          <div className="w-full max-w-md space-y-4 p-8 bg-white rounded-2xl border border-slate-200 shadow-sm">
            <Skeleton className="h-10 w-32 mx-auto rounded-lg" />
            <Skeleton className="h-12 w-full rounded-lg" />
            <Skeleton className="h-12 w-full rounded-lg" />
            <Skeleton className="h-10 w-full rounded-lg" />
          </div>
        </div>
      )
    }

    if (sessionQuery.data?.accessToken) {
      return <CustomersDashboard />
    }

    return (
      <div className="min-h-screen flex flex-col justify-center items-center p-4 bg-slate-950 text-slate-100 relative">
        <div className="absolute inset-0 bg-[radial-gradient(#0284c715_1px,transparent_1px)] [background-size:24px_24px] pointer-events-none opacity-40" />
        <div className="w-full max-w-md relative z-10">
          <LoginForm
            onSuccess={() => {
              navigate({ to: '/customers' })
            }}
          />
        </div>
      </div>
    )
  },
})
