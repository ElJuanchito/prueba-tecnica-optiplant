import * as React from 'react'
import {
  Building2,
  Calendar,
  Clock,
  DollarSign,
  History,
  List,
  PackageCheck,
  Plus,
  RefreshCw,
  ShoppingBag,
} from 'lucide-react'
import { AppLayout } from '@/components/layout/AppLayout.tsx'
import { Button } from '@/components/ui/button.tsx'
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card.tsx'
import { Input } from '@/components/ui/input.tsx'
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { Permissions } from '@/lib/permissions.ts'
import {
  useApprovePurchaseOrder,
  useDisableSupplier,
  useEnableSupplier,
  usePurchaseOrderDetail,
  usePurchaseOrders,
  useSuppliers,
} from '../hooks/use-purchases.ts'
import type {
  PurchaseOrderDetailResponse,
  PurchaseOrderQueryParams,
  PurchaseOrderStatus,
  PurchaseOrderSummaryResponse,
  SupplierQueryParams,
  SupplierResponse,
} from '../types/index.ts'
import { PurchaseOrderTable } from './PurchaseOrderTable.tsx'
import { PurchaseOrderFormDialog } from './PurchaseOrderFormDialog.tsx'
import { PurchaseOrderDetailDialog } from './PurchaseOrderDetailDialog.tsx'
import { PurchaseOrderCancelDialog } from './PurchaseOrderCancelDialog.tsx'
import { PurchaseReceptionDialog } from './PurchaseReceptionDialog.tsx'
import { SupplierTable } from './SupplierTable.tsx'
import { SupplierFormDialog } from './SupplierFormDialog.tsx'
import { CostHistoryView } from './CostHistoryView.tsx'

