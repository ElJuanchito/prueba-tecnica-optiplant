import * as React from 'react'
import { Check, ChevronDown, Search, X } from 'lucide-react'
import { cn } from '@/lib/utils.ts'
import { Input } from '@/components/ui/input.tsx'

export interface ProductOption {
  externalId: string
  sku: string
  name: string
  baseUnit?: string | undefined
  category?:
    | {
        name?: string | undefined
        externalId?: string | undefined
        active?: boolean | undefined
      }
    | null
    | undefined
}

interface ProductSearchSelectProps {
  value?: string | undefined
  onChange?: ((value: string) => void) | undefined
  onSelectProduct?: ((product: ProductOption) => void) | undefined
  clearOnSelect?: boolean | undefined
  products: ProductOption[]
  disabled?: boolean | undefined
  error?: string | undefined
  placeholder?: string | undefined
}

export function ProductSearchSelect({
  value,
  onChange,
  onSelectProduct,
  clearOnSelect = false,
  products,
  disabled = false,
  error,
  placeholder = 'Buscar o seleccionar producto...',
}: ProductSearchSelectProps) {
  const [isOpen, setIsOpen] = React.useState(false)
  const [search, setSearch] = React.useState('')
  const containerRef = React.useRef<HTMLDivElement>(null)
  const inputRef = React.useRef<HTMLInputElement>(null)

  const selectedProduct = React.useMemo(
    () =>
      !clearOnSelect && value
        ? products.find((p) => p.externalId === value)
        : undefined,
    [clearOnSelect, products, value],
  )

  const filteredProducts = React.useMemo(() => {
    if (!search.trim()) return products
    const term = search.toLowerCase().trim()
    return products.filter(
      (p) =>
        p.sku.toLowerCase().includes(term) ||
        p.name.toLowerCase().includes(term) ||
        (p.category?.name && p.category.name.toLowerCase().includes(term)),
    )
  }, [products, search])

  // Handle click outside to close dropdown
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
      // Focus input when opened
      setTimeout(() => inputRef.current?.focus(), 50)
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [isOpen])

  const handleSelect = (product: ProductOption) => {
    if (onSelectProduct) {
      onSelectProduct(product)
    }
    if (onChange) {
      onChange(product.externalId)
    }
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
      {/* Trigger Button */}
      <button
        type="button"
        disabled={disabled}
        onClick={() => setIsOpen((prev) => !prev)}
        className={cn(
          'w-full flex items-center justify-between gap-2 px-3 py-2 text-xs rounded-md border bg-white text-left transition-colors',
          'focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500',
          error
            ? 'border-rose-400 focus:ring-rose-400'
            : 'border-slate-200 hover:border-slate-300',
          disabled && 'opacity-50 cursor-not-allowed bg-slate-50',
        )}
      >
        <div className="flex-1 min-w-0 truncate">
          {selectedProduct ? (
            <span className="flex items-center gap-1.5 truncate">
              <span className="font-mono font-semibold text-emerald-700 bg-emerald-50 px-1.5 py-0.5 rounded text-[11px]">
                {selectedProduct.sku}
              </span>
              <span className="font-medium text-slate-800 truncate">
                {selectedProduct.name}
              </span>
              {selectedProduct.baseUnit && (
                <span className="text-[10px] text-slate-400">
                  ({selectedProduct.baseUnit})
                </span>
              )}
            </span>
          ) : (
            <span className="text-slate-400">{placeholder}</span>
          )}
        </div>

        <div className="flex items-center gap-1 text-slate-400 shrink-0">
          {selectedProduct && !disabled && (
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
              isOpen && 'rotate-180 text-emerald-600',
            )}
          />
        </div>
      </button>

      {/* Dropdown Panel */}
      {isOpen && (
        <div className="absolute z-50 mt-1 w-full rounded-md border border-slate-200 bg-white shadow-lg animate-in fade-in-0 zoom-in-95 duration-100">
          {/* Search Box */}
          <div className="p-2 border-b border-slate-100 bg-slate-50/70 rounded-t-md">
            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
              <Input
                ref={inputRef}
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Buscar por SKU, nombre o categoría..."
                className="pl-8 h-8 text-xs bg-white border-slate-200 focus-visible:ring-emerald-500"
              />
            </div>
          </div>

          {/* Options List */}
          <div className="max-h-52 overflow-y-auto p-1 space-y-0.5">
            {filteredProducts.length === 0 ? (
              <div className="py-4 text-center text-xs text-slate-400">
                {products.length === 0
                  ? 'No hay productos disponibles'
                  : 'No se encontraron productos coincidentes'}
              </div>
            ) : (
              filteredProducts.map((p) => {
                const isSelected = p.externalId === value
                return (
                  <button
                    key={p.externalId}
                    type="button"
                    onClick={() => handleSelect(p)}
                    className={cn(
                      'w-full flex items-center justify-between gap-2 px-2.5 py-1.5 text-left rounded text-xs transition-colors',
                      isSelected
                        ? 'bg-emerald-50 text-emerald-900 font-medium'
                        : 'text-slate-700 hover:bg-slate-100',
                    )}
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-1.5">
                        <span className="font-mono font-semibold text-[11px] text-emerald-700">
                          {p.sku}
                        </span>
                        {p.category?.name && (
                          <span className="text-[10px] bg-slate-100 text-slate-500 px-1 py-0.5 rounded truncate max-w-[120px]">
                            {p.category.name}
                          </span>
                        )}
                      </div>
                      <div className="text-[11px] text-slate-800 truncate mt-0.5">
                        {p.name}
                        {p.baseUnit && (
                          <span className="text-slate-400 ml-1">
                            • {p.baseUnit}
                          </span>
                        )}
                      </div>
                    </div>
                    {isSelected && (
                      <Check className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
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
