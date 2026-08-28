import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { LoginForm } from '@/features/iam/components/LoginForm.tsx'

export const Route = createFileRoute('/login')({
  component: function LoginPage() {
    const navigate = useNavigate()

    return (
      <div className="min-h-screen flex flex-col justify-center items-center p-4 bg-slate-950 text-slate-100 relative">
        {/* Subtle industrial grid / constellation overlay */}
        <div className="absolute inset-0 bg-[radial-gradient(#ea580c15_1px,transparent_1px)] [background-size:24px_24px] pointer-events-none opacity-40" />
        <div className="w-full max-w-md relative z-10">
          <LoginForm
            onSuccess={() => {
              navigate({ to: '/' })
            }}
          />
        </div>
      </div>
    )
  },
})