export function PurchasesDashboard() {
  const { t } = useTranslation()
  const sessionQuery = useSession()
  const session = sessionQuery.data
  const role = session?.role
  const hasBranch = Boolean(session?.branchId)

  const isAdmin = Permissions.canManageSuppliers(role)
  const canCreateOrder = Permissions.canCreatePurchaseOrder(role, hasBranch)
  const canApprove = Permissions.canApprovePurchaseOrder(role)
  const canCancel = Permissions.canCancelPurchaseOrder(role)
  const canReceive = Permissions.canReceivePurchaseOrder(role, hasBranch)

  const [activeTab, setActiveTab] = React.useState<
    'orders' | 'suppliers' | 'costHistory'
  >('orders')

  // --- Orders Tab State & Queries ---
  const [statusFilter, setStatusFilter] = React.useState<string>('ALL')
  const [dateFrom, setDateFrom] = React.useState<string>('')
  const [dateTo, setDateTo] = React.useState<string>('')
  const [ordersPage, setOrdersPage] = React.useState<number>(0)
  const ordersPageSize = 15

  const orderQueryParams = React.useMemo<PurchaseOrderQueryParams>(() => {
    const params: PurchaseOrderQueryParams = {
      page: ordersPage,
      size: ordersPageSize,
    }
    if (statusFilter && statusFilter !== 'ALL') {
      params.status = statusFilter as PurchaseOrderStatus
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
  }, [statusFilter, dateFrom, dateTo, ordersPage])

  const ordersQuery = usePurchaseOrders(orderQueryParams)
  const ordersPageData = ordersQuery.data
  const orders = ordersPageData?.content ?? []
  const totalOrdersCount = ordersPageData?.totalElements ?? 0

  // Aggregate stats from current page or orders list
  const pendingCount = React.useMemo(
    () => orders.filter((o) => o.status === 'PENDING').length,
    [orders],
  )
  const inReceptionCount = React.useMemo(
    () =>
      orders.filter(
        (o) => o.status === 'APPROVED' || o.status === 'PARTIALLY_RECEIVED',
      ).length,
    [orders],
  )
  const totalPurchasedAmount = React.useMemo(
    () => orders.reduce((sum, o) => sum + o.totalAmount, 0),
    [orders],
  )

  // --- Suppliers Tab State & Queries ---
  const [supplierSearch, setSupplierSearch] = React.useState<string>('')
  const [supplierActiveFilter, setSupplierActiveFilter] =
    React.useState<string>('ALL')
  const [suppliersPage, setSuppliersPage] = React.useState<number>(0)
  const suppliersPageSize = 15

  const supplierQueryParams = React.useMemo<SupplierQueryParams>(() => {
    const params: SupplierQueryParams = {
      page: suppliersPage,
      size: suppliersPageSize,
    }
    if (supplierSearch.trim()) {
      params.search = supplierSearch.trim()
    }
    if (supplierActiveFilter === 'ACTIVE') {
      params.active = true
    } else if (supplierActiveFilter === 'DISABLED') {
      params.active = false
    }
    return params
  }, [supplierSearch, supplierActiveFilter, suppliersPage])

  const suppliersQuery = useSuppliers(supplierQueryParams)
  const suppliersPageData = suppliersQuery.data
  const suppliers = suppliersPageData?.content ?? []
  const totalSuppliersCount = suppliersPageData?.totalElements ?? 0

  // --- Mutations ---
  const approveMutation = useApprovePurchaseOrder()
  const disableSupplierMutation = useDisableSupplier()
  const enableSupplierMutation = useEnableSupplier()

  // --- Dialog States ---
  const [isOrderFormOpen, setIsOrderFormOpen] = React.useState(false)
  const [orderToEdit, setOrderToEdit] =
    React.useState<PurchaseOrderDetailResponse | null>(null)

  const [selectedDetailExternalId, setSelectedDetailExternalId] =
    React.useState<string | null>(null)
  const [selectedCancelOrder, setSelectedCancelOrder] =
    React.useState<PurchaseOrderSummaryResponse | null>(null)
  const [selectedReceiveExternalId, setSelectedReceiveExternalId] =
    React.useState<string | null>(null)

  const [isSupplierFormOpen, setIsSupplierFormOpen] = React.useState(false)
  const [supplierToEdit, setSupplierToEdit] =
    React.useState<SupplierResponse | null>(null)

  // Edit order handler: fetches full detail first
  const orderDetailForEditQuery = usePurchaseOrderDetail(
    selectedDetailExternalId ?? '',
    false,
  )

  const handleCreateOrder = () => {
    setOrderToEdit(null)
    setIsOrderFormOpen(true)
  }

  const handleEditOrder = async (_order: PurchaseOrderSummaryResponse) => {
    try {
      const detail = await orderDetailForEditQuery.refetch()
      if (detail.data) {
        setOrderToEdit(detail.data)
        setIsOrderFormOpen(true)
      }
    } catch {
      // Fallback
    }
  }

  const handleApproveOrder = (order: PurchaseOrderSummaryResponse) => {
    if (window.confirm(t('purchases.orders.confirmApprove'))) {
      approveMutation.mutate(order.externalId)
    }
  }

  const handleReceiveOrder = (order: PurchaseOrderSummaryResponse) => {
    setSelectedReceiveExternalId(order.externalId)
  }

  const handleCancelOrder = (order: PurchaseOrderSummaryResponse) => {
    setSelectedCancelOrder(order)
  }

  const handleCreateSupplier = () => {
    setSupplierToEdit(null)
    setIsSupplierFormOpen(true)
  }

  const handleEditSupplier = (supplier: SupplierResponse) => {
    setSupplierToEdit(supplier)
    setIsSupplierFormOpen(true)
  }

  const handleToggleSupplierStatus = (supplier: SupplierResponse) => {
    if (supplier.active) {
      if (
        window.confirm(
          t('purchases.suppliers.confirmDisable', { name: supplier.name }),
        )
      ) {
        disableSupplierMutation.mutate(supplier.externalId)
      }
    } else {
      if (
        window.confirm(
          t('purchases.suppliers.confirmEnable', { name: supplier.name }),
        )
      ) {
        enableSupplierMutation.mutate(supplier.externalId)
      }
    }
  }

  const handleRefresh = () => {
    if (activeTab === 'orders') {
      ordersQuery.refetch()
    } else if (activeTab === 'suppliers') {
      suppliersQuery.refetch()
    }
  }

  return (
    <AppLayout activeModule="purchases">
      <div className="space-y-6">
        {/* Top Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2.5">
              <div className="h-10 w-10 rounded-xl bg-rose-600 text-white flex items-center justify-center shadow-xs">
                <ShoppingBag className="h-5 w-5" />
              </div>
              <div>
                <h1 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight">
                  {t('purchases.title')}
                </h1>
                <p className="text-xs text-slate-500">
                  {t('purchases.subtitle')}
                </p>
              </div>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex items-center gap-2.5">
            {activeTab === 'suppliers' && isAdmin && (
              <Button
                type="button"
                size="sm"
                className="text-xs bg-rose-600 hover:bg-rose-700 text-white shadow-xs font-semibold"
                onClick={handleCreateSupplier}
              >
                <Plus className="h-3.5 w-3.5 mr-1.5" />
                {t('purchases.suppliers.newSupplier')}
              </Button>
            )}

            {activeTab === 'orders' && (
              <Button
                type="button"
                size="sm"
                className="text-xs bg-rose-600 hover:bg-rose-700 text-white shadow-xs font-semibold"
                onClick={handleCreateOrder}
                disabled={!canCreateOrder}
              >
                <Plus className="h-3.5 w-3.5 mr-1.5" />
                {t('purchases.orders.newOrder')}
              </Button>
            )}
          </div>
        </div>

        {/* Aggregates Stats Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <Card className="border-slate-200 shadow-2xs">
            <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
              <CardTitle className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                {t('purchases.stats.totalOrders')}
              </CardTitle>
              <div className="h-7 w-7 rounded-lg bg-rose-50 text-rose-600 flex items-center justify-center">
                <ShoppingBag className="h-4 w-4" />
              </div>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-black text-slate-900">
                {totalOrdersCount}
              </div>
              <p className="text-[11px] text-slate-500 mt-0.5">
                {t('purchases.stats.totalOrdersDesc')}
              </p>
            </CardContent>
          </Card>

          <Card className="border-slate-200 shadow-2xs">
            <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
              <CardTitle className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                {t('purchases.stats.pendingOrders')}
              </CardTitle>
              <div className="h-7 w-7 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
                <Clock className="h-4 w-4" />
              </div>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-black text-slate-900">
                {pendingCount}
              </div>
              <p className="text-[11px] text-slate-500 mt-0.5">
                {t('purchases.stats.pendingOrdersDesc')}
              </p>
            </CardContent>
          </Card>

          <Card className="border-slate-200 shadow-2xs">
            <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
              <CardTitle className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                {t('purchases.stats.inReception')}
              </CardTitle>
              <div className="h-7 w-7 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center">
                <PackageCheck className="h-4 w-4" />
              </div>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-black text-slate-900">
                {inReceptionCount}
              </div>
              <p className="text-[11px] text-slate-500 mt-0.5">
                {t('purchases.stats.inReceptionDesc')}
              </p>
            </CardContent>
          </Card>

          <Card className="border-slate-200 shadow-2xs">
            <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
              <CardTitle className="text-xs font-bold text-slate-500 uppercase tracking-wider">
                {t('purchases.stats.totalSpent')}
              </CardTitle>
              <div className="h-7 w-7 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
                <DollarSign className="h-4 w-4" />
              </div>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-black text-slate-900 font-mono">
                ${totalPurchasedAmount.toFixed(2)}
              </div>
              <p className="text-[11px] text-slate-500 mt-0.5">
                {t('purchases.stats.totalSpentDesc')}
              </p>
            </CardContent>
          </Card>
        </div>

        {/* Tab Navigation */}
        <Tabs
          value={activeTab}
          onValueChange={(val) =>
            setActiveTab(val as 'orders' | 'suppliers' | 'costHistory')
          }
          className="space-y-4"
        >
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-200 pb-2">
            <TabsList className="bg-slate-100 p-1 rounded-xl">
              <TabsTrigger
                value="orders"
                className="text-xs font-semibold px-3.5 py-1.5 data-[state=active]:bg-white data-[state=active]:text-rose-900 data-[state=active]:shadow-2xs rounded-lg"
              >
                <List className="h-3.5 w-3.5 mr-1.5" />
                {t('purchases.tabs.orders')}
              </TabsTrigger>
              <TabsTrigger
                value="suppliers"
                className="text-xs font-semibold px-3.5 py-1.5 data-[state=active]:bg-white data-[state=active]:text-rose-900 data-[state=active]:shadow-2xs rounded-lg"
              >
                <Building2 className="h-3.5 w-3.5 mr-1.5" />
                {t('purchases.tabs.suppliers')}
              </TabsTrigger>
              <TabsTrigger
                value="costHistory"
                className="text-xs font-semibold px-3.5 py-1.5 data-[state=active]:bg-white data-[state=active]:text-rose-900 data-[state=active]:shadow-2xs rounded-lg"
              >
                <History className="h-3.5 w-3.5 mr-1.5" />
                {t('purchases.tabs.costHistory')}
              </TabsTrigger>
            </TabsList>

            {activeTab !== 'costHistory' && (
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
                    ordersQuery.isFetching || suppliersQuery.isFetching
                      ? 'animate-spin'
                      : ''
                  }`}
                />
                {t('common.refresh')}
              </Button>
            )}
          </div>

          {/* Tab 1: Orders */}
          <TabsContent value="orders" className="space-y-4">
            {/* Filter Bar */}
            <div className="p-3 bg-white border border-slate-200 rounded-xl shadow-2xs flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-wrap items-center gap-2.5">
                {/* Status Filter */}
                <div className="w-44">
                  <select
                    value={statusFilter}
                    onChange={(e: React.ChangeEvent<HTMLSelectElement>) => {
                      setStatusFilter(e.target.value)
                      setOrdersPage(0)
                    }}
                    className="w-full flex h-8 rounded-md border border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-rose-500"
                  >
                    <option value="ALL">{t('common.allStatus')}</option>
                    <option value="PENDING">
                      {t('purchases.status.PENDING')}
                    </option>
                    <option value="APPROVED">
                      {t('purchases.status.APPROVED')}
                    </option>
                    <option value="PARTIALLY_RECEIVED">
                      {t('purchases.status.PARTIALLY_RECEIVED')}
                    </option>
                    <option value="RECEIVED">
                      {t('purchases.status.RECEIVED')}
                    </option>
                    <option value="CANCELLED">
                      {t('purchases.status.CANCELLED')}
                    </option>
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
                      setOrdersPage(0)
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
                      setOrdersPage(0)
                    }}
                    className="text-xs h-8 bg-slate-50 border-slate-200 w-36"
                  />
                </div>
              </div>
            </div>

            {/* Orders Table */}
            <PurchaseOrderTable
              orders={orders}
              isLoading={ordersQuery.isLoading}
              totalElements={totalOrdersCount}
              page={ordersPage}
              size={ordersPageSize}
              onPageChange={setOrdersPage}
              onViewDetail={(order) =>
                setSelectedDetailExternalId(order.externalId)
              }
              onEdit={(order) => handleEditOrder(order)}
              onApprove={(order) => handleApproveOrder(order)}
              onReceive={(order) => handleReceiveOrder(order)}
              onCancel={(order) => handleCancelOrder(order)}
              canApprove={canApprove}
              canReceive={canReceive}
              canCancel={canCancel}
            />
          </TabsContent>

          {/* Tab 2: Suppliers */}
          <TabsContent value="suppliers" className="space-y-4">
            {/* Filter Bar */}
            <div className="p-3 bg-white border border-slate-200 rounded-xl shadow-2xs flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-wrap items-center gap-2.5">
                <div className="w-64">
                  <Input
                    type="text"
                    value={supplierSearch}
                    onChange={(e) => {
                      setSupplierSearch(e.target.value)
                      setSuppliersPage(0)
                    }}
                    placeholder={t('common.search')}
                    className="text-xs h-8 bg-slate-50 border-slate-200"
                  />
                </div>

                <div className="w-40">
                  <select
                    value={supplierActiveFilter}
                    onChange={(e: React.ChangeEvent<HTMLSelectElement>) => {
                      setSupplierActiveFilter(e.target.value)
                      setSuppliersPage(0)
                    }}
                    className="w-full flex h-8 rounded-md border border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-rose-500"
                  >
                    <option value="ALL">{t('common.all')}</option>
                    <option value="ACTIVE">{t('common.activeOnly')}</option>
                    <option value="DISABLED">{t('common.disabledOnly')}</option>
                  </select>
                </div>
              </div>
            </div>

            {/* Suppliers Table */}
            <SupplierTable
              suppliers={suppliers}
              isLoading={suppliersQuery.isLoading}
              totalElements={totalSuppliersCount}
              page={suppliersPage}
              size={suppliersPageSize}
              onPageChange={setSuppliersPage}
              onEdit={handleEditSupplier}
              onToggleStatus={handleToggleSupplierStatus}
              isAdmin={isAdmin}
            />
          </TabsContent>

          {/* Tab 3: Cost History */}
          <TabsContent value="costHistory" className="space-y-4">
            <CostHistoryView />
          </TabsContent>
        </Tabs>

        {/* Modal Dialogs */}
        <PurchaseOrderFormDialog
          orderToEdit={orderToEdit}
          open={isOrderFormOpen}
          onOpenChange={setIsOrderFormOpen}
          onSuccess={(created) => {
            setSelectedDetailExternalId(created.externalId)
            ordersQuery.refetch()
          }}
        />

        <PurchaseOrderDetailDialog
          orderExternalId={selectedDetailExternalId}
          open={Boolean(selectedDetailExternalId)}
          onOpenChange={(open) => {
            if (!open) setSelectedDetailExternalId(null)
          }}
          onEdit={(extId) => {
            const found = orders.find((o) => o.externalId === extId)
            if (found) handleEditOrder(found)
          }}
          onApprove={(extId) => {
            const found = orders.find((o) => o.externalId === extId)
            if (found) handleApproveOrder(found)
          }}
          onReceive={(extId) => setSelectedReceiveExternalId(extId)}
          onCancel={(extId) => {
            const found = orders.find((o) => o.externalId === extId)
            if (found) setSelectedCancelOrder(found)
          }}
          canApprove={canApprove}
          canReceive={canReceive}
          canCancel={canCancel}
        />

        <PurchaseOrderCancelDialog
          order={selectedCancelOrder}
          open={Boolean(selectedCancelOrder)}
          onOpenChange={(open) => {
            if (!open) setSelectedCancelOrder(null)
          }}
          onSuccess={() => {
            ordersQuery.refetch()
          }}
        />

        <PurchaseReceptionDialog
          orderExternalId={selectedReceiveExternalId}
          open={Boolean(selectedReceiveExternalId)}
          onOpenChange={(open) => {
            if (!open) setSelectedReceiveExternalId(null)
          }}
          onSuccess={() => {
            ordersQuery.refetch()
          }}
        />

        <SupplierFormDialog
          supplierToEdit={supplierToEdit}
          open={isSupplierFormOpen}
          onOpenChange={setIsSupplierFormOpen}
          onSuccess={() => {
            suppliersQuery.refetch()
          }}
        />
      </div>
    </AppLayout>
  )
}
