import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { LoginForm } from '@/features/iam/components/LoginForm.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { Languages } from 'lucide-react'

export const Route = createFileRoute('/login')({
  component: function LoginPage() {
    const navigate = useNavigate()
    const { language, setLanguage } = useTranslation()

    return (
      <div className="min-h-screen flex flex-col justify-center items-center p-4 bg-slate-950 text-slate-100 relative">
        {/* Language selector in top-right */}
        <div className="absolute top-4 right-4 z-20">
          <button
            type="button"
            onClick={() => setLanguage(language === 'es' ? 'en' : 'es')}
            className="flex items-center gap-1.5 px-2.5 py-1 text-xs rounded border border-slate-700 bg-slate-900/80 text-slate-300 hover:text-white hover:border-slate-500 transition-colors cursor-pointer backdrop-blur-xs"
            title="Cambiar idioma / Change language"
          >
            <Languages className="h-3.5 w-3.5 text-orange-500" />
            <span className="font-semibold">{language.toUpperCase()}</span>
          </button>
        </div>

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
