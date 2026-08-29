import * as React from 'react'
import { Card, CardContent } from '@/components/ui/card.tsx'
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs.tsx'
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { AppLayout } from '@/components/layout/AppLayout.tsx'
import { AlertCenter } from '@/features/notifications/components/AlertCenter.tsx'
import { useAlerts } from '@/features/notifications/hooks/use-alerts.ts'
import { useProducts } from '@/features/catalog/hooks/use-products.ts'
import { useStock } from '../hooks/use-inventory.ts'
import { KardexTable } from './KardexTable.tsx'
import { StockTable } from './StockTable.tsx'
import {
  AlertTriangle,
  Boxes,
  DollarSign,
  Globe,
  History,
  MapPin,
  ShieldAlert,
} from 'lucide-react'

interface InventoryDashboardProps {
  onLogout?: (() => void) | undefined
  defaultTab?: string
}

export function InventoryDashboard({
  onLogout,
  defaultTab = 'stock',
}: InventoryDashboardProps) {
  const [activeTab, setActiveTab] = React.useState<string>(defaultTab)
  const [selectedKardexProductId, setSelectedKardexProductId] = React.useState<string>('')

  const sessionQuery = useSession()
  const session = sessionQuery.data

  const role = session?.role ?? 'OPERATOR'
  const isAdmin = role === 'ADMIN'
  const isOperator = role === 'OPERATOR'
  const canViewKardexAndAlerts = !isOperator // ADMIN and BRANCH_MANAGER per contract §5

  // Metrics Queries - stock is branch-scoped, so corporate ADMIN uses catalog & network scope
  const productsQuery = useProducts({ page: 0, size: 100 }, isAdmin)
  const allStockQuery = useStock({ page: 0, size: 100 }, !isAdmin)
  const criticalStockQuery = useStock({ page: 0, size: 1, belowThreshold: true }, !isAdmin)
  const alertsQuery = useAlerts({ resolved: false, page: 0, size: 100 })

  const stockItems = allStockQuery.data?.content ?? []
  const totalStockItems = allStockQuery.data?.totalElements ?? 0
  const criticalCount = criticalStockQuery.data?.totalElements ?? 0
  const unresolvedAlertsCount = alertsQuery.data?.totalElements ?? 0

  const totalValuation = React.useMemo(() => {
    return stockItems.reduce((acc, item) => {
      return acc + item.currentStock * item.averageCost
    }, 0)
  }, [stockItems])

  const handleViewKardexForProduct = (productExternalId: string) => {
    setSelectedKardexProductId(productExternalId)
    setActiveTab('kardex')
  }

  const assignedBranchName = session?.branchName
    ? session.branchCode
      ? `${session.branchName} (${session.branchCode})`
      : session.branchName
    : session?.branchId
      ? 'Assigned Branch'
      : null

  return (
    <AppLayout
      activeModule={activeTab === 'alerts' ? 'notifications' : 'inventory'}
      onLogout={onLogout}
    >
      <div className="space-y-6">
        {/* Module Title Banner */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 pb-2 border-b border-slate-200">
          <div>
            <h1 className="text-2xl font-black tracking-tight text-slate-900">
              Inventory & Stock Management
            </h1>
            <p className="text-xs text-slate-600 mt-1">
              Real-time branch inventory tracking, immutable Kardex ledger, and corporate network availability.
            </p>
          </div>
        </div>

        {/* KPI Overview Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <Card className="bg-white border-slate-200 shadow-2xs">
            <CardContent className="p-4 flex items-center justify-between">
              <div>
                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  {isAdmin ? 'Catalog Products' : 'Total Products in Stock'}
                </p>
                <div className="flex items-baseline gap-2 mt-1">
                  <span className="text-2xl font-bold text-slate-900">
                    {isAdmin
                      ? productsQuery.isLoading
                        ? '...'
                        : (productsQuery.data?.totalElements ?? 0)
                      : allStockQuery.isLoading
                        ? '...'
                        : totalStockItems}
                  </span>
                  <span className="text-xs text-slate-500">
                    {isAdmin ? 'Master Catalog' : 'Registered Lines'}
                  </span>
                </div>
              </div>
              <div className="h-9 w-9 rounded bg-amber-50 text-amber-700 flex items-center justify-center border border-amber-200">
                <Boxes className="h-4 w-4" />
              </div>
            </CardContent>
          </Card>

          <Card className="bg-white border-slate-200 shadow-2xs">
            <CardContent className="p-4 flex items-center justify-between">
              <div>
                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  {isAdmin ? 'Active System Alerts' : 'Critical / Low Stock'}
                </p>
                <div className="flex items-baseline gap-2 mt-1">
                  <span
                    className={`text-2xl font-bold ${
                      (isAdmin ? unresolvedAlertsCount : criticalCount) > 0
                        ? 'text-rose-600'
                        : 'text-slate-900'
                    }`}
                  >
                    {isAdmin
                      ? alertsQuery.isLoading
                        ? '...'
                        : unresolvedAlertsCount
                      : criticalStockQuery.isLoading
                        ? '...'
                        : criticalCount}
                  </span>
                  <span className="text-xs text-slate-500">
                    {isAdmin ? 'Network Requiring Action' : 'Below Safety Threshold'}
                  </span>
                </div>
              </div>
              <div
                className={`h-9 w-9 rounded flex items-center justify-center border ${
                  (isAdmin ? unresolvedAlertsCount : criticalCount) > 0
                    ? 'bg-rose-50 text-rose-700 border-rose-200'
                    : 'bg-slate-50 text-slate-600 border-slate-200'
                }`}
              >
                <AlertTriangle className="h-4 w-4" />
              </div>
            </CardContent>
          </Card>

          <Card className="bg-white border-slate-200 shadow-2xs">
            <CardContent className="p-4 flex items-center justify-between">
              <div>
                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  {isAdmin ? 'Scope' : 'Local Stock Valuation'}
                </p>
                <div className="flex items-baseline gap-2 mt-1">
                  {isAdmin ? (
                    <span className="text-base font-bold text-slate-900 truncate">
                      Corporate Multi-Branch
                    </span>
                  ) : (
                    <span className="text-2xl font-bold text-slate-900 font-mono">
                      $
                      {totalValuation.toLocaleString(undefined, {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2,
                      })}
                    </span>
                  )}
                </div>
                <p className="text-[10px] text-slate-500 mt-0.5">
                  {isAdmin
                    ? 'Global network inventory overview'
                    : 'Based on weighted average cost (CPP)'}
                </p>
              </div>
              <div className="h-9 w-9 rounded bg-emerald-50 text-emerald-700 flex items-center justify-center border border-emerald-200">
                {isAdmin ? <Globe className="h-4 w-4 text-emerald-700" /> : <DollarSign className="h-4 w-4" />}
              </div>
            </CardContent>
          </Card>

          {canViewKardexAndAlerts ? (
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardContent className="p-4 flex items-center justify-between">
                <div>
                  <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Operational Alerts
                  </p>
                  <div className="flex items-baseline gap-2 mt-1">
                    <span
                      className={`text-2xl font-bold ${
                        unresolvedAlertsCount > 0 ? 'text-amber-600' : 'text-slate-900'
                      }`}
                    >
                      {alertsQuery.isLoading ? '...' : unresolvedAlertsCount}
                    </span>
                    <span className="text-xs text-slate-500">Requiring Action</span>
                  </div>
                </div>
                <div
                  className={`h-9 w-9 rounded flex items-center justify-center border ${
                    unresolvedAlertsCount > 0
                      ? 'bg-amber-50 text-amber-700 border-amber-200'
                      : 'bg-slate-50 text-slate-600 border-slate-200'
                  }`}
                >
                  <ShieldAlert className="h-4 w-4" />
                </div>
              </CardContent>
            </Card>
          ) : (
            <Card className="bg-white border-slate-200 shadow-2xs">
              <CardContent className="p-4 flex items-center justify-between">
                <div>
                  <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    Station Scope
                  </p>
                  <div className="flex items-baseline gap-2 mt-1">
                    <span className="text-sm font-bold text-slate-900 truncate max-w-[170px]">
                      {assignedBranchName ?? 'Assigned Station'}
                    </span>
                  </div>
                  <p className="text-[10px] text-emerald-700 mt-0.5 font-medium">
                    Operational Write-Offs Authorized
                  </p>
                </div>
                <div className="h-9 w-9 rounded bg-slate-100 text-slate-700 flex items-center justify-center border border-slate-200">
                  <MapPin className="h-4 w-4" />
                </div>
              </CardContent>
            </Card>
          )}
        </div>

        {/* Tabbed Main Interface */}
        <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
          <TabsList className="bg-white border border-slate-200 p-0.5 rounded-md h-auto">
            <TabsTrigger
              value="stock"
              className="flex items-center space-x-2 px-3.5 py-1.5 text-xs font-semibold rounded data-[state=active]:bg-amber-900 data-[state=active]:text-white transition-colors"
            >
              <Boxes className="h-3.5 w-3.5" />
              <span>Stock Balances</span>
            </TabsTrigger>

            {canViewKardexAndAlerts && (
              <>
                <TabsTrigger
                  value="kardex"
                  className="flex items-center space-x-2 px-3.5 py-1.5 text-xs font-semibold rounded data-[state=active]:bg-amber-900 data-[state=active]:text-white transition-colors"
                >
                  <History className="h-3.5 w-3.5" />
                  <span>Kardex Ledger</span>
                </TabsTrigger>

                <TabsTrigger
                  value="alerts"
                  className="flex items-center space-x-2 px-3.5 py-1.5 text-xs font-semibold rounded data-[state=active]:bg-amber-900 data-[state=active]:text-white transition-colors"
                >
                  <ShieldAlert className="h-3.5 w-3.5" />
                  <span>Alerts & Notifications</span>
                  {unresolvedAlertsCount > 0 && (
                    <span className="bg-rose-500 text-white text-[10px] px-1 rounded-full font-mono">
                      {unresolvedAlertsCount}
                    </span>
                  )}
                </TabsTrigger>
              </>
            )}
          </TabsList>

          <TabsContent value="stock" className="focus-visible:outline-none">
            <StockTable
              currentActorRole={role}
              onViewKardex={canViewKardexAndAlerts ? handleViewKardexForProduct : undefined}
            />
          </TabsContent>

          {canViewKardexAndAlerts && (
            <>
              <TabsContent value="kardex" className="focus-visible:outline-none">
                <KardexTable initialProductExternalId={selectedKardexProductId} />
              </TabsContent>

              <TabsContent value="alerts" className="focus-visible:outline-none">
                <AlertCenter currentActorRole={role} />
              </TabsContent>
            </>
          )}
        </Tabs>
      </div>
    </AppLayout>
  )
}
