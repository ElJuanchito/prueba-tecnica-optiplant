import * as React from 'react'
import {
  Calendar,
  DollarSign,
  Plus,
  Receipt,
  RefreshCw,
  Search,
  ShoppingCart,
  TrendingUp,
} from 'lucide-react'
import { AppLayout } from '@/components/layout/AppLayout.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card.tsx'
import { Input } from '@/components/ui/input.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { Permissions } from '@/lib/permissions.ts'
import { useSales } from '../hooks/use-sales.ts'
import type {
  SaleDetailResponse,
  SaleQueryParams,
  SaleStatus,
  SaleSummaryResponse,
} from '../types/index.ts'
import { SaleTable } from './SaleTable.tsx'
import { SaleRegistrationDialog } from './SaleRegistrationDialog.tsx'
import { SaleDetailDialog } from './SaleDetailDialog.tsx'
import { SaleCancelDialog } from './SaleCancelDialog.tsx'
import { InvoiceSearchDialog } from './InvoiceSearchDialog.tsx'

export function SalesDashboard() {
  const { t } = useTranslation()
  const sessionQuery = useSession()
  const session = sessionQuery.data
  const role = session?.role

  const canRegister = Permissions.canRegisterSale(role, Boolean(session?.branchId))
  const canCancel = Permissions.canCancelSale(role)

  // Filters and pagination state
  const [statusFilter, setStatusFilter] = React.useState<string>('ALL')
  const [dateFrom, setDateFrom] = React.useState<string>('')
  const [dateTo, setDateTo] = React.useState<string>('')
  const [page, setPage] = React.useState<number>(0)
  const pageSize = 15

  const queryParams = React.useMemo<SaleQueryParams>(() => {
    const params: SaleQueryParams = {
      page,
      size: pageSize,
    }
    if (statusFilter && statusFilter !== 'ALL') {
      params.status = statusFilter as SaleStatus
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
  }, [statusFilter, dateFrom, dateTo, page])

  const salesQuery = useSales(queryParams)
  const salesPage = salesQuery.data
  const sales = salesPage?.content ?? []
  const aggregates = salesPage?.aggregates ?? { salesCount: 0, totalAmount: 0 }
  const avgTicket =
    aggregates.salesCount > 0
      ? aggregates.totalAmount / aggregates.salesCount
      : 0

  // Dialog states
  const [isRegisterOpen, setIsRegisterOpen] = React.useState(false)
  const [isSearchOpen, setIsSearchOpen] = React.useState(false)
  const [selectedDetailId, setSelectedDetailId] = React.useState<string | null>(
    null,
  )
  const [selectedCancelSale, setSelectedCancelSale] =
    React.useState<SaleSummaryResponse | null>(null)

  const handleOpenDetail = (sale: SaleSummaryResponse) => {
    setSelectedDetailId(sale.externalId)
  }

  const handleFoundFromSearch = (sale: SaleDetailResponse) => {
    setSelectedDetailId(sale.externalId)
  }

  const handleOpenCancel = (sale: SaleSummaryResponse) => {
    setSelectedCancelSale(sale)
  }

  const handleRefresh = () => {
    salesQuery.refetch()
  }

  return (
    <AppLayout activeModule="sales">
      <div className="space-y-6">
        {/* Top Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2.5">
              <div className="h-10 w-10 rounded-xl bg-teal-500 text-white flex items-center justify-center shadow-xs">
                <ShoppingCart className="h-5 w-5" />
              </div>
              <div>
                <h1 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight">
                  {t('sales.title')}
                </h1>
                <p className="text-xs text-slate-500">{t('sales.subtitle')}</p>
              </div>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex items-center gap-2.5">
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="text-xs border-slate-300 text-slate-700 hover:text-slate-900"
              onClick={() => setIsSearchOpen(true)}
            >
              <Search className="h-3.5 w-3.5 mr-1.5 text-slate-500" />
              {t('sales.searchByInvoice')}
            </Button>

            <Button
              type="button"
              size="sm"
              className="text-xs bg-teal-600 hover:bg-teal-700 text-white shadow-xs font-semibold"
              onClick={() => setIsRegisterOpen(true)}
              disabled={!canRegister}
            >
              <Plus className="h-3.5 w-3.5 mr-1.5" />
              {t('sales.newSale')}
            </Button>
          </div>
        </div>

        {/* Aggregates Stats Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Card className="border-slate-200 shadow-2xs">
            <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
              <CardTitle className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                {t('sales.stats.totalSales')}
              </CardTitle>
              <div className="h-7 w-7 rounded-lg bg-teal-50 text-teal-600 flex items-center justify-center">
                <Receipt className="h-4 w-4" />
              </div>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-black text-slate-900">
                {aggregates.salesCount}
              </div>
              <p className="text-[11px] text-slate-500 mt-0.5">
                {t('sales.stats.totalSalesDesc')}
              </p>
            </CardContent>
          </Card>

          <Card className="border-slate-200 shadow-2xs">
            <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
              <CardTitle className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                {t('sales.stats.totalRevenue')}
              </CardTitle>
              <div className="h-7 w-7 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
                <DollarSign className="h-4 w-4" />
              </div>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-black text-slate-900 font-mono">
                ${aggregates.totalAmount.toFixed(2)}
              </div>
              <p className="text-[11px] text-slate-500 mt-0.5">
                {t('sales.stats.totalRevenueDesc')}
              </p>
            </CardContent>
          </Card>

          <Card className="border-slate-200 shadow-2xs">
            <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
              <CardTitle className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                {t('sales.stats.avgTicket')}
              </CardTitle>
              <div className="h-7 w-7 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
                <TrendingUp className="h-4 w-4" />
              </div>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-black text-slate-900 font-mono">
                ${avgTicket.toFixed(2)}
              </div>
              <p className="text-[11px] text-slate-500 mt-0.5">
                {t('sales.stats.avgTicketDesc')}
              </p>
            </CardContent>
          </Card>
        </div>

        {/* Filter Bar */}
        <div className="p-3 bg-white border border-slate-200 rounded-xl shadow-2xs flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2.5">
            {/* Status Select */}
            <div className="w-40">
              <select
                value={statusFilter}
                onChange={(e: React.ChangeEvent<HTMLSelectElement>) => {
                  setStatusFilter(e.target.value)
                  setPage(0)
                }}
                className="w-full flex h-8 rounded-md border border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-teal-500"
              >
                <option value="ALL">{t('common.allStatus')}</option>
                <option value="COMPLETED">{t('sales.status.COMPLETED')}</option>
                <option value="CANCELLED">{t('sales.status.CANCELLED')}</option>
              </select>
            </div>

            {/* Date From */}
            <div className="flex items-center gap-1.5 text-xs text-slate-600">
              <Calendar className="h-3.5 w-3.5 text-slate-400" />
              <Input
                type="date"
                value={dateFrom}
                onChange={(e) => {
                  setDateFrom(e.target.value)
                  setPage(0)
                }}
                className="text-xs h-8 bg-slate-50 border-slate-200 w-36"
              />
            </div>

            {/* Date To */}
            <div className="flex items-center gap-1.5 text-xs text-slate-600">
              <span className="text-slate-400">{t('common.to')}</span>
              <Input
                type="date"
                value={dateTo}
                onChange={(e) => {
                  setDateTo(e.target.value)
                  setPage(0)
                }}
                className="text-xs h-8 bg-slate-50 border-slate-200 w-36"
              />
            </div>
          </div>

          <Button
            type="button"
            variant="outline"
            size="sm"
            className="h-8 text-xs border-slate-200 text-slate-700 hover:bg-slate-50"
            onClick={handleRefresh}
            title={t('common.refresh')}
          >
            <RefreshCw
              className={`h-3.5 w-3.5 mr-1 text-slate-500 ${
                salesQuery.isFetching ? 'animate-spin' : ''
              }`}
            />
            {t('common.refresh')}
          </Button>
        </div>

        {/* Sales Table */}
        <SaleTable
          sales={sales}
          isLoading={salesQuery.isLoading}
          totalElements={salesPage?.totalElements ?? 0}
          page={page}
          size={pageSize}
          onPageChange={setPage}
          onViewDetail={handleOpenDetail}
          onCancelSale={handleOpenCancel}
          canCancel={canCancel}
        />

        {/* Dialogs */}
        <SaleRegistrationDialog
          open={isRegisterOpen}
          onOpenChange={setIsRegisterOpen}
          onSuccess={(created) => {
            setSelectedDetailId(created.externalId)
            salesQuery.refetch()
          }}
        />

        <SaleDetailDialog
          saleExternalId={selectedDetailId}
          open={Boolean(selectedDetailId)}
          onOpenChange={(open) => {
            if (!open) setSelectedDetailId(null)
          }}
          onCancelSale={(externalId) => {
            const found = sales.find((s) => s.externalId === externalId)
            if (found) setSelectedCancelSale(found)
          }}
          canCancel={canCancel}
        />

        <SaleCancelDialog
          sale={selectedCancelSale}
          open={Boolean(selectedCancelSale)}
          onOpenChange={(open) => {
            if (!open) setSelectedCancelSale(null)
          }}
          onSuccess={() => {
            salesQuery.refetch()
          }}
        />

        <InvoiceSearchDialog
          open={isSearchOpen}
          onOpenChange={setIsSearchOpen}
          onFoundSale={handleFoundFromSearch}
        />
      </div>
    </AppLayout>
  )
}
