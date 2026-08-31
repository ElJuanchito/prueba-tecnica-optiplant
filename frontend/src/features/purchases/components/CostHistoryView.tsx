import * as React from 'react'
import { History, Package } from 'lucide-react'
import { Button } from '@/components/ui/button.tsx'
import { Input } from '@/components/ui/input.tsx'
import { Label } from '@/components/ui/label.tsx'
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
import { useCostHistory, useSuppliers } from '../hooks/use-purchases.ts'
import type { CostHistoryQueryParams } from '../types/index.ts'
import { SupplierSearchSelect } from './SupplierSearchSelect.tsx'
import {
  type ProductOption,
  ProductSearchSelect,
} from '@/features/transfers/components/ProductSearchSelect.tsx'

export function CostHistoryView() {
  const { t } = useTranslation()

  // Selected product state (required for querying cost history)
  const [selectedProductId, setSelectedProductId] = React.useState<string>('')
  const [selectedSupplierId, setSelectedSupplierId] = React.useState<string>('')
  const [dateFrom, setDateFrom] = React.useState<string>('')
  const [dateTo, setDateTo] = React.useState<string>('')
  const [page, setPage] = React.useState<number>(0)
  const pageSize = 15

  // Load products and suppliers
  const productsQuery = useProducts({ active: 'true', size: 100 })
  const suppliersQuery = useSuppliers({ active: true, size: 100 })

  const suppliers = suppliersQuery.data?.content ?? []
  const productOptions = React.useMemo<ProductOption[]>(() => {
    return (productsQuery.data?.content ?? []).map((p) => ({
      externalId: p.externalId,
      sku: p.sku,
      name: p.name,
      baseUnit: p.baseUnit,
      category: p.category,
    }))
  }, [productsQuery.data])

  const queryParams = React.useMemo<CostHistoryQueryParams>(() => {
    const params: CostHistoryQueryParams = {
      productExternalId: selectedProductId,
      page,
      size: pageSize,
    }
    if (selectedSupplierId) {
      params.supplierExternalId = selectedSupplierId
    }
    if (dateFrom) {
      params.from = new Date(dateFrom).toISOString()
    }
    if (dateTo) {
      const endOfDay = new Date(dateTo)
      endOfDay.setHours(23, 59, 59, 999)
      params.to = endOfDay.toISOString()
    }
    return params
  }, [selectedProductId, selectedSupplierId, dateFrom, dateTo, page])

  const historyQuery = useCostHistory(queryParams, Boolean(selectedProductId))
  const historyData = historyQuery.data
  const historyItems = historyData?.content ?? []
  const totalElements = historyData?.totalElements ?? 0
  const totalPages = Math.ceil(totalElements / pageSize) || 1

  const formatDate = (isoString?: string | null) => {
    if (!isoString) return '—'
    try {
      const d = new Date(isoString)
      return d.toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      })
    } catch {
      return isoString
    }
  }

  return (
    <div className="space-y-4">
      {/* Header & Product Selector */}
      <div className="p-4 bg-white border border-slate-200 rounded-xl shadow-2xs space-y-3.5">
        <div className="flex items-center gap-2 text-rose-600">
          <History className="h-5 w-5" />
          <h2 className="text-sm font-bold text-slate-900">
            {t('purchases.costHistory.title')}
          </h2>
        </div>
        <p className="text-xs text-slate-500">
          {t('purchases.costHistory.subtitle')}
        </p>

        {/* Filters Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 pt-1">
          {/* Required Product Select */}
          <div className="space-y-1 sm:col-span-2 lg:col-span-1">
            <Label className="text-[11px] font-bold uppercase tracking-wider text-slate-700">
              {t('purchases.costHistory.product')}{' '}
              <span className="text-rose-500">*</span>
            </Label>
            <ProductSearchSelect
              products={productOptions}
              value={selectedProductId}
              onChange={(val) => {
                setSelectedProductId(val)
                setPage(0)
              }}
              placeholder={t('purchases.costHistory.searchProductPlaceholder')}
            />
          </div>

          {/* Supplier Filter */}
          <div className="space-y-1 sm:col-span-2 lg:col-span-1">
            <Label className="text-[11px] font-bold uppercase tracking-wider text-slate-700">
              {t('purchases.costHistory.supplier')}
            </Label>
            <SupplierSearchSelect
              suppliers={suppliers}
              value={selectedSupplierId}
              onChange={(val) => {
                setSelectedSupplierId(val)
                setPage(0)
              }}
              placeholder={t('purchases.orders.allSuppliers')}
            />
          </div>

          {/* Date From */}
          <div className="space-y-1">
            <Label className="text-[11px] font-bold uppercase tracking-wider text-slate-700">
              {t('inventory.fromDate')}
            </Label>
            <Input
              type="date"
              value={dateFrom}
              onChange={(e) => {
                setDateFrom(e.target.value)
                setPage(0)
              }}
              className="text-xs h-9 bg-slate-50 border-slate-200"
            />
          </div>

          {/* Date To */}
          <div className="space-y-1">
            <Label className="text-[11px] font-bold uppercase tracking-wider text-slate-700">
              {t('inventory.toDate')}
            </Label>
            <Input
              type="date"
              value={dateTo}
              onChange={(e) => {
                setDateTo(e.target.value)
                setPage(0)
              }}
              className="text-xs h-9 bg-slate-50 border-slate-200"
            />
          </div>
        </div>
      </div>

      {/* Main Content Area */}
      {!selectedProductId ? (
        <div className="rounded-xl border border-dashed border-slate-200 bg-white p-16 text-center shadow-2xs">
          <Package className="mx-auto h-10 w-10 text-slate-300 mb-2" />
          <h3 className="text-sm font-semibold text-slate-700">
            {t('purchases.costHistory.selectProductPrompt')}
          </h3>
          <p className="text-xs text-slate-400 mt-1 max-w-md mx-auto">
            Utilice el selector superior para consultar las órdenes de compra y
            el costo unitario efectivo pactado a lo largo del tiempo.
          </p>
        </div>
      ) : historyQuery.isLoading ? (
        <div className="rounded-xl border border-slate-200 bg-white p-12 text-center shadow-2xs">
          <div className="inline-flex h-8 w-8 animate-spin rounded-full border-2 border-slate-300 border-t-rose-600" />
          <p className="mt-2 text-xs text-slate-500">{t('common.loading')}</p>
        </div>
      ) : historyItems.length === 0 ? (
        <div className="rounded-xl border border-slate-200 bg-white p-12 text-center shadow-2xs">
          <p className="text-sm font-semibold text-slate-700">
            {t('purchases.costHistory.noHistory')}
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xs">
            <Table>
              <TableHeader>
                <TableRow className="bg-slate-50/75 hover:bg-slate-50/75">
                  <TableHead className="text-xs font-bold text-slate-700">
                    {t('purchases.costHistory.orderNumber')}
                  </TableHead>
                  <TableHead className="text-xs font-bold text-slate-700">
                    {t('purchases.costHistory.supplier')}
                  </TableHead>
                  <TableHead className="text-right text-xs font-bold text-slate-700">
                    {t('purchases.costHistory.quantity')}
                  </TableHead>
                  <TableHead className="text-right text-xs font-bold text-slate-700">
                    {t('purchases.costHistory.grossUnitCost')}
                  </TableHead>
                  <TableHead className="text-right text-xs font-bold text-slate-700">
                    {t('purchases.costHistory.discountPercent')}
                  </TableHead>
                  <TableHead className="text-right text-xs font-bold text-slate-700">
                    {t('purchases.costHistory.effectiveUnitCost')}
                  </TableHead>
                  <TableHead className="text-xs font-bold text-slate-700">
                    {t('purchases.costHistory.orderedAt')}
                  </TableHead>
                  <TableHead className="text-xs font-bold text-slate-700">
                    {t('purchases.costHistory.receivedAt')}
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {historyItems.map((item, idx) => (
                  <TableRow
                    key={`${item.orderExternalId}-${idx}`}
                    className="hover:bg-slate-50/50"
                  >
                    <TableCell className="font-mono font-bold text-rose-700 text-xs">
                      {item.orderNumber}
                    </TableCell>
                    <TableCell className="text-xs font-semibold text-slate-900">
                      {item.supplier.name}
                    </TableCell>
                    <TableCell className="text-right font-mono font-bold text-xs text-slate-900">
                      {item.quantity}
                    </TableCell>
                    <TableCell className="text-right font-mono text-xs text-slate-600">
                      ${item.unitCost.toFixed(2)}
                    </TableCell>
                    <TableCell className="text-right font-mono text-xs text-slate-600">
                      {item.discountPercent > 0
                        ? `${item.discountPercent}%`
                        : '—'}
                    </TableCell>
                    <TableCell className="text-right font-mono font-black text-xs text-emerald-800 bg-emerald-50/30">
                      ${item.effectiveUnitCost.toFixed(2)}
                    </TableCell>
                    <TableCell className="text-xs text-slate-600">
                      {formatDate(item.orderedAt)}
                    </TableCell>
                    <TableCell className="text-xs text-slate-600">
                      {formatDate(item.receivedAt)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          {/* Pagination */}
          <div className="flex items-center justify-between text-xs text-slate-500 px-1">
            <span>
              {t('common.showing')}{' '}
              <strong className="font-semibold text-slate-700">
                {totalElements > 0 ? page * pageSize + 1 : 0}
              </strong>{' '}
              {t('common.to')}{' '}
              <strong className="font-semibold text-slate-700">
                {Math.min((page + 1) * pageSize, totalElements)}
              </strong>{' '}
              {t('common.of')}{' '}
              <strong className="font-semibold text-slate-700">
                {totalElements}
              </strong>{' '}
              {t('common.results')}
            </span>

            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
                className="h-7 text-xs px-2.5"
              >
                {t('common.previous')}
              </Button>
              <span className="font-medium">
                {page + 1} / {totalPages}
              </span>
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage(page + 1)}
                className="h-7 text-xs px-2.5"
              >
                {t('common.next')}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
