import { createFileRoute } from '@tanstack/react-router'
import { IamDashboard } from '@/features/iam/components/IamDashboard.tsx'
import { LoginForm } from '@/features/iam/components/LoginForm.tsx'
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { Skeleton } from '@/components/ui/skeleton.tsx'

export const Route = createFileRoute('/')({
  component: function IndexPage() {
    const sessionQuery = useSession()

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
      return <IamDashboard />
    }

    return (
      <div className="min-h-screen flex flex-col justify-center items-center p-4 bg-slate-950 text-slate-100 relative">
        <div className="absolute inset-0 bg-[radial-gradient(#ea580c15_1px,transparent_1px)] [background-size:24px_24px] pointer-events-none opacity-40" />
        <div className="w-full max-w-md relative z-10">
          <LoginForm />
        </div>
      </div>
    )
  },
})

