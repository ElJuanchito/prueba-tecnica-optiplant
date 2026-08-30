import * as React from 'react'
import {
  AlertCircle,
  Calculator,
  Percent,
  Plus,
  Tag,
  Trash2,
} from 'lucide-react'
import { Button } from '@/components/ui/button.tsx'
import { Input } from '@/components/ui/input.tsx'
import { Label } from '@/components/ui/label.tsx'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert.tsx'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { useProducts } from '@/features/catalog/hooks/use-products.ts'
import {
  usePriceLists,
  usePricingQuote,
} from '../hooks/use-pricing.ts'
import type { QuoteItemRequest, QuoteResponse } from '../types/index.ts'
import {
  type ProductOption,
  ProductSearchSelect,
} from '@/features/transfers/components/ProductSearchSelect.tsx'

export function QuoteSimulator() {
  const { t } = useTranslation()
  const priceListsQuery = usePriceLists({ active: true, size: 50 })
  const productsQuery = useProducts({ active: 'true', size: 100 })

  const priceLists = priceListsQuery.data?.content ?? []
  const productOptions = React.useMemo<ProductOption[]>(() => {
    return (productsQuery.data?.content ?? []).map((p) => ({
      externalId: p.externalId,
      sku: p.sku,
      name: p.name,
      baseUnit: p.baseUnit,
      category: p.category,
    }))
  }, [productsQuery.data])

  const productsMap = React.useMemo(() => {
    const map = new Map<string, { sku: string; name: string }>()
    for (const p of productsQuery.data?.content ?? []) {
      map.set(p.externalId, { sku: p.sku, name: p.name })
    }
    return map
  }, [productsQuery.data])

  const [selectedPriceListId, setSelectedPriceListId] =
    React.useState<string>('')
  const [items, setItems] = React.useState<QuoteItemRequest[]>([
    { productExternalId: '', quantity: 1, discountPercent: 0 },
  ])
  const [serverError, setServerError] = React.useState<string | null>(null)
  const [quoteResult, setQuoteResult] = React.useState<QuoteResponse | null>(
    null,
  )

  const quoteMutation = usePricingQuote()

  const handleAddItem = () => {
    setItems((prev) => [
      ...prev,
      { productExternalId: '', quantity: 1, discountPercent: 0 },
    ])
  }

  const handleRemoveItem = (index: number) => {
    setItems((prev) => prev.filter((_, i) => i !== index))
  }

  const handleUpdateItem = (
    index: number,
    field: keyof QuoteItemRequest,
    value: unknown,
  ) => {
    setItems((prev) => {
      const updated = [...prev]
      const current = updated[index]
      if (current) {
        updated[index] = { ...current, [field]: value }
      }
      return updated
    })
  }

  const handleCalculate = (e: React.FormEvent) => {
    e.preventDefault()
    setServerError(null)

    const validItems = items.filter(
      (item) => item.productExternalId && item.quantity > 0,
    )
    if (validItems.length === 0) {
      setServerError(t('pricing.quote.noItems'))
      return
    }

    quoteMutation.mutate(
      {
        priceListExternalId: selectedPriceListId || null,
        items: validItems.map((item) => ({
          productExternalId: item.productExternalId,
          quantity: Number(item.quantity),
          discountPercent:
            item.discountPercent !== undefined && item.discountPercent !== null
              ? Number(item.discountPercent)
              : 0,
        })),
      },
      {
        onSuccess: (res) => {
          setQuoteResult(res)
        },
        onError: (err) => {
          setServerError(err.message || t('common.error'))
          setQuoteResult(null)
        },
      },
    )
  }

  const totalCalculated = React.useMemo(() => {
    if (!quoteResult) return 0
    return quoteResult.items.reduce((acc, i) => acc + i.subtotal, 0)
  }, [quoteResult])

  return (
    <div className="space-y-6">
      <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-2xs space-y-4">
        <div>
          <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
            <Calculator className="h-4 w-4 text-violet-600" />
            <span>{t('pricing.quote.title')}</span>
          </h2>
          <p className="text-xs text-slate-500 mt-0.5">
            {t('pricing.quote.desc')}
          </p>
        </div>

        {serverError && (
          <Alert variant="destructive" className="py-2 px-3 text-xs">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle className="text-xs font-semibold">
              {t('common.error')}
            </AlertTitle>
            <AlertDescription className="text-[11px]">
              {serverError}
            </AlertDescription>
          </Alert>
        )}

        <form onSubmit={handleCalculate} className="space-y-4">
          {/* Price List Selection */}
          <div className="w-full sm:w-80 space-y-1.5">
            <Label htmlFor="quote-price-list" className="text-xs font-semibold">
              {t('pricing.rates.selectList')}
            </Label>
            <select
              id="quote-price-list"
              value={selectedPriceListId}
              onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
                setSelectedPriceListId(e.target.value)
              }
              className="w-full flex h-9 rounded-md border border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-violet-500"
            >
              <option value="">
                {t('sales.dialog.defaultPriceList')}
              </option>
              {priceLists.map((pl) => (
                <option key={pl.externalId} value={pl.externalId}>
                  {pl.name} ({pl.code}) - {t('pricing.priceLists.maxDiscount')}: {pl.maxDiscountPercent}%
                </option>
              ))}
            </select>
          </div>

          {/* Items Table / Row list */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                {t('sales.dialog.items')}
              </Label>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="h-7 text-xs border-violet-300 text-violet-700 hover:bg-violet-50"
                onClick={handleAddItem}
              >
                <Plus className="h-3.5 w-3.5 mr-1" />
                {t('sales.dialog.addItem')}
              </Button>
            </div>

            <div className="space-y-2">
              {items.map((item, index) => (
                <div
                  key={index}
                  className="p-3 bg-slate-50 border border-slate-200 rounded-xl grid grid-cols-1 sm:grid-cols-12 gap-2.5 items-center text-xs"
                >
                  <div className="sm:col-span-6">
                    <ProductSearchSelect
                      products={productOptions}
                      value={item.productExternalId}
                      onSelectProduct={(p) =>
                        handleUpdateItem(index, 'productExternalId', p.externalId)
                      }
                      placeholder={t('pricing.quote.selectProductPlaceholder')}
                    />
                  </div>

                  <div className="sm:col-span-2">
                    <Input
                      type="number"
                      step="1"
                      min="1"
                      value={item.quantity}
                      onChange={(e) =>
                        handleUpdateItem(
                          index,
                          'quantity',
                          Math.max(1, Number(e.target.value)),
                        )
                      }
                      placeholder="Cant."
                      className="text-xs h-9 bg-white"
                    />
                  </div>

                  <div className="sm:col-span-3">
                    <div className="relative">
                      <Input
                        type="number"
                        step="0.1"
                        min="0"
                        max="100"
                        value={item.discountPercent ?? 0}
                        onChange={(e) =>
                          handleUpdateItem(
                            index,
                            'discountPercent',
                            Number(e.target.value),
                          )
                        }
                        placeholder="Desc. %"
                        className="text-xs h-9 pr-6 bg-white"
                      />
                      <Percent className="absolute right-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
                    </div>
                  </div>

                  <div className="sm:col-span-1 flex justify-center">
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      className="h-8 w-8 p-0 text-slate-400 hover:text-rose-600 hover:bg-rose-50"
                      onClick={() => handleRemoveItem(index)}
                      disabled={items.length <= 1}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="flex justify-end pt-2">
            <Button
              type="submit"
              variant="default"
              size="sm"
              className="text-xs bg-violet-600 hover:bg-violet-700 text-white font-semibold"
              disabled={quoteMutation.isPending}
            >
              {quoteMutation.isPending ? (
                <span>{t('pricing.quote.calculating')}</span>
              ) : (
                <span className="flex items-center gap-1.5">
                  <Calculator className="h-3.5 w-3.5" />
                  {t('pricing.quote.calculate')}
                </span>
              )}
            </Button>
          </div>
        </form>
      </div>

      {/* Quote Results Section */}
      {quoteResult && (
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-2xs space-y-4 animate-in fade-in-0 duration-200">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-100 pb-3">
            <div>
              <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                <Tag className="h-4 w-4 text-emerald-600" />
                <span>{t('pricing.quote.results')}</span>
              </h3>
              {quoteResult.code && (
                <p className="text-xs text-slate-500 mt-0.5">
                  {t('pricing.quote.appliedList')}:{' '}
                  <strong className="text-slate-800 font-mono">
                    {quoteResult.code}
                  </strong>{' '}
                  ({t('pricing.priceLists.maxDiscount')}: {quoteResult.maxDiscountPercent}%)
                </p>
              )}
            </div>

            <div className="text-right">
              <span className="text-xs text-slate-500 font-medium">
                {t('sales.dialog.totalAmount')}:
              </span>
              <div className="font-mono text-xl font-extrabold text-emerald-700">
                ${totalCalculated.toFixed(2)}
              </div>
            </div>
          </div>

          <div className="rounded-xl border border-slate-200 overflow-hidden">
            <Table>
              <TableHeader className="bg-slate-50 border-b border-slate-200">
                <TableRow>
                  <TableHead className="text-xs font-bold text-slate-700 h-9 px-3">
                    {t('pricing.rates.product')}
                  </TableHead>
                  <TableHead className="text-xs font-bold text-slate-700 h-9 px-3 text-right">
                    {t('sales.dialog.listPrice')}
                  </TableHead>
                  <TableHead className="text-xs font-bold text-slate-700 h-9 px-3 text-right">
                    {t('sales.dialog.unitPrice')}
                  </TableHead>
                  <TableHead className="text-xs font-bold text-slate-700 h-9 px-3 text-right">
                    {t('sales.dialog.subtotal')}
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {quoteResult.items.map((item, idx) => {
                  const product = productsMap.get(item.productExternalId)
                  return (
                    <TableRow
                      key={idx}
                      className="border-b border-slate-100 last:border-0 text-xs"
                    >
                      <TableCell className="py-2.5 px-3">
                        <div className="font-semibold text-slate-900">
                          {product?.name ?? '—'}
                        </div>
                        <div className="font-mono text-[10px] text-slate-500">
                          {product?.sku ?? '—'}
                        </div>
                      </TableCell>
                      <TableCell className="py-2.5 px-3 text-right font-mono text-slate-500">
                        ${item.listUnitPrice.toFixed(2)}
                      </TableCell>
                      <TableCell className="py-2.5 px-3 text-right font-mono font-semibold text-slate-800">
                        ${item.unitPrice.toFixed(2)}
                      </TableCell>
                      <TableCell className="py-2.5 px-3 text-right font-mono font-bold text-slate-900">
                        ${item.subtotal.toFixed(2)}
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </div>
        </div>
      )}
    </div>
  )
}
