import * as React from 'react'
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs.tsx'
import { AppLayout } from '@/components/layout/AppLayout.tsx'
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { useBranches } from '@/features/iam/hooks/use-branches.ts'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { SalesTrendView } from './SalesTrendView.tsx'
import { RotationView } from './RotationView.tsx'
import { TransfersImpactView } from './TransfersImpactView.tsx'
import { ReplenishmentPanelView } from './ReplenishmentPanelView.tsx'
import { CorporateBoardView } from './CorporateBoardView.tsx'
import {
  ArrowLeftRight,
  BarChart2,
  Building2,
  Globe2,
  PieChart,
  ShieldAlert,
} from 'lucide-react'

interface AnalyticsDashboardProps {
  onLogout?: (() => void) | undefined
  defaultTab?: string
}

export function AnalyticsDashboard({
  onLogout,
  defaultTab = 'salesTrend',
}: AnalyticsDashboardProps) {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = React.useState<string>(defaultTab)

  const sessionQuery = useSession()
  const session = sessionQuery.data
  const role = session?.role
  const isCorporateAdmin = role === 'ADMIN'

  // Branches query for Corporate Admin branch selector
  const branchesQuery = useBranches(
    { active: true, size: 100 },
    isCorporateAdmin,
  )
  const branches = branchesQuery.data?.content ?? []

  // Selected branch for Corporate Admin
  const [selectedBranchId, setSelectedBranchId] = React.useState<string>('')

  // Auto-select first active branch for corporate admin if none selected
  React.useEffect(() => {
    if (isCorporateAdmin && branches.length > 0 && !selectedBranchId) {
      const firstBranch = branches[0]
      if (firstBranch) {
        setSelectedBranchId(firstBranch.externalId)
      }
    }
  }, [isCorporateAdmin, branches, selectedBranchId])

  // Get selected branch object to display readable name/code (NEVER raw UUID)
  const currentSelectedBranch = React.useMemo(() => {
    if (isCorporateAdmin) {
      return branches.find((b) => b.externalId === selectedBranchId) ?? null
    }
    return null
  }, [isCorporateAdmin, branches, selectedBranchId])

  const branchNameToDisplay = isCorporateAdmin
    ? currentSelectedBranch
      ? `${currentSelectedBranch.name} (${currentSelectedBranch.code})`
      : t('analytics.selectBranchPrompt')
    : session?.branchName
      ? session.branchCode
        ? `${session.branchName} (${session.branchCode})`
        : session.branchName
      : t('iam.assignedBranch')

  return (
    <AppLayout activeModule="analytics" onLogout={onLogout}>
      <div className="space-y-6">
        {/* Module Title Banner & Branch Selector for Admin */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 pb-4 border-b border-slate-200">
          <div>
            <h1 className="text-2xl font-black tracking-tight text-slate-900 flex items-center gap-2.5">
              <BarChart2 className="h-7 w-7 text-indigo-600" />
              {t('analytics.title')}
            </h1>
            <p className="text-xs text-slate-600 mt-1 max-w-2xl">
              {t('analytics.subtitle')}
            </p>
          </div>

          {/* Corporate Admin Branch Selector (Only for branch-scoped tabs) */}
          {isCorporateAdmin && activeTab !== 'corporateBoard' && (
            <div className="flex items-center gap-2 p-2.5 bg-white rounded-xl border border-slate-200 shadow-2xs">
              <Building2 className="h-4 w-4 text-indigo-600 shrink-0" />
              <div className="flex flex-col">
                <label
                  htmlFor="admin-branch-select"
                  className="text-[10px] font-bold uppercase tracking-wider text-slate-400"
                >
                  {t('analytics.branchScopeNotice')}
                </label>
                <select
                  id="admin-branch-select"
                  value={selectedBranchId}
                  onChange={(e) => setSelectedBranchId(e.target.value)}
                  className="h-7 rounded-md border-0 bg-transparent text-xs font-bold text-slate-900 focus:ring-0 focus:outline-none p-0 cursor-pointer"
                >
                  {branches.map((b) => (
                    <option key={b.externalId} value={b.externalId}>
                      {b.name} ({b.code})
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}

          {!isCorporateAdmin && (
            <div className="flex items-center gap-2 px-3 py-2 bg-slate-100/80 rounded-xl border border-slate-200/80 text-xs font-semibold text-slate-700 self-start md:self-auto">
              <Building2 className="h-4 w-4 text-slate-500" />
              <span>
                {t('analytics.branchScopeNotice')}:{' '}
                <span className="font-bold text-slate-900">
                  {branchNameToDisplay}
                </span>
              </span>
            </div>
          )}
        </div>

        {/* Dashboard Tabs */}
        <Tabs
          value={activeTab}
          onValueChange={setActiveTab}
          className="space-y-6"
        >
          <TabsList className="bg-slate-200/70 p-1 rounded-xl h-auto flex flex-wrap gap-1">
            <TabsTrigger
              value="salesTrend"
              className="rounded-lg text-xs font-bold px-3.5 py-2 data-[state=active]:bg-white data-[state=active]:text-slate-900 data-[state=active]:shadow-2xs transition-all flex items-center gap-2"
            >
              <BarChart2 className="h-4 w-4 text-indigo-600" />
              {t('analytics.tabs.salesTrend')}
            </TabsTrigger>

            <TabsTrigger
              value="rotation"
              className="rounded-lg text-xs font-bold px-3.5 py-2 data-[state=active]:bg-white data-[state=active]:text-slate-900 data-[state=active]:shadow-2xs transition-all flex items-center gap-2"
            >
              <PieChart className="h-4 w-4 text-indigo-600" />
              {t('analytics.tabs.rotation')}
            </TabsTrigger>

            <TabsTrigger
              value="transfers"
              className="rounded-lg text-xs font-bold px-3.5 py-2 data-[state=active]:bg-white data-[state=active]:text-slate-900 data-[state=active]:shadow-2xs transition-all flex items-center gap-2"
            >
              <ArrowLeftRight className="h-4 w-4 text-cyan-600" />
              {t('analytics.tabs.transfers')}
            </TabsTrigger>

            <TabsTrigger
              value="replenishment"
              className="rounded-lg text-xs font-bold px-3.5 py-2 data-[state=active]:bg-white data-[state=active]:text-slate-900 data-[state=active]:shadow-2xs transition-all flex items-center gap-2"
            >
              <ShieldAlert className="h-4 w-4 text-rose-600" />
              {t('analytics.tabs.replenishment')}
            </TabsTrigger>

            {isCorporateAdmin && (
              <TabsTrigger
                value="corporateBoard"
                className="rounded-lg text-xs font-bold px-3.5 py-2 data-[state=active]:bg-white data-[state=active]:text-slate-900 data-[state=active]:shadow-2xs transition-all flex items-center gap-2"
              >
                <Globe2 className="h-4 w-4 text-emerald-600" />
                {t('analytics.tabs.corporateBoard')}
              </TabsTrigger>
            )}
          </TabsList>

          {/* 1. Sales Trend Tab */}
          <TabsContent
            value="salesTrend"
            className="mt-0 focus-visible:outline-none"
          >
            <SalesTrendView
              branchExternalId={isCorporateAdmin ? selectedBranchId : undefined}
            />
          </TabsContent>

          {/* 2. Rotation / Pareto ABC Tab */}
          <TabsContent
            value="rotation"
            className="mt-0 focus-visible:outline-none"
          >
            <RotationView
              branchExternalId={isCorporateAdmin ? selectedBranchId : undefined}
            />
          </TabsContent>

          {/* 3. Transfers Activity & Stock Impact Tab */}
          <TabsContent
            value="transfers"
            className="mt-0 focus-visible:outline-none"
          >
            <TransfersImpactView
              branchExternalId={isCorporateAdmin ? selectedBranchId : undefined}
            />
          </TabsContent>

          {/* 4. Critical Replenishment Panel Tab */}
          <TabsContent
            value="replenishment"
            className="mt-0 focus-visible:outline-none"
          >
            <ReplenishmentPanelView
              branchExternalId={isCorporateAdmin ? selectedBranchId : undefined}
            />
          </TabsContent>

          {/* 5. Corporate Board Tab (Admin only) */}
          {isCorporateAdmin && (
            <TabsContent
              value="corporateBoard"
              className="mt-0 focus-visible:outline-none"
            >
              <CorporateBoardView />
            </TabsContent>
          )}
        </Tabs>
      </div>
    </AppLayout>
  )
}
