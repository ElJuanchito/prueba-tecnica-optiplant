import * as React from 'react'
import { translations, type Language } from './translations.ts'
import { Globe } from 'lucide-react'

interface I18nContextType {
  language: Language
  setLanguage: (lang: Language) => void
  t: (keyPath: string, params?: Record<string, string | number>) => string
}

const I18nContext = React.createContext<I18nContextType | null>(null)

const STORAGE_KEY = 'optiplant_lang'

export function I18nProvider({
  children,
  initialLanguage,
}: {
  children: React.ReactNode
  initialLanguage?: Language
}) {
  const [language, setLanguageState] = React.useState<Language>(() => {
    if (initialLanguage) {
      return initialLanguage
    }
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem(STORAGE_KEY) as Language | null
      if (saved === 'es' || saved === 'en') {
        return saved
      }
    }
    return 'es'
  })

  React.useEffect(() => {
    if (initialLanguage) {
      setLanguageState(initialLanguage)
    }
  }, [initialLanguage])

  const setLanguage = React.useCallback((lang: Language) => {
    setLanguageState(lang)
    if (typeof window !== 'undefined') {
      localStorage.setItem(STORAGE_KEY, lang)
      document.documentElement.lang = lang
    }
  }, [])

  React.useEffect(() => {
    if (typeof window !== 'undefined') {
      document.documentElement.lang = language
    }
  }, [language])

  const t = React.useCallback(
    (keyPath: string, params?: Record<string, string | number>): string => {
      const keys = keyPath.split('.')
      const dict = translations[language] as Record<string, unknown>
      let current: unknown = dict

      for (const k of keys) {
        if (current && typeof current === 'object' && k in current) {
          current = (current as Record<string, unknown>)[k]
        } else {
          // Fallback to Spanish or keyPath
          const fallbackDict = translations.es as Record<string, unknown>
          let fallbackVal: unknown = fallbackDict
          for (const fk of keys) {
            if (
              fallbackVal &&
              typeof fallbackVal === 'object' &&
              fk in fallbackVal
            ) {
              fallbackVal = (fallbackVal as Record<string, unknown>)[fk]
            } else {
              fallbackVal = null
              break
            }
          }
          current = fallbackVal ?? keyPath
          break
        }
      }

      if (typeof current !== 'string') {
        return keyPath
      }

      let result = current
      if (params) {
        Object.entries(params).forEach(([paramKey, val]) => {
          result = result.replace(
            new RegExp(`\\{${paramKey}\\}`, 'g'),
            String(val),
          )
        })
      }
      return result
    },
    [language],
  )

  const value = React.useMemo(
    () => ({
      language,
      setLanguage,
      t,
    }),
    [language, setLanguage, t],
  )

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export function useTranslation() {
  const context = React.useContext(I18nContext)
  if (!context) {
    // Fallback if rendered without provider
    const fallbackT = (
      keyPath: string,
      params?: Record<string, string | number>,
    ) => {
      const keys = keyPath.split('.')
      let current: unknown = translations.es
      for (const k of keys) {
        if (current && typeof current === 'object' && k in current) {
          current = (current as Record<string, unknown>)[k]
        } else {
          return keyPath
        }
      }
      let res = typeof current === 'string' ? current : keyPath
      if (params) {
        Object.entries(params).forEach(([k, v]) => {
          res = res.replace(new RegExp(`\\{${k}\\}`, 'g'), String(v))
        })
      }
      return res
    }
    return {
      language: 'es' as Language,
      setLanguage: () => {},
      t: fallbackT,
    }
  }
  return context
}

export function LanguageSwitcher({
  variant = 'compact',
  className = '',
}: {
  variant?: 'compact' | 'full'
  className?: string
}) {
  const { language, setLanguage } = useTranslation()

  if (variant === 'full') {
    return (
      <div
        className={`flex items-center gap-1.5 p-1 bg-slate-100 rounded-lg border border-slate-200 ${className}`}
      >
        <Globe className="h-3.5 w-3.5 text-slate-500 ml-1.5 shrink-0" />
        <button
          type="button"
          onClick={() => setLanguage('es')}
          className={`flex-1 px-2.5 py-1 text-xs font-semibold rounded-md transition-all ${
            language === 'es'
              ? 'bg-white text-slate-900 shadow-2xs border border-slate-200/80 font-bold'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          Español
        </button>
        <button
          type="button"
          onClick={() => setLanguage('en')}
          className={`flex-1 px-2.5 py-1 text-xs font-semibold rounded-md transition-all ${
            language === 'en'
              ? 'bg-white text-slate-900 shadow-2xs border border-slate-200/80 font-bold'
              : 'text-slate-600 hover:text-slate-900'
          }`}
        >
          English
        </button>
      </div>
    )
  }

  return (
    <div
      className={`inline-flex items-center rounded-lg border border-slate-200 bg-slate-100/90 p-0.5 text-xs font-semibold ${className}`}
      role="group"
      aria-label="Language Selector"
    >
      <button
        type="button"
        onClick={() => setLanguage('es')}
        aria-pressed={language === 'es'}
        className={`rounded-md px-2 py-0.5 text-[11px] font-bold transition-colors ${
          language === 'es'
            ? 'bg-white text-slate-900 shadow-2xs border border-slate-200/80'
            : 'text-slate-500 hover:text-slate-900'
        }`}
      >
        ES
      </button>
      <button
        type="button"
        onClick={() => setLanguage('en')}
        aria-pressed={language === 'en'}
        className={`rounded-md px-2 py-0.5 text-[11px] font-bold transition-colors ${
          language === 'en'
            ? 'bg-white text-slate-900 shadow-2xs border border-slate-200/80'
            : 'text-slate-500 hover:text-slate-900'
        }`}
      >
        EN
      </button>
    </div>
  )
}
