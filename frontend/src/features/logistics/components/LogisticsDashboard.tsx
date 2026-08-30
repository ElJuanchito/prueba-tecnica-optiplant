import * as React from 'react'
import { AppLayout } from '@/components/layout/AppLayout.tsx'
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from '@/components/ui/tabs.tsx'
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { Permissions } from '@/lib/permissions.ts'
import { ActiveTransfersMonitorTable } from './ActiveTransfersMonitorTable.tsx'
import { ComplianceReportView } from './ComplianceReportView.tsx'
import { RouteTable } from './RouteTable.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import { Award, Radio, Route as RouteIcon } from 'lucide-react'

export function LogisticsDashboard() {
  const { t } = useTranslation()
  const sessionQuery = useSession()
  const session = sessionQuery.data
  const role = session?.role ?? 'OPERATOR'
  const canManageRoutes = Permissions.canManageRoutes(role)

  const [activeTab, setActiveTab] = React.useState<string>('monitor')

  return (
    <AppLayout activeModule="logistics">
      <div className="space-y-6">
        {/* Top Header */}
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-white p-6 rounded-2xl border border-slate-200 shadow-2xs">
          <div className="flex items-center gap-2.5">
            <div className="h-10 w-10 rounded-xl bg-emerald-600 text-white flex items-center justify-center shadow-xs">
              <RouteIcon className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-xl font-black tracking-tight text-slate-900">
                {t('logistics.title')}
              </h1>
              <p className="text-xs text-slate-500 max-w-xl">
                {t('logistics.subtitle')}
              </p>
            </div>
          </div>
        </div>

        {/* Tab Navigation */}
        <Tabs
          value={activeTab}
          onValueChange={setActiveTab}
          className="w-full space-y-4"
        >
          <TabsList className="bg-white p-1 rounded-xl border border-slate-200 shadow-2xs flex flex-wrap h-auto gap-1">
            <TabsTrigger
              value="monitor"
              className="text-xs font-semibold gap-1.5 py-2 px-3.5 data-[state=active]:bg-emerald-50 data-[state=active]:text-emerald-900 data-[state=active]:border-emerald-200"
            >
              <Radio className="h-3.5 w-3.5" />
              {t('logistics.tabs.monitor')}
            </TabsTrigger>

            <TabsTrigger
              value="compliance"
              className="text-xs font-semibold gap-1.5 py-2 px-3.5 data-[state=active]:bg-emerald-50 data-[state=active]:text-emerald-900 data-[state=active]:border-emerald-200"
            >
              <Award className="h-3.5 w-3.5" />
              {t('logistics.tabs.compliance')}
            </TabsTrigger>

            {canManageRoutes && (
              <TabsTrigger
                value="routes"
                className="text-xs font-semibold gap-1.5 py-2 px-3.5 data-[state=active]:bg-emerald-50 data-[state=active]:text-emerald-900 data-[state=active]:border-emerald-200"
              >
                <RouteIcon className="h-3.5 w-3.5" />
                {t('logistics.tabs.routes')}
              </TabsTrigger>
            )}
          </TabsList>

          {/* Active Transfers Monitor Tab */}
          <TabsContent value="monitor" className="space-y-4 outline-none">
            <ActiveTransfersMonitorTable />
          </TabsContent>

          {/* Compliance Report Tab */}
          <TabsContent value="compliance" className="space-y-4 outline-none">
            <ComplianceReportView />
          </TabsContent>

          {/* Routes Management Tab */}
          {canManageRoutes && (
            <TabsContent value="routes" className="space-y-4 outline-none">
              <RouteTable />
            </TabsContent>
          )}
        </Tabs>
      </div>
    </AppLayout>
  )
}
