import * as React from 'react'
import { AppLayout } from '@/components/layout/AppLayout.tsx'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert.tsx'
import { Button } from '@/components/ui/button.tsx'
import { Card, CardContent } from '@/components/ui/card.tsx'
import { useSession } from '@/features/iam/hooks/use-auth.ts'
import { Permissions } from '@/lib/permissions.ts'
import { useTransfers } from '../hooks/use-transfers.ts'
import { TransferRequestDialog } from './TransferRequestDialog.tsx'
import { TransferTable } from './TransferTable.tsx'
import { useTranslation } from '@/lib/i18n/i18n-context.tsx'
import {
  AlertTriangle,
  ArrowLeftRight,
  CheckCircle2,
  Clock,
  Info,
  PackageCheck,
  Plus,
  Truck,
  XCircle,
} from 'lucide-react'

export function TransfersDashboard() {
  const { t } = useTranslation()
  const sessionQuery = useSession()
  const session = sessionQuery.data
  const role = session?.role ?? 'OPERATOR'
  const hasBranch = Boolean(session?.branchId)
  const isCorporateAdmin = role === 'ADMIN' && !hasBranch

  const [isRequestOpen, setIsRequestOpen] = React.useState(false)

  // Fetch count metrics
  const requestedQuery = useTransfers({ status: 'REQUESTED', size: 1 })
  const inPrepQuery = useTransfers({ status: 'IN_PREPARATION', size: 1 })
  const inTransitQuery = useTransfers({ status: 'IN_TRANSIT', size: 1 })
  const receivedQuery = useTransfers({ status: 'RECEIVED', size: 1 })
  const discrepancyQuery = useTransfers({
    status: 'RECEIVED_WITH_DISCREPANCY',
    size: 1,
  })
  const cancelledQuery = useTransfers({ status: 'CANCELLED', size: 1 })

  return (
    <AppLayout activeModule="transfers">
      <div className="space-y-6">
        {/* Top Header */}
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-white p-6 rounded-2xl border border-slate-200 shadow-2xs">
          <div className="space-y-1">
            <div className="flex items-center gap-2.5">
              <div className="h-10 w-10 rounded-xl bg-cyan-600 text-white flex items-center justify-center shadow-xs">
                <ArrowLeftRight className="h-5 w-5" />
              </div>
              <div>
                <h1 className="text-xl font-black tracking-tight text-slate-900">
                  {t('transfers.title')}
                </h1>
                <p className="text-xs text-slate-500 max-w-xl">
                  {t('transfers.subtitle')}
                </p>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Button
              onClick={() => setIsRequestOpen(true)}
              disabled={
                isCorporateAdmin ||
                !Permissions.canRequestTransfer(role, hasBranch)
              }
              className="bg-cyan-600 hover:bg-cyan-700 text-white text-xs font-semibold shadow-xs gap-1.5 h-9"
            >
              <Plus className="h-4 w-4" />
              {t('transfers.requestTransfer')}
            </Button>
          </div>
        </div>

        {/* Corporate Admin Notice (R-05) */}
        {isCorporateAdmin && (
          <Alert className="bg-amber-50 border-amber-200 text-amber-900 py-3">
            <Info className="h-4 w-4 text-amber-600" />
            <AlertTitle className="text-xs font-bold">
              {t('transfers.corporateScope', {
                defaultValue: 'Alcance Corporativo',
              })}
            </AlertTitle>
            <AlertDescription className="text-xs">
              {t('transfers.noBranchNotice')}
            </AlertDescription>
          </Alert>
        )}

        {/* State Machine KPI Metric Cards */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          {/* Requested */}
          <Card className="border-slate-200 bg-white shadow-2xs">
            <CardContent className="p-3.5 flex items-center justify-between">
              <div>
                <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                  {t('transfers.metrics.requested')}
                </p>
                <p className="text-lg font-black text-sky-600 font-mono mt-0.5">
                  {requestedQuery.data?.totalElements ?? '—'}
                </p>
              </div>
              <div className="h-8 w-8 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center">
                <Clock className="h-4 w-4" />
              </div>
            </CardContent>
          </Card>

          {/* In Preparation */}
          <Card className="border-slate-200 bg-white shadow-2xs">
            <CardContent className="p-3.5 flex items-center justify-between">
              <div>
                <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                  {t('transfers.metrics.inPreparation')}
                </p>
                <p className="text-lg font-black text-amber-600 font-mono mt-0.5">
                  {inPrepQuery.data?.totalElements ?? '—'}
                </p>
              </div>
              <div className="h-8 w-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
                <PackageCheck className="h-4 w-4" />
              </div>
            </CardContent>
          </Card>

          {/* In Transit */}
          <Card className="border-slate-200 bg-white shadow-2xs">
            <CardContent className="p-3.5 flex items-center justify-between">
              <div>
                <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                  {t('transfers.metrics.inTransit')}
                </p>
                <p className="text-lg font-black text-indigo-600 font-mono mt-0.5">
                  {inTransitQuery.data?.totalElements ?? '—'}
                </p>
              </div>
              <div className="h-8 w-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
                <Truck className="h-4 w-4" />
              </div>
            </CardContent>
          </Card>

          {/* Received */}
          <Card className="border-slate-200 bg-white shadow-2xs">
            <CardContent className="p-3.5 flex items-center justify-between">
              <div>
                <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                  {t('transfers.metrics.received')}
                </p>
                <p className="text-lg font-black text-emerald-600 font-mono mt-0.5">
                  {receivedQuery.data?.totalElements ?? '—'}
                </p>
              </div>
              <div className="h-8 w-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
                <CheckCircle2 className="h-4 w-4" />
              </div>
            </CardContent>
          </Card>

          {/* With Discrepancy */}
          <Card className="border-slate-200 bg-white shadow-2xs">
            <CardContent className="p-3.5 flex items-center justify-between">
              <div>
                <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                  {t('transfers.metrics.withDiscrepancy')}
                </p>
                <p className="text-lg font-black text-rose-600 font-mono mt-0.5">
                  {discrepancyQuery.data?.totalElements ?? '—'}
                </p>
              </div>
              <div className="h-8 w-8 rounded-lg bg-rose-50 text-rose-600 flex items-center justify-center">
                <AlertTriangle className="h-4 w-4" />
              </div>
            </CardContent>
          </Card>

          {/* Cancelled */}
          <Card className="border-slate-200 bg-white shadow-2xs">
            <CardContent className="p-3.5 flex items-center justify-between">
              <div>
                <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                  {t('transfers.metrics.cancelled')}
                </p>
                <p className="text-lg font-black text-slate-600 font-mono mt-0.5">
                  {cancelledQuery.data?.totalElements ?? '—'}
                </p>
              </div>
              <div className="h-8 w-8 rounded-lg bg-slate-100 text-slate-500 flex items-center justify-center">
                <XCircle className="h-4 w-4" />
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Transfer Management Table */}
        <TransferTable />

        {/* Request Dialog Modal */}
        <TransferRequestDialog
          open={isRequestOpen}
          onOpenChange={setIsRequestOpen}
        />
      </div>
    </AppLayout>
  )
}
