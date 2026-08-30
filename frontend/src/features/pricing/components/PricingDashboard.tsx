import * as React from 'react'
import {
  BadgePercent,
  Calculator,
  List,
  Plus,
  RefreshCw,
  Tag,
} from 'lucide-react'
import { AppLayout } from '@/components/layout/AppLayout.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { Permissions } from '@/lib/permissions.ts'
import {
  usePriceList,
  usePriceLists,
  usePrices,
} from '../hooks/use-pricing.ts'
import type {
  PriceListResponse,
  PriceQueryParams,
  PriceResponse,
} from '../types/index.ts'
import { PriceListTable } from './PriceListTable.tsx'
import { PriceListFormDialog } from './PriceListFormDialog.tsx'
import { PriceTable } from './PriceTable.tsx'
import { SetPriceDialog } from './SetPriceDialog.tsx'
import { ClosePriceDialog } from './ClosePriceDialog.tsx'
import { QuoteSimulator } from './QuoteSimulator.tsx'

export function PricingDashboard() {
  const { t } = useTranslation()
  const sessionQuery = useSession()
  const session = sessionQuery.data
  const role = session?.role

  const isAdmin = Permissions.canManagePricing(role)

  const [activeTab, setActiveTab] = React.useState<'lists' | 'rates' | 'quotes'>(
    'lists',
  )

  // Price lists pagination
  const [listsPage, setListsPage] = React.useState(0)
  const listsPageSize = 10
  const priceListsQuery = usePriceLists({
    page: listsPage,
    size: listsPageSize,
  })
  const priceLists = priceListsQuery.data?.content ?? []

  // Price List Selection for Tab 2 (Rates)
  const [selectedListId, setSelectedListId] = React.useState<string>('')
  const [currentOnly, setCurrentOnly] = React.useState<boolean>(false)
  const [pricesPage, setPricesPage] = React.useState(0)
  const pricesPageSize = 15

  // Automatically select first price list if none selected
  React.useEffect(() => {
    if (!selectedListId && priceLists.length > 0) {
      const first = priceLists[0]
      if (first) setSelectedListId(first.externalId)
    }
  }, [priceLists, selectedListId])

  const selectedListQuery = usePriceList(
    selectedListId,
    Boolean(selectedListId),
  )
  const selectedPriceList = selectedListQuery.data ?? null

  const priceQueryParams = React.useMemo<PriceQueryParams>(() => {
    return {
      currentOnly: currentOnly ? true : undefined,
      page: pricesPage,
      size: pricesPageSize,
    }
  }, [currentOnly, pricesPage])

  const pricesQuery = usePrices(
    selectedListId,
    priceQueryParams,
    Boolean(selectedListId),
  )
  const prices = pricesQuery.data?.content ?? []

  // Dialog states
  const [isListFormOpen, setIsListFormOpen] = React.useState(false)
  const [priceListToEdit, setPriceListToEdit] =
    React.useState<PriceListResponse | null>(null)

  const [isSetPriceOpen, setIsSetPriceOpen] = React.useState(false)
  const [priceToClose, setPriceToClose] = React.useState<PriceResponse | null>(
    null,
  )

  const handleEditList = (list: PriceListResponse) => {
    setPriceListToEdit(list)
    setIsListFormOpen(true)
  }

  const handleCreateList = () => {
    setPriceListToEdit(null)
    setIsListFormOpen(true)
  }

  const handleManagePrices = (list: PriceListResponse) => {
    setSelectedListId(list.externalId)
    setActiveTab('rates')
  }

  const handleRefresh = () => {
    if (activeTab === 'lists') {
      priceListsQuery.refetch()
    } else if (activeTab === 'rates') {
      pricesQuery.refetch()
    }
  }

  return (
    <AppLayout activeModule="pricing">
      <div className="space-y-6">
        {/* Top Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2.5">
              <div className="h-10 w-10 rounded-xl bg-violet-600 text-white flex items-center justify-center shadow-xs">
                <BadgePercent className="h-5 w-5" />
              </div>
              <div>
                <h1 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight">
                  {t('pricing.title')}
                </h1>
                <p className="text-xs text-slate-500">{t('pricing.subtitle')}</p>
              </div>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex items-center gap-2.5">
            {isAdmin && activeTab === 'lists' && (
              <Button
                type="button"
                size="sm"
                className="text-xs bg-violet-600 hover:bg-violet-700 text-white shadow-xs font-semibold"
                onClick={handleCreateList}
              >
                <Plus className="h-3.5 w-3.5 mr-1.5" />
                {t('pricing.priceLists.create')}
              </Button>
            )}

            {isAdmin && activeTab === 'rates' && selectedPriceList && (
              <Button
                type="button"
                size="sm"
                className="text-xs bg-violet-600 hover:bg-violet-700 text-white shadow-xs font-semibold"
                onClick={() => setIsSetPriceOpen(true)}
              >
                <Plus className="h-3.5 w-3.5 mr-1.5" />
                {t('pricing.rates.setPrice')}
              </Button>
            )}
          </div>
        </div>

        {/* Tabs Navigation */}
        <Tabs
          value={activeTab}
          onValueChange={(val) =>
            setActiveTab(val as 'lists' | 'rates' | 'quotes')
          }
          className="space-y-4"
        >
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-200 pb-2">
            <TabsList className="bg-slate-100 p-1 rounded-xl">
              <TabsTrigger
                value="lists"
                className="text-xs font-semibold px-3.5 py-1.5 data-[state=active]:bg-white data-[state=active]:text-violet-900 data-[state=active]:shadow-2xs rounded-lg"
              >
                <List className="h-3.5 w-3.5 mr-1.5" />
                {t('pricing.tabs.priceLists')}
              </TabsTrigger>
              <TabsTrigger
                value="rates"
                className="text-xs font-semibold px-3.5 py-1.5 data-[state=active]:bg-white data-[state=active]:text-violet-900 data-[state=active]:shadow-2xs rounded-lg"
              >
                <Tag className="h-3.5 w-3.5 mr-1.5" />
                {t('pricing.tabs.rates')}
              </TabsTrigger>
              <TabsTrigger
                value="quotes"
                className="text-xs font-semibold px-3.5 py-1.5 data-[state=active]:bg-white data-[state=active]:text-violet-900 data-[state=active]:shadow-2xs rounded-lg"
              >
                <Calculator className="h-3.5 w-3.5 mr-1.5" />
                {t('pricing.tabs.quoteSimulator')}
              </TabsTrigger>
            </TabsList>

            {activeTab !== 'quotes' && (
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
                    priceListsQuery.isFetching || pricesQuery.isFetching
                      ? 'animate-spin'
                      : ''
                  }`}
                />
                {t('common.refresh')}
              </Button>
            )}
          </div>

          {/* Tab 1: Price Lists */}
          <TabsContent value="lists" className="space-y-4">
            <PriceListTable
              priceLists={priceLists}
              isLoading={priceListsQuery.isLoading}
              totalElements={priceListsQuery.data?.totalElements ?? 0}
              page={listsPage}
              size={listsPageSize}
              onPageChange={setListsPage}
              onEdit={handleEditList}
              onDeactivate={() => {
                // Triggered from table action
              }}
              onManagePrices={handleManagePrices}
              isAdmin={isAdmin}
            />
          </TabsContent>

          {/* Tab 2: Prices by List */}
          <TabsContent value="rates" className="space-y-4">
            <div className="p-3.5 bg-white border border-slate-200 rounded-xl shadow-2xs flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-wrap items-center gap-3">
                <div className="w-64 space-y-1">
                  <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                    {t('pricing.rates.selectList')}
                  </span>
                  <select
                    value={selectedListId}
                    onChange={(e: React.ChangeEvent<HTMLSelectElement>) => {
                      setSelectedListId(e.target.value)
                      setPricesPage(0)
                    }}
                    className="w-full flex h-8 rounded-md border border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-900 focus:outline-none focus:ring-1 focus:ring-violet-500"
                  >
                    {priceLists.map((pl) => (
                      <option key={pl.externalId} value={pl.externalId}>
                        {pl.name} ({pl.code}) - {t('pricing.priceLists.maxDiscount')}: {pl.maxDiscountPercent}%
                      </option>
                    ))}
                  </select>
                </div>

                <div className="flex items-center gap-2 pt-4">
                  <label className="flex items-center gap-2 text-xs font-semibold text-slate-700 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={currentOnly}
                      onChange={(e) => {
                        setCurrentOnly(e.target.checked)
                        setPricesPage(0)
                      }}
                      className="rounded border-slate-300 text-violet-600 focus:ring-violet-500"
                    />
                    <span>{t('pricing.rates.currentOnly')}</span>
                  </label>
                </div>
              </div>

              {selectedPriceList && (
                <div className="text-xs text-right space-y-0.5">
                  <span className="text-slate-500">{t('pricing.priceLists.maxDiscount')}:</span>
                  <div className="font-mono font-bold text-amber-800 text-sm">
                    {selectedPriceList.maxDiscountPercent}%
                  </div>
                </div>
              )}
            </div>

            <PriceTable
              prices={prices}
              isLoading={pricesQuery.isLoading}
              totalElements={pricesQuery.data?.totalElements ?? 0}
              page={pricesPage}
              size={pricesPageSize}
              onPageChange={setPricesPage}
              onClosePrice={(price) => setPriceToClose(price)}
              isAdmin={isAdmin}
            />
          </TabsContent>

          {/* Tab 3: Quote Simulator */}
          <TabsContent value="quotes">
            <QuoteSimulator />
          </TabsContent>
        </Tabs>

        {/* Dialogs */}
        <PriceListFormDialog
          priceListToEdit={priceListToEdit}
          open={isListFormOpen}
          onOpenChange={setIsListFormOpen}
          onSuccess={() => {
            priceListsQuery.refetch()
          }}
        />

        <SetPriceDialog
          priceList={selectedPriceList}
          open={isSetPriceOpen}
          onOpenChange={setIsSetPriceOpen}
          onSuccess={() => {
            pricesQuery.refetch()
          }}
        />

        <ClosePriceDialog
          price={priceToClose}
          open={Boolean(priceToClose)}
          onOpenChange={(open) => {
            if (!open) setPriceToClose(null)
          }}
          onSuccess={() => {
            pricesQuery.refetch()
          }}
        />
      </div>
    </AppLayout>
  )
}
