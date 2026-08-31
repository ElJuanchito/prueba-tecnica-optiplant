import * as React from 'react'
import { Check, ChevronDown, Search, X } from 'lucide-react'
import { cn } from '@/lib/utils.ts'
import { Input } from '@/components/ui/input.tsx'
import type { SupplierResponse } from '../types/index.ts'

interface SupplierSearchSelectProps {
  value?: string | undefined
  onChange?: ((value: string) => void) | undefined
  onSelectSupplier?: ((supplier: SupplierResponse) => void) | undefined
  suppliers: SupplierResponse[]
  disabled?: boolean | undefined
  error?: string | undefined
  placeholder?: string | undefined
}

export function SupplierSearchSelect({
  value,
  onChange,
  onSelectSupplier,
  suppliers,
  disabled = false,
  error,
  placeholder = 'Seleccionar proveedor...',
}: SupplierSearchSelectProps) {
  const [isOpen, setIsOpen] = React.useState(false)
  const [search, setSearch] = React.useState('')
  const containerRef = React.useRef<HTMLDivElement>(null)
  const inputRef = React.useRef<HTMLInputElement>(null)

  const selectedSupplier = React.useMemo(
    () => (value ? suppliers.find((s) => s.externalId === value) : undefined),
    [suppliers, value],
  )

  const filteredSuppliers = React.useMemo(() => {
    if (!search.trim()) return suppliers
    const term = search.toLowerCase().trim()
    return suppliers.filter(
      (s) =>
        s.name.toLowerCase().includes(term) ||
        s.taxId.toLowerCase().includes(term) ||
        (s.contactName && s.contactName.toLowerCase().includes(term)),
    )
  }, [suppliers, search])

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

  const handleSelect = (supplier: SupplierResponse) => {
    onSelectSupplier?.(supplier)
    onChange?.(supplier.externalId)
    setIsOpen(false)
    setSearch('')
  }

  const handleClear = (e: React.MouseEvent) => {
    e.stopPropagation()
    onChange?.('')
    setSearch('')
  }

  return (
    <div ref={containerRef} className="relative w-full">
      <button
        type="button"
        disabled={disabled}
        onClick={() => setIsOpen((prev) => !prev)}
        className={cn(
          'w-full flex items-center justify-between gap-2 px-3 py-2 text-xs rounded-md border bg-white text-left transition-colors',
          'focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500',
          error
            ? 'border-rose-400 focus:ring-rose-400'
            : 'border-slate-200 hover:border-slate-300',
          disabled && 'opacity-50 cursor-not-allowed bg-slate-50',
        )}
      >
        <div className="flex-1 min-w-0 truncate">
          {selectedSupplier ? (
            <span className="flex items-center gap-1.5 truncate">
              <span className="font-semibold text-slate-900 truncate">
                {selectedSupplier.name}
              </span>
              <span className="font-mono text-[11px] text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded">
                {selectedSupplier.taxId}
              </span>
            </span>
          ) : (
            <span className="text-slate-400">{placeholder}</span>
          )}
        </div>

        <div className="flex items-center gap-1 text-slate-400 shrink-0">
          {selectedSupplier && !disabled && (
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
              isOpen && 'rotate-180 text-rose-600',
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
                placeholder="Buscar por nombre, NIT o contacto..."
                className="pl-8 h-8 text-xs bg-white border-slate-200 focus-visible:ring-rose-500"
              />
            </div>
          </div>

          <div className="max-h-52 overflow-y-auto p-1 space-y-0.5">
            {filteredSuppliers.length === 0 ? (
              <div className="py-4 text-center text-xs text-slate-400">
                {suppliers.length === 0
                  ? 'No hay proveedores disponibles'
                  : 'No se encontraron proveedores coincidentes'}
              </div>
            ) : (
              filteredSuppliers.map((s) => {
                const isSelected = s.externalId === value
                return (
                  <button
                    key={s.externalId}
                    type="button"
                    onClick={() => handleSelect(s)}
                    className={cn(
                      'w-full flex items-center justify-between gap-2 px-2.5 py-1.5 text-left rounded text-xs transition-colors',
                      isSelected
                        ? 'bg-rose-50 text-rose-900 font-medium'
                        : 'text-slate-700 hover:bg-slate-100',
                    )}
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-1.5">
                        <span className="font-semibold text-slate-900 truncate">
                          {s.name}
                        </span>
                        <span className="font-mono text-[10px] bg-slate-100 text-slate-600 px-1 py-0.2 rounded">
                          {s.taxId}
                        </span>
                      </div>
                      {s.contactName && (
                        <div className="text-[11px] text-slate-500 truncate mt-0.5">
                          {s.contactName} {s.phone ? `• ${s.phone}` : ''}
                        </div>
                      )}
                    </div>
                    {isSelected && (
                      <Check className="w-3.5 h-3.5 text-rose-600 shrink-0" />
                    )}
                  </button>
                )
              })
            )}
          </div>
        </div>
      )}

      {error && (
        <p className="text-[11px] text-rose-600 font-medium mt-1">{error}</p>
      )}
    </div>
  )
}
