import * as React from 'react'
import { Check, ChevronDown, Search, X } from 'lucide-react'
import { cn } from '@/lib/utils.ts'
import { Input } from '@/components/ui/input.tsx'
import type { CustomerResponse } from '../types/index.ts'

interface CustomerSearchSelectProps {
  value?: string | undefined
  onChange?: ((value: string | undefined) => void) | undefined
  onSelectCustomer?: ((customer: CustomerResponse | null) => void) | undefined
  customers: CustomerResponse[]
  disabled?: boolean | undefined
  error?: string | undefined
  placeholder?: string | undefined
}

export function CustomerSearchSelect({
  value,
  onChange,
  onSelectCustomer,
  customers,
  disabled = false,
  error,
  placeholder = 'Buscar o seleccionar cliente...',
}: CustomerSearchSelectProps) {
  const [isOpen, setIsOpen] = React.useState(false)
  const [search, setSearch] = React.useState('')
  const containerRef = React.useRef<HTMLDivElement>(null)
  const inputRef = React.useRef<HTMLInputElement>(null)

  const selectedCustomer = React.useMemo(
    () => (value ? customers.find((c) => c.externalId === value) : undefined),
    [customers, value],
  )

  const filteredCustomers = React.useMemo(() => {
    if (!search.trim()) return customers
    const term = search.toLowerCase().trim()
    return customers.filter(
      (c) =>
        c.name.toLowerCase().includes(term) ||
        (c.taxId && c.taxId.toLowerCase().includes(term)),
    )
  }, [customers, search])

  React.useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false)
      }
    }
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside)
      setTimeout(() => inputRef.current?.focus(), 50)
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [isOpen])

  const handleSelect = (customer: CustomerResponse) => {
    onSelectCustomer?.(customer)
    onChange?.(customer.externalId)
    setIsOpen(false)
    setSearch('')
  }

  const handleClear = (e: React.MouseEvent) => {
    e.stopPropagation()
    onSelectCustomer?.(null)
    onChange?.(undefined)
    setSearch('')
  }

  return (
    <div ref={containerRef} className="relative w-full">
      <button
        type="button"
        disabled={disabled}
        onClick={() => setIsOpen((prev) => !prev)}
        className={cn(
          'w-full flex items-center justify-between gap-2 px-3 py-1.5 text-xs rounded-md border bg-white text-left transition-colors h-8',
          'focus:outline-none focus:ring-1 focus:ring-teal-500 focus:border-teal-500',
          error
            ? 'border-rose-400 focus:ring-rose-400'
            : 'border-slate-200 hover:border-slate-300',
          disabled && 'opacity-50 cursor-not-allowed bg-slate-50',
        )}
      >
        <div className="flex-1 min-w-0 truncate">
          {selectedCustomer ? (
            <span className="flex items-center gap-1.5 truncate">
              <span className="font-semibold text-slate-900 truncate">
                {selectedCustomer.name}
              </span>
              {selectedCustomer.taxId && (
                <span className="font-mono text-[10px] text-slate-500 bg-slate-100 px-1 py-0.2 rounded">
                  {selectedCustomer.taxId}
                </span>
              )}
            </span>
          ) : (
            <span className="text-slate-400">{placeholder}</span>
          )}
        </div>

        <div className="flex items-center gap-1 text-slate-400 shrink-0">
          {selectedCustomer && !disabled && (
            <span
              role="button"
              tabIndex={0}
              onClick={handleClear}
              className="p-0.5 hover:text-slate-600 rounded cursor-pointer"
              title="Limpiar selección"
            >
              <X className="w-3.5 h-3.5" />
            </span>
          )}
          <ChevronDown
            className={cn(
              'w-3.5 h-3.5 transition-transform duration-200',
              isOpen && 'rotate-180 text-teal-600',
            )}
          />
        </div>
      </button>

      {isOpen && (
        <div className="absolute z-50 mt-1 w-full rounded-md border border-slate-200 bg-white shadow-lg animate-in fade-in-0 zoom-in-95 duration-100">
          <div className="p-2 border-b border-slate-100 bg-slate-50/70 rounded-t-md">
            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
              <Input
                ref={inputRef}
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Buscar por nombre o NIT..."
                className="pl-8 h-8 text-xs bg-white border-slate-200 focus-visible:ring-teal-500"
              />
            </div>
          </div>

          <div className="max-h-48 overflow-y-auto p-1 space-y-0.5">
            {filteredCustomers.length === 0 ? (
              <div className="py-3 text-center text-xs text-slate-400">
                No se encontraron clientes
              </div>
            ) : (
              filteredCustomers.map((c) => {
                const isSelected = c.externalId === value
                return (
                  <button
                    key={c.externalId}
                    type="button"
                    onClick={() => handleSelect(c)}
                    className={cn(
                      'w-full flex items-center justify-between gap-2 px-2.5 py-1.5 text-left rounded text-xs transition-colors',
                      isSelected
                        ? 'bg-teal-50 text-teal-900 font-medium'
                        : 'text-slate-700 hover:bg-slate-100',
                    )}
                  >
                    <div className="min-w-0 flex-1">
                      <div className="font-semibold text-xs text-slate-900 truncate">
                        {c.name}
                      </div>
                      {c.taxId && (
                        <div className="font-mono text-[10px] text-slate-500">
                          NIT: {c.taxId}
                        </div>
                      )}
                    </div>
                    {isSelected && (
                      <Check className="w-3.5 h-3.5 text-teal-600 shrink-0" />
                    )}
                  </button>
                )
              })
            )}
          </div>
        </div>
      )}
    </div>
  )
}
